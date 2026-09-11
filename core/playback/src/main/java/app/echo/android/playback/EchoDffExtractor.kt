package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import kotlin.math.min

@UnstableApi
internal class EchoDffExtractor : Extractor {
    private var output: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null
    private var layout: EchoDffLayout? = null
    private var dataBytesRead: Long = 0L

    override fun sniff(input: ExtractorInput): Boolean {
        val header = ByteArray(16)
        if (!input.peekFully(header, 0, 16, true)) return false
        return EchoDffLayout.sniff(header)
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val current = layout ?: return readHeader(input)
        val track = trackOutput ?: return Extractor.RESULT_END_OF_INPUT
        val remaining = current.dataSize - dataBytesRead
        if (remaining <= 0L) return Extractor.RESULT_END_OF_INPUT
        val packetBytes = current.channelCount * EchoDffLayout.PacketFrames
        val toRead = min(packetBytes.toLong(), remaining).toInt()
        val timeUs = current.timeUs(dataBytesRead)
        val written = track.sampleData(input, toRead, true)
        if (written == C.RESULT_END_OF_INPUT) return Extractor.RESULT_END_OF_INPUT
        track.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, written, 0, null)
        dataBytesRead += written
        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        val current = layout ?: return
        dataBytesRead = if (position >= current.dataOffset) {
            (position - current.dataOffset).coerceIn(0L, current.dataSize)
        } else {
            current.seekAudioByteOffset(timeUs)
        }
    }

    override fun release() = Unit

    private fun readHeader(input: ExtractorInput): Int {
        val parsed = parseDff(input)
        if (parsed.compressed) {
            throw ParserException.createForUnsupportedContainerFeature("DST compressed DFF is not supported")
        }
        layout = parsed
        val track = trackOutput ?: return Extractor.RESULT_END_OF_INPUT
        val maxInput = parsed.channelCount * EchoDffLayout.PacketFrames
        track.format(
            Format.Builder()
                .setSampleMimeType(parsed.mimeType)
                .setChannelCount(parsed.channelCount)
                .setSampleRate(parsed.decoderPcmRateHz)
                .setMaxInputSize(maxInput)
                .build(),
        )
        output?.seekMap(EchoDffSeekMap(parsed))
        return Extractor.RESULT_CONTINUE
    }
}

@UnstableApi
private class EchoDffSeekMap(private val layout: EchoDffLayout) : SeekMap {
    override fun isSeekable(): Boolean = true
    override fun getDurationUs(): Long = layout.durationUs
    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val offset = layout.seekAudioByteOffset(timeUs)
        return SeekMap.SeekPoints(SeekPoint(layout.timeUs(offset), layout.dataOffset + offset))
    }
}

@UnstableApi
internal fun parseDff(input: ExtractorInput): EchoDffLayout {
    val frm = ByteArray(16)
    input.readFully(frm, 0, 16)
    if (!EchoDffLayout.sniff(frm)) {
        throw ParserException.createForMalformedContainer("Invalid DFF header", null)
    }
    var channelCount = 0
    var dsdRateHz = 0
    var compressed: Boolean? = null
    var dataOffset = -1L
    var dataSize = -1L
    var chunks = 0
    while (chunks < MaxDffChunks) {
        chunks++
        val chunk = ByteArray(12)
        if (!input.readFully(chunk, 0, 12, true)) break
        val tag = fourcc(chunk, 0)
        val size = u64be(chunk, 4)
        if (size < 0L || size > 1L shl 40) {
            throw ParserException.createForMalformedContainer("Invalid DFF chunk size", null)
        }
        val padded = size + (size and 1L)
        when (tag) {
            "PROP" -> parseDffProp(input, size) { channels, rate, dst ->
                if (channels > 0) channelCount = channels
                if (rate > 0) dsdRateHz = rate
                if (dst != null) compressed = dst
            }
            "DSD " -> {
                dataOffset = input.position
                dataSize = size
                break
            }
            "DST " -> {
                compressed = true
                dataOffset = input.position
                dataSize = size
                break
            }
            else -> skipFully(input, padded)
        }
    }
    if (dataOffset < 0L || dataSize <= 0L || channelCount !in 1..6 || dsdRateHz < 8 || dsdRateHz % 8 != 0) {
        throw ParserException.createForMalformedContainer("Incomplete DFF audio header", null)
    }
    val decoderPcmRateHz = EchoDsdPcm.decoderPcmRateHz(dsdRateHz)
    val durationUs = EchoDsdPcm.durationUs(dataSize / channelCount * 8L, dsdRateHz)
    return EchoDffLayout(
        channelCount = channelCount,
        dsdRateHz = dsdRateHz,
        decoderPcmRateHz = decoderPcmRateHz,
        dataOffset = dataOffset,
        dataSize = dataSize,
        durationUs = durationUs,
        compressed = compressed == true,
        mimeType = EchoDsdMime.Msbf,
    )
}

@UnstableApi
private fun parseDffProp(
    input: ExtractorInput,
    size: Long,
    onProperty: (channels: Int, dsdRateHz: Int, compressed: Boolean?) -> Unit,
) {
    val end = input.position + size
    if (size < 4L) {
        skipFully(input, size)
        return
    }
    val kind = ByteArray(4)
    input.readFully(kind, 0, 4)
    if (fourcc(kind, 0) != "SND ") {
        skipFully(input, (end - input.position).coerceAtLeast(0L) + (size and 1L))
        return
    }
    var channels = 0
    var rate = 0
    var compressed: Boolean? = null
    while (input.position + 12 <= end) {
        val chunk = ByteArray(12)
        input.readFully(chunk, 0, 12)
        val tag = fourcc(chunk, 0)
        val chunkSize = u64be(chunk, 4)
        val payloadEnd = input.position + chunkSize
        if (payloadEnd > end) break
        when (tag) {
            "FS  " -> if (chunkSize >= 4L) {
                val value = ByteArray(4)
                input.readFully(value, 0, 4)
                rate = u32be(value, 0).toInt()
            }
            "CHNL" -> if (chunkSize >= 2L) {
                val count = ByteArray(2)
                input.readFully(count, 0, 2)
                channels = u16be(count, 0)
            }
            "CMPR" -> if (chunkSize >= 4L) {
                val codec = ByteArray(4)
                input.readFully(codec, 0, 4)
                compressed = fourcc(codec, 0) == "DST "
            }
        }
        val target = (payloadEnd + (chunkSize and 1L)).coerceAtMost(end)
        skipFully(input, (target - input.position).coerceAtLeast(0L))
    }
    skipFully(input, (end - input.position).coerceAtLeast(0L))
    if ((size and 1L) == 1L) skipFully(input, 1L)
    onProperty(channels, rate, compressed)
}

@UnstableApi
private fun skipFully(input: ExtractorInput, bytes: Long) {
    var left = bytes.coerceAtLeast(0L)
    while (left > 0L) {
        val chunk = min(left, Int.MAX_VALUE.toLong()).toInt()
        input.skipFully(chunk)
        left -= chunk
    }
}

private const val MaxDffChunks = 64

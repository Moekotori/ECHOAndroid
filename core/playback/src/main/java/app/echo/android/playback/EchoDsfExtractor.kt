package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
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
internal class EchoDsfExtractor : Extractor {
    private var output: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null
    private var layout: EchoDsfLayout? = null
    private var dataFileBytesRead: Long = 0L
    private var audioBytesEmitted: Long = 0L

    override fun sniff(input: ExtractorInput): Boolean {
        val header = ByteArray(12)
        if (!input.peekFully(header, 0, 12, true)) return false
        return EchoDsfLayout.sniff(header)
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val current = layout ?: return readHeader(input)
        val track = trackOutput ?: return Extractor.RESULT_END_OF_INPUT
        if (audioBytesEmitted >= current.audioSize) return Extractor.RESULT_END_OF_INPUT
        return if (current.padded && dataFileBytesRead == current.dataSize - current.blockAlign) {
            readPaddedLastPacket(input, current, track)
        } else {
            val remainingAudio = current.audioSize - audioBytesEmitted
            val remainingFile = current.dataSize - dataFileBytesRead
            val toRead = min(current.blockAlign.toLong(), min(remainingAudio, remainingFile)).toInt()
            if (toRead <= 0) return Extractor.RESULT_END_OF_INPUT
            val timeUs = current.timeUs(audioBytesEmitted)
            val written = track.sampleData(input, toRead, true)
            if (written == C.RESULT_END_OF_INPUT) return Extractor.RESULT_END_OF_INPUT
            track.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, written, 0, null)
            dataFileBytesRead += written
            audioBytesEmitted += written
            Extractor.RESULT_CONTINUE
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        val current = layout ?: return
        val audioOffset = if (position >= current.dataOffset) {
            (position - current.dataOffset).coerceIn(0L, current.audioSize)
        } else {
            current.seekAudioByteOffset(timeUs)
        }
        dataFileBytesRead = audioOffset
        audioBytesEmitted = audioOffset.coerceAtMost(current.audioSize)
    }

    override fun release() = Unit

    private fun readHeader(input: ExtractorInput): Int {
        val header = ByteArray(EchoDsfLayout.HeaderBytes)
        input.readFully(header, 0, header.size)
        val parsed = EchoDsfLayout.parse(header)
            ?: throw ParserException.createForMalformedContainer("Invalid DSF header", null)
        layout = parsed
        val track = trackOutput ?: return Extractor.RESULT_END_OF_INPUT
        track.format(
            Format.Builder()
                .setSampleMimeType(parsed.mimeType)
                .setChannelCount(parsed.channelCount)
                .setSampleRate(parsed.decoderPcmRateHz)
                .setMaxInputSize(parsed.blockAlign)
                .build(),
        )
        output?.seekMap(EchoDsfSeekMap(parsed))
        return Extractor.RESULT_CONTINUE
    }

    private fun readPaddedLastPacket(
        input: ExtractorInput,
        layout: EchoDsfLayout,
        track: TrackOutput,
    ): Int {
        val realPerChannel = (layout.audioSize - audioBytesEmitted) / layout.channelCount
        if (realPerChannel <= 0L) return Extractor.RESULT_END_OF_INPUT
        val packet = ByteArray((realPerChannel * layout.channelCount).toInt())
        val skipPerChannel = layout.blockSizePerChannel - realPerChannel.toInt()
        var offset = 0
        repeat(layout.channelCount) {
            input.readFully(packet, offset, realPerChannel.toInt())
            offset += realPerChannel.toInt()
            if (skipPerChannel > 0) input.skipFully(skipPerChannel)
        }
        val timeUs = layout.timeUs(audioBytesEmitted)
        track.sampleData(ParsableByteArray(packet), packet.size)
        track.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, packet.size, 0, null)
        dataFileBytesRead = layout.dataSize
        audioBytesEmitted = layout.audioSize
        return Extractor.RESULT_CONTINUE
    }
}

@UnstableApi
private class EchoDsfSeekMap(private val layout: EchoDsfLayout) : SeekMap {
    override fun isSeekable(): Boolean = true
    override fun getDurationUs(): Long = layout.durationUs
    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val audioOffset = layout.seekAudioByteOffset(timeUs)
        val actualTimeUs = layout.timeUs(audioOffset)
        return SeekMap.SeekPoints(SeekPoint(actualTimeUs, layout.dataOffset + audioOffset))
    }
}

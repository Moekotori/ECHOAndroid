package app.echo.android.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Demux only: packets use the existing FFmpeg renderer, AudioSink and DSP chain. */
@UnstableApi
internal class EchoFfmpegExtractor(uri: Uri?, sources: DataSource.Factory?) : Extractor {
    private val source = EchoFfmpegInput(uri, sources)
    private val demuxer = EchoFfmpegDemuxer()
    private var container: String? = null
    private var output: ExtractorOutput? = null
    private var track: TrackOutput? = null
    private var opened = false
    private var pendingSeekUs = C.TIME_UNSET
    private var seekable = false
    private var drainSent = false
    private val packet = ParsableByteArray(0)

    override fun sniff(input: ExtractorInput): Boolean = try {
        val header = ByteArray(16)
        input.peekFully(header, 0, header.size, true) &&
            (echoFfmpegContainer(header).also { container = it } != null)
    } catch (_: EOFException) { false }

    override fun init(output: ExtractorOutput) {
        this.output = output
        track = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        source.bind(input)
        if (!opened) {
            try {
                source.beginProbe()
                demuxer.open(source, requireNotNull(container))
                val info = demuxer.info()
                val mime = "audio/x-echo-ffmpeg-${demuxer.codec()}"
                if (!FfmpegLibrary.supportsFormat(mime)) {
                    throw ParserException.createForUnsupportedContainerFeature("Unsupported audio codec: ${demuxer.codec()}")
                }
                val parameters = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 2..5) parameters.putInt(info[i].toInt())
                track!!.format(Format.Builder().setSampleMimeType(mime)
                    .setSampleRate(info[0].toInt()).setChannelCount(info[1].toInt())
                    .setInitializationData(listOf(demuxer.extraData(), parameters.array()))
                    .setMaxInputSize(64 * 1024).build())
                opened = true
                source.opening = false
                seekable = source.canSeek() && info[6] > 0
                output!!.seekMap(object : SeekMap {
                    override fun isSeekable(): Boolean = seekable
                    override fun getDurationUs(): Long = info[6]
                    // The native demuxer resolves the actual byte position on the loader thread.
                    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints =
                        SeekMap.SeekPoints(SeekPoint(timeUs.coerceAtLeast(0), 0))
                })
            } catch (error: Exception) {
                demuxer.close()
                source.closeAlternate()
                throw error
            }
            return synchronize(input, seekPosition)
        }
        if (pendingSeekUs != C.TIME_UNSET) {
            demuxer.seek(pendingSeekUs)
            pendingSeekUs = C.TIME_UNSET
            return synchronize(input, seekPosition)
        }
        val size = demuxer.readPacket()
        if (size < 0) {
            if (drainSent) return Extractor.RESULT_END_OF_INPUT
            drainSent = true
            // A normal empty packet drains delayed WMA samples before Media3's EOS.
            track!!.sampleMetadata(demuxer.packetTimeUs(), C.BUFFER_FLAG_KEY_FRAME, 0, 0, null)
            return Extractor.RESULT_CONTINUE
        }
        val bytes = demuxer.packetData()
        // Reuse the Java packet buffer; the native AVPacket remains valid until the next read.
        packet.reset(size)
        bytes.get(packet.data, 0, size)
        track!!.sampleData(packet, size)
        track!!.sampleMetadata(demuxer.packetTimeUs(), C.BUFFER_FLAG_KEY_FRAME, size, 0, null)
        return synchronize(input, seekPosition)
    }

    private fun synchronize(input: ExtractorInput, seekPosition: PositionHolder): Int {
        source.closeAlternate()
        return if (input.position != source.position) {
            seekPosition.position = source.position
            Extractor.RESULT_SEEK
        } else Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        if (opened || timeUs > 0) pendingSeekUs = timeUs.coerceAtLeast(0)
        drainSent = false
    }

    override fun release() {
        try { demuxer.close() } finally { source.close() }
    }
}

/** Signature selection prevents the fallback from intercepting existing playback formats. */
internal fun echoFfmpegContainer(bytes: ByteArray): String? {
    if (bytes.size < 16) return null
    return when {
        fourcc(bytes, 0) == "FORM" && fourcc(bytes, 8) in listOf("AIFF", "AIFC") -> "aiff"
        fourcc(bytes, 0) == "wvpk" -> "wv"
        bytes.copyOfRange(0, 16).contentEquals(AsfSignature) -> "asf"
        u32be(bytes, 0) in DtsSignatures -> "dts"
        else -> null
    }
}

private val DtsSignatures = setOf(0x7ffe8001L, 0xfe7f0180L, 0x1fffe800L, 0xff1f00e8L)
private val AsfSignature = byteArrayOf(0x30, 0x26, 0xb2.toByte(), 0x75, 0x8e.toByte(), 0x66,
    0xcf.toByte(), 0x11, 0xa6.toByte(), 0xd9.toByte(), 0, 0xaa.toByte(), 0, 0x62, 0xce.toByte(), 0x6c)

package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.EOFException

/** Emits FFmpeg APE packets (block count, byte/bit skip, aligned compressed frame). */
@UnstableApi
internal class EchoApeExtractor : Extractor {
    private var output: ExtractorOutput? = null
    private var track: TrackOutput? = null
    private var layout: EchoApeLayout? = null
    private var frame = 0
    private var frameBytesRead = 0
    private val packetHeader = ByteArray(8)
    private val headerData = ParsableByteArray(packetHeader)
    private val padding = ParsableByteArray(ByteArray(3))

    override fun sniff(input: ExtractorInput): Boolean = try {
        val prefix = ByteArray(10)
        if (!input.peekFully(prefix, 0, 10, true)) false else {
            if (prefix[0] == 'I'.code.toByte() && prefix[1] == 'D'.code.toByte() && prefix[2] == '3'.code.toByte()) {
                if ((6..9).any { prefix[it].toInt() and 128 != 0 }) return false
                val size = (6..9).fold(0) { n, i -> (n shl 7) or (prefix[i].toInt() and 127) }
                if (size > MaxApeHeaderBytes) return false
                input.advancePeekPosition(size + if (prefix[3].toInt() == 4 && prefix[5].toInt() and 16 != 0) 10 else 0)
                input.peekFully(prefix, 0, 6)
            }
            fourcc(prefix, 0) == "MAC "
        }
    } catch (_: EOFException) {
        false
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        track = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val current = layout ?: run {
            val parsed = readApeLayout(input)
            layout = parsed
            track!!.format(Format.Builder().setSampleMimeType("audio/ape")
                .setSampleRate(parsed.sampleRate).setChannelCount(parsed.channels)
                .setPcmEncoding(Util.getPcmEncoding(parsed.bitDepth))
                .setInitializationData(listOf(parsed.extraData()))
                .setMaxInputSize(parsed.sizes.max() + 8).build())
            output!!.seekMap(ApeSeekMap(parsed))
            return Extractor.RESULT_CONTINUE
        }
        if (frame >= current.positions.size) return Extractor.RESULT_END_OF_INPUT
        val target = current.positions[frame]
        if (frameBytesRead == 0) {
            if (input.position != target) {
                seekPosition.position = target
                return Extractor.RESULT_SEEK
            }
            val blocks = if (frame == current.positions.lastIndex) current.finalFrameBlocks else current.blocksPerFrame
            for (i in 0..3) {
                packetHeader[i] = (blocks shr (8 * i)).toByte()
                packetHeader[i + 4] = (current.skips[frame] shr (8 * i)).toByte()
            }
            headerData.setPosition(0)
            track!!.sampleData(headerData, 8)
        }
        val size = current.sizes[frame]
        // Last-frame word alignment can need up to three zero bytes beyond the audio payload.
        val readable = minOf(size.toLong(), current.audioEnd - target).toInt()
        if (frameBytesRead < readable) {
            frameBytesRead += track!!.sampleData(input, minOf(64 * 1024, readable - frameBytesRead), false)
        }
        if (frameBytesRead == readable) {
            if (size > readable) {
                padding.setPosition(0)
                track!!.sampleData(padding, size - readable)
            }
            track!!.sampleMetadata(current.timeUs(frame), C.BUFFER_FLAG_KEY_FRAME, size + 8, 0, null)
            frame++
            frameBytesRead = 0
        }
        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        val current = layout ?: return
        // SeekMap timestamps are rounded to microseconds; the byte position
        // identifies the frame exactly even when converting time back loses a sample.
        frame = current.positions.binarySearch(position).takeIf { it >= 0 } ?: current.frameAt(timeUs)
        frameBytesRead = 0
    }

    override fun release() = Unit
}

@UnstableApi
private class ApeSeekMap(private val layout: EchoApeLayout) : SeekMap {
    override fun isSeekable(): Boolean = true
    override fun getDurationUs(): Long = layout.durationUs
    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val frame = layout.frameAt(timeUs)
        val first = SeekPoint(layout.timeUs(frame), layout.positions[frame])
        return if (first.timeUs < timeUs && frame < layout.positions.lastIndex) {
            SeekMap.SeekPoints(first, SeekPoint(layout.timeUs(frame + 1), layout.positions[frame + 1]))
        } else SeekMap.SeekPoints(first)
    }
}

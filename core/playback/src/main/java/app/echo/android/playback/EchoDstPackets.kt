package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput

/** DST is frame compressed DSD. Feed whole DSTF packets to FFmpeg, never to the DoP packer. */
@UnstableApi
internal class EchoDstPackets private constructor(
    private val firstFrameOffset: Long,
    private val endOffset: Long,
    private val frames: Long,
) {
    private val header = ByteArray(12)
    private var frame = 0L
    private var targetFrame = 0L
    private val indexLock = Any()
    private val positions = LongArray(4096).also { it[0] = firstFrameOffset }
    private val frameNumbers = LongArray(4096)
    private var indexSize = 1

    val seekMap: SeekMap = object : SeekMap {
        override fun isSeekable() = true
        override fun getDurationUs() = frames * 1_000_000L / FrameRate
        // Use a bounded sparse index, then skip remaining packet payloads on the loader thread.
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints = synchronized(indexLock) {
            val target = frameForTime(timeUs)
            val index = (indexSize - 1 downTo 0).first { frameNumbers[it] <= target }
            SeekMap.SeekPoints(SeekPoint(timeUs.coerceIn(0, durationUs), positions[index]))
        }
    }

    fun seek(position: Long, timeUs: Long) {
        frame = synchronized(indexLock) {
            val index = (0 until indexSize).firstOrNull { positions[it] == position } ?: 0
            frameNumbers[index]
        }
        targetFrame = frameForTime(timeUs)
    }

    private fun frameForTime(timeUs: Long) =
        timeUs.coerceIn(0, frames * 1_000_000L / FrameRate) * FrameRate / 1_000_000L

    fun read(input: ExtractorInput, track: TrackOutput): Int {
        repeat(256) {
            if (frame >= frames) return Extractor.RESULT_END_OF_INPUT
            if (endOffset - input.position < 12) malformed("Truncated DST frame list")
            val position = input.position
            input.readFully(header, 0, 12)
            val size = u64be(header, 4)
            val padded = size + (size and 1L)
            if (size < 0 || padded < size || padded > endOffset - input.position) malformed("Invalid DST frame size")
            if (fourcc(header, 0) == "DSTF") {
                if (frame > frameNumbers[indexSize - 1] && frame % FrameRate == 0L && indexSize < positions.size) {
                    synchronized(indexLock) {
                        positions[indexSize] = position
                        frameNumbers[indexSize++] = frame
                    }
                }
                if (size !in 2..MaxPacketBytes.toLong()) malformed("Invalid DST packet")
                if (frame < targetFrame) {
                    input.skipFully(padded.toInt())
                    frame++
                } else {
                    var remaining = size.toInt()
                    while (remaining > 0) {
                        val count = track.sampleData(input, remaining, false)
                        if (count <= 0) malformed("Truncated DST packet")
                        remaining -= count
                    }
                    track.sampleMetadata(frame * 1_000_000L / FrameRate, C.BUFFER_FLAG_KEY_FRAME, size.toInt(), 0, null)
                    if (size and 1L != 0L) input.skipFully(1)
                    frame++
                    return Extractor.RESULT_CONTINUE
                }
            } else {
                // CRC and other optional chunks have no audio timestamps.
                var remaining = padded
                while (remaining > 0) {
                    val count = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
                    input.skipFully(count)
                    remaining -= count
                }
            }
        }
        return Extractor.RESULT_CONTINUE
    }

    companion object {
        const val Mime = "audio/x-echo-ffmpeg-dst"
        const val MaxPacketBytes = 1024 * 1024
        private const val FrameRate = 75L

        fun readHeader(input: ExtractorInput, layout: EchoDffLayout): EchoDstPackets {
            val end = layout.dataOffset + layout.dataSize
            val header = ByteArray(18)
            if (layout.dataSize < header.size) malformed("Missing DST frame information")
            input.readFully(header, 0, header.size)
            if (fourcc(header, 0) != "FRTE" || u64be(header, 4) != 6L) malformed("Invalid DST frame information")
            val frames = u32be(header, 12)
            if (frames <= 0 || u16be(header, 16) != FrameRate.toInt() || layout.dsdRateHz % 44_100 != 0) {
                throw ParserException.createForUnsupportedContainerFeature("Unsupported DST frame rate")
            }
            return EchoDstPackets(input.position, end, frames)
        }

        private fun malformed(message: String): Nothing =
            throw ParserException.createForMalformedContainer(message, null)
    }
}

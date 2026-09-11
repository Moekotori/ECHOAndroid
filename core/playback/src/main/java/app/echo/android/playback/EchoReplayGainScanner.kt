package app.echo.android.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.core.net.toUri
import app.echo.android.model.library.LibraryPlaybackSupport
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EchoReplayGainScanner(context: Context) {
    private val appContext = context.applicationContext

    fun scanTrackGainDb(uri: String, mimeType: String? = null): Float? {
        if (LibraryPlaybackSupport.isDsd(mimeType, uri)) return null
        val parsed = runCatching { uri.toUri() }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "content" && scheme != "file") return null
        val extractor = MediaExtractor()
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            extractor.setDataSource(appContext, parsed, null)
            val track = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            if (sampleRate < 8_000 || channels > 2) return null
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            try {
                drain(extractor, codec, sampleRate, channels, startedAt)
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return null
    }

    private fun drain(
        extractor: MediaExtractor,
        codec: MediaCodec,
        sampleRate: Int,
        channels: Int,
        startedAt: Long,
    ): Float? {
        val info = MediaCodec.BufferInfo()
        val acc = EchoReplayGainAccumulator()
        val producedPerInput = EchoReplayGainLoudness.TargetRateHz.toDouble() / sampleRate
        var pending = 0.0
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (SystemClock.elapsedRealtime() - startedAt > MaxScanMs) return acc.gainDb()
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index) ?: break
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                val buffer = codec.getOutputBuffer(outIndex)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val pcm = decodePcm16Mono(buffer, channels)
                    pcm.forEach { sample ->
                        pending += producedPerInput
                        while (pending >= 1.0) {
                            acc.add(sample)
                            pending -= 1.0
                        }
                    }
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                codec.releaseOutputBuffer(outIndex, false)
            }
        }
        return acc.gainDb()
    }

    private fun decodePcm16Mono(buffer: ByteBuffer, channels: Int): FloatArray {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shorts = buffer.remaining() / 2
        val frames = (shorts / channels).coerceAtLeast(0)
        val out = FloatArray(frames)
        var index = 0
        repeat(frames) { frame ->
            var sum = 0f
            repeat(channels) {
                sum += buffer.short / 32768f
            }
            out[index++] = sum / channels
        }
        return out
    }

    private companion object {
        const val MaxScanMs = 60_000L
    }
}

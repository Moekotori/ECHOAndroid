package app.echo.android.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.core.net.toUri
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class EchoSmartTransitionDecoder(context: Context) {
    private val appContext = context.applicationContext

    data class AnalysisPcm(
        val samples: FloatArray,
        val nativeSampleRateHz: Int,
        val vocal: EchoSmartTransitionVocal = EchoSmartTransitionVocal(),
    )

    fun decodeAnalysisMono(uri: String, startMs: Long, durationMs: Long): AnalysisPcm? {
        val parsed = runCatching { uri.toUri() }.getOrNull() ?: return null
        if (!EchoSmartTransitionPolicy.isLocalUri(uri, null)) return null
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
            if (channels > 2) return null
            extractor.seekTo(startMs.coerceAtLeast(0L) * 1_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val windowMs = durationMs.coerceAtMost(EchoSmartTransitionPolicy.WindowMs.toLong())
            val maxSamples = ((windowMs * EchoSmartTransitionPolicy.AnalysisSampleRateHz) / 1_000L).toInt().coerceAtLeast(1)
            val writer = EchoSmartTransitionDownsampler(
                inputRate = sampleRate,
                channels = channels,
                targetRate = EchoSmartTransitionPolicy.AnalysisSampleRateHz,
                maxSamples = maxSamples,
            )
            val vocal = EchoSmartTransitionVocalAccumulator(sampleRate, channels)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            drainAnalysis(extractor, codec, channels, startMs, durationMs, startedAt, writer, vocal)
            val samples = writer.toArray()
            if (samples.isEmpty()) null else AnalysisPcm(samples, sampleRate, vocal.finish())
        } catch (_: RuntimeException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun decodeMixWindow(
        uri: String,
        startMs: Long,
        durationMs: Long,
        expectedRateHz: Int,
        expectedChannels: Int,
    ): FloatArray? {
        val decoded = decode(uri, startMs, durationMs, expectedRateHz, expectedChannels) ?: return null
        if (decoded.sampleRateHz != expectedRateHz) return null
        val channels = expectedChannels.coerceIn(1, 2)
        val maxFrames = EchoSmartTransitionPolicy.MaxMixBytes / (channels * 4)
        val frames = minOf(decoded.pcm.size / decoded.channels, maxFrames)
        if (decoded.channels == channels) {
            return decoded.pcm.copyOf(frames * channels)
        }
        val output = FloatArray(frames * channels)
        for (frame in 0 until frames) {
            if (decoded.channels == 1) {
                val sample = decoded.pcm[frame]
                output[frame * channels] = sample
                if (channels == 2) output[frame * channels + 1] = sample
            } else {
                val left = decoded.pcm[frame * decoded.channels]
                val right = decoded.pcm[frame * decoded.channels + 1]
                if (channels == 1) {
                    output[frame] = (left + right) * 0.5f
                } else {
                    output[frame * 2] = left
                    output[frame * 2 + 1] = right
                }
            }
        }
        return output
    }

    private data class DecodedPcm(
        val pcm: FloatArray,
        val sampleRateHz: Int,
        val channels: Int,
    )

    private fun decode(
        uri: String,
        startMs: Long,
        durationMs: Long,
        expectedRateHz: Int?,
        expectedChannels: Int?,
    ): DecodedPcm? {
        val parsed = runCatching { uri.toUri() }.getOrNull() ?: return null
        if (!EchoSmartTransitionPolicy.isLocalUri(uri, null)) return null
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
            if (expectedRateHz != null && expectedRateHz != sampleRate) return null
            if (expectedChannels != null && channels > 2) return null
            extractor.seekTo(startMs.coerceAtLeast(0L) * 1_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            drain(extractor, codec, sampleRate, channels, startMs, durationMs, startedAt)
        } catch (_: RuntimeException) {
            null
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
        startMs: Long,
        durationMs: Long,
        startedAt: Long,
    ): DecodedPcm? {
        val endUs = (startMs + durationMs).coerceAtLeast(startMs) * 1_000L
        val maxFrames = ((durationMs.coerceAtLeast(1L) * sampleRate) / 1_000L).toInt() + 64
        val pcm = FloatArray(maxFrames * channels)
        var written = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (SystemClock.elapsedRealtime() - startedAt > EchoSmartTransitionPolicy.DecodeTimeoutMs) {
                    return null
                }
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val timeUs = extractor.sampleTime
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        if (size < 0 || timeUs > endUs + 200_000L) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, timeUs.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val encoding = codec.outputFormat.let { format ->
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                    }
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT) return null
                } else if (outputIndex >= 0) {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0 && info.presentationTimeUs >= startMs * 1_000L - 20_000L) {
                        written += appendPcm16(output, info, pcm, written, channels)
                        if (info.presentationTimeUs >= endUs || written >= pcm.size - channels) {
                            outputDone = true
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            runCatching {
                codec.stop()
                codec.release()
            }
        }
        if (written < channels) return null
        return DecodedPcm(pcm.copyOf(written), sampleRate, channels)
    }

    private fun appendPcm16(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        pcm: FloatArray,
        offset: Int,
        channels: Int,
    ): Int {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shorts = info.size / 2
        val available = (pcm.size - offset).coerceAtLeast(0)
        val count = minOf(shorts, available)
        var written = 0
        repeat(count) {
            pcm[offset + written] = buffer.short / 32768f
            written += 1
        }
        return written - (written % channels)
    }

    private fun drainAnalysis(
        extractor: MediaExtractor,
        codec: MediaCodec,
        channels: Int,
        startMs: Long,
        durationMs: Long,
        startedAt: Long,
        writer: EchoSmartTransitionDownsampler,
        vocal: EchoSmartTransitionVocalAccumulator,
    ) {
        val endUs = (startMs + durationMs).coerceAtLeast(startMs) * 1_000L
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (SystemClock.elapsedRealtime() - startedAt > EchoSmartTransitionPolicy.DecodeTimeoutMs) return
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val timeUs = extractor.sampleTime
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        if (size < 0 || timeUs > endUs + 200_000L) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, timeUs.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val encoding = codec.outputFormat.let { format ->
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                    }
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT) return
                } else if (outputIndex >= 0) {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0 && info.presentationTimeUs >= startMs * 1_000L - 20_000L) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        output.order(ByteOrder.LITTLE_ENDIAN)
                        val frames = (info.size / 2) / channels
                        val pcm = output.asShortBuffer()
                        repeat(frames) {
                            if (pcm.remaining() < channels) return@repeat
                            val left = pcm.get() / 32768f
                            val right = if (channels > 1) pcm.get() / 32768f else left
                            writer.pushStereoFrame(left, right)
                            vocal.pushPcm16(left, right)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 ||
                        info.presentationTimeUs >= endUs
                    ) {
                        outputDone = true
                    }
                }
            }
        } finally {
            runCatching {
                codec.stop()
                codec.release()
            }
        }
    }
}

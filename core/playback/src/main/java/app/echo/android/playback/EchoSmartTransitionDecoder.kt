package app.echo.android.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** Extra decoding is bounded, cancellable, and never runs on the player's application thread. */
internal class EchoSmartTransitionDecoder(context: Context) {
    private val appContext = context.applicationContext

    data class AnalysisPcm(
        val samples: FloatArray,
        val nativeSampleRateHz: Int,
        val vocal: EchoSmartTransitionVocal = EchoSmartTransitionVocal(),
    )

    fun sourceRevision(uri: String): String = runCatching {
        val parsed = uri.toUri()
        if (parsed.scheme == "file") {
            val file = File(requireNotNull(parsed.path))
            "${file.length()}:${file.lastModified()}"
        } else {
            appContext.contentResolver.query(parsed, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use "unknown"
                listOf("_size", "date_modified", "last_modified").joinToString(":") { name ->
                    val index = cursor.getColumnIndex(name)
                    if (index >= 0) cursor.getString(index).orEmpty() else ""
                }
            } ?: "unknown"
        }
    }.getOrDefault("unknown")

    suspend fun decodeAnalysisMono(uri: String, startMs: Long, durationMs: Long): AnalysisPcm? =
        withContext(Dispatchers.IO) {
            var writer: EchoSmartTransitionDownsampler? = null
            var vocal: EchoSmartTransitionVocalAccumulator? = null
            var nativeRate = 0
            val windowMs = durationMs.coerceAtMost(EchoSmartTransitionPolicy.WindowMs.toLong())
            val success = decodeWindow(uri, startMs, windowMs, configure = { rate, channels ->
                nativeRate = rate
                writer = EchoSmartTransitionDownsampler(rate, channels,
                    EchoSmartTransitionPolicy.AnalysisSampleRateHz,
                    (windowMs * EchoSmartTransitionPolicy.AnalysisSampleRateHz / 1000).toInt())
                vocal = EchoSmartTransitionVocalAccumulator(rate, channels)
                true
            }) { left, right ->
                writer!!.pushStereoFrame(left, right)
                vocal!!.pushPcm16(left, right)
            }
            val samples = writer?.toArray()
            if (!success || samples == null || samples.size < windowMs * EchoSmartTransitionPolicy.AnalysisSampleRateHz / 1000 - 2) {
                null
            } else AnalysisPcm(samples, nativeRate, vocal!!.finish())
        }

    suspend fun decodeMixWindow(
        uri: String,
        startMs: Long,
        durationMs: Long,
        expectedRateHz: Int,
        expectedChannels: Int,
    ): FloatArray? = withContext(Dispatchers.IO) {
        if (durationMs !in 1..EchoSmartTransitionPolicy.MaxOverlapMs.toLong() ||
            expectedRateHz !in 8_000..384_000 || expectedChannels !in 1..2) return@withContext null
        val outputFrames = durationMs * expectedRateHz / 1000
        if (outputFrames * expectedChannels * 4 > EchoSmartTransitionPolicy.MaxMixBytes) return@withContext null
        var pcm = FloatArray(0)
        var nativeRate = 0
        var nativeChannels = 0
        var written = 0
        val success = decodeWindow(uri, startMs, durationMs, configure = { rate, channels ->
            nativeRate = rate
            nativeChannels = channels
            val samples = durationMs * rate / 1000 * channels
            if (samples * 4 > 8L * 1024 * 1024) false else {
                pcm = FloatArray(samples.toInt())
                true
            }
        }) { left, right ->
            if (written + nativeChannels <= pcm.size) {
                pcm[written++] = left
                if (nativeChannels == 2) pcm[written++] = right
            }
        }
        coroutineContext.ensureActive()
        // Never arm a truncated window: its continuation would skip audio that was not mixed.
        if (!success || written != pcm.size || written == 0) return@withContext null
        if (nativeRate == expectedRateHz && nativeChannels == expectedChannels) return@withContext pcm
        withContext(Dispatchers.Default) {
            EchoSmartTransitionPcm.resampleInterleaved(pcm, nativeRate, nativeChannels, expectedRateHz, expectedChannels)
        }
    }

    private suspend fun decodeWindow(
        uri: String,
        startMs: Long,
        durationMs: Long,
        configure: (Int, Int) -> Boolean,
        frame: (Float, Float) -> Unit,
    ): Boolean {
        if (startMs < 0 || durationMs <= 0 || !EchoSmartTransitionPolicy.isLocalUri(uri, null)) return false
        coroutineContext.ensureActive()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val startedAt = SystemClock.elapsedRealtime()
        try {
            extractor.setDataSource(appContext, uri.toUri(), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (rate !in 8_000..384_000 || channels !in 1..2 || !configure(rate, channels)) return false
            val startUs = startMs * 1000
            val endUs = (startMs + durationMs) * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val activeCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: return false)
            codec = activeCodec
            activeCodec.configure(format, null, null, 0)
            activeCodec.start()
            val info = MediaCodec.BufferInfo()
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var inputDone = false
            while (true) {
                coroutineContext.ensureActive()
                if (SystemClock.elapsedRealtime() - startedAt > EchoSmartTransitionPolicy.DecodeTimeoutMs) return false
                if (!inputDone) {
                    val index = activeCodec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = activeCodec.getInputBuffer(index)
                        val timeUs = extractor.sampleTime
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0 || timeUs > endUs + 200_000) {
                            activeCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            activeCodec.queueInputBuffer(index, 0, size, timeUs.coerceAtLeast(0), 0)
                            extractor.advance()
                        }
                    }
                }
                val index = activeCodec.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output = activeCodec.outputFormat
                    if (output.getInteger(MediaFormat.KEY_SAMPLE_RATE) != rate ||
                        output.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != channels) return false
                    encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        else AudioFormat.ENCODING_PCM_16BIT
                } else if (index >= 0) {
                    try {
                        val output = activeCodec.getOutputBuffer(index)
                        if (output != null && info.size > 0) {
                            val bytesPerFrame = EchoSmartTransitionPcm.bytesPerSample(encoding) * channels
                            val frames = info.size / bytesPerFrame
                            val range = EchoSmartTransitionPcm.windowFrames(info.presentationTimeUs, frames, rate, startUs, endUs)
                            EchoSmartTransitionPcm.prepareBuffer(output, info.offset + range.first * bytesPerFrame,
                                (range.last - range.first + 1).coerceAtLeast(0) * bytesPerFrame)
                            for (ignored in range) {
                                val left = EchoSmartTransitionPcm.readSample(output, encoding)
                                val right = if (channels == 2) EchoSmartTransitionPcm.readSample(output, encoding) else left
                                frame(left, right)
                            }
                            if (info.presentationTimeUs + frames * 1_000_000L / rate >= endUs) return true
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                    } finally {
                        activeCodec.releaseOutputBuffer(index, false)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } finally {
            // Release independently: stop/configure failure must not leak a codec.
            codec?.let { active ->
                runCatching { active.stop() }
                runCatching { active.release() }
            }
            runCatching { extractor.release() }
        }
    }
}

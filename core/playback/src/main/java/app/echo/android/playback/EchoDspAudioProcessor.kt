package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Keeps internal headroom until the final limiter and converts back only once. */
@UnstableApi
internal class EchoDspAudioProcessor(private val stages: Array<AudioProcessor>) : BaseAudioProcessor() {
    init {
        stages.forEach {
            when (it) {
                is EchoEqualizerAudioProcessor -> it.preserveFloatHeadroom = true
                is EchoChannelBalanceAudioProcessor -> it.preserveFloatHeadroom = true
                is EchoSmartTransitionMixer -> it.preserveFloatHeadroom = true
            }
        }
    }
    private val kernel = EchoDspKernel()
    private var scratch = ByteBuffer.allocateDirect(1024 * 8 * 4).order(ByteOrder.nativeOrder())
    private val multichannelFrame = FloatArray(8)
    private var bytesPerSample = 2
    private var order = ByteOrder.LITTLE_ENDIAN

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        bytesPerSample = when (format.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
            else -> throw AudioProcessor.UnhandledAudioFormatException(format)
        }
        if (format.channelCount !in 1..8) throw AudioProcessor.UnhandledAudioFormatException(format)
        order = when (format.encoding) {
            C.ENCODING_PCM_16BIT_BIG_ENDIAN, C.ENCODING_PCM_24BIT_BIG_ENDIAN, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> ByteOrder.BIG_ENDIAN
            else -> ByteOrder.LITTLE_ENDIAN
        }
        val internalFormat = AudioProcessor.AudioFormat(format.sampleRate, format.channelCount, C.ENCODING_PCM_FLOAT)
        stages.forEach { it.configure(internalFormat) }
        kernel.configure(format.sampleRate)
        return format
    }

    override fun onFlush(metadata: AudioProcessor.StreamMetadata) {
        stages.forEach { it.flush(metadata) }
        kernel.setTarget(EchoPlaybackProcessRuntime.dspSettings, EchoPlaybackProcessRuntime.dspReplayGainDb)
        kernel.reset()
    }

    override fun onReset() { stages.forEach { it.reset() }; kernel.reset() }

    override fun queueInput(input: ByteBuffer) {
        if (!input.hasRemaining()) return
        val channels = inputAudioFormat.channelCount
        val frames = minOf(1024, input.remaining() / (channels * bytesPerSample))
        check(frames > 0) { "Incomplete PCM frame" }
        val output = replaceOutputBuffer(frames * channels * bytesPerSample).order(order)
        val settings = EchoPlaybackProcessRuntime.dspSettings
        val replayGain = EchoPlaybackProcessRuntime.dspReplayGainDb
        if (!settings.limiterEnabled && !settings.crossfeedEnabled && replayGain == 0f && kernel.neutral && stages.none { it.isActive }) {
            val originalLimit = input.limit()
            input.limit(input.position() + frames * channels * bytesPerSample)
            output.put(input)
            input.limit(originalLimit)
            output.flip()
            return
        }
        input.order(order)
        scratch.clear()
        repeat(frames * channels) { scratch.putFloat(readSample(input)) }
        scratch.flip()
        var samples = scratch
        for (stage in stages) {
            if (stage.isActive) {
                stage.queueInput(samples)
                samples = stage.output.order(ByteOrder.nativeOrder())
            }
        }
        kernel.setTarget(settings, replayGain)
        repeat(frames) {
            if (channels <= 2) {
                val l = samples.float.let { if (it.isFinite()) it else 0f }
                val r = if (channels == 2) samples.float.let { if (it.isFinite()) it else 0f } else l
                kernel.process(l, r, channels == 2)
                writeSample(output, kernel.left)
                if (channels == 2) writeSample(output, kernel.right)
            } else {
                var peak = 0f
                repeat(channels) { channel ->
                    val value = samples.float.let { if (it.isFinite()) it else 0f }
                    multichannelFrame[channel] = value
                    peak = maxOf(peak, kotlin.math.abs(value))
                }
                // No crossfeed for surround; all channels share one limiter envelope.
                kernel.process(peak, peak, false)
                val factor = if (peak > 0f) kernel.left / peak else 0f
                repeat(channels) { writeSample(output, multichannelFrame[it] * factor) }
            }
        }
        output.flip()
    }

    private fun readSample(input: ByteBuffer): Float = when {
        inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT -> input.float
        bytesPerSample == 2 -> input.short / 32768f
        bytesPerSample == 4 -> (input.int / 2147483648.0).toFloat()
        else -> {
            val a = input.get().toInt() and 255
            val b = input.get().toInt() and 255
            val c = input.get().toInt() and 255
            val packed = if (order == ByteOrder.LITTLE_ENDIAN) a or (b shl 8) or (c shl 16) else c or (b shl 8) or (a shl 16)
            ((packed shl 8) shr 8) / 8388608f
        }
    }

    private fun writeSample(output: ByteBuffer, value: Float) {
        val sample = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
        when {
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT -> output.putFloat(sample)
            bytesPerSample == 2 -> output.putShort((sample * 32768.0).toInt().coerceIn(-32768, 32767).toShort())
            bytesPerSample == 4 -> output.putInt((sample * 2147483648.0).toLong().coerceIn(-2147483648L, 2147483647L).toInt())
            else -> {
                val n = (sample * 8388608.0).toInt().coerceIn(-8388608, 8388607)
                if (order == ByteOrder.LITTLE_ENDIAN) { output.put(n.toByte()); output.put((n shr 8).toByte()); output.put((n shr 16).toByte()) }
                else { output.put((n shr 16).toByte()); output.put((n shr 8).toByte()); output.put(n.toByte()) }
            }
        }
    }
}

package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
internal class EchoSmartTransitionMixer : BaseAudioProcessor() {
    data class MixSession(
        val pcm: FloatArray,
        val frames: Int,
        val channels: Int,
        val holdFrames: Int,
        val incomingGain: Float,
        val bassSwap: Boolean,
        val lowPass: Float,
    ) {
        @Volatile var readFrame: Int = 0
        @Volatile var held: Int = 0
        var outLow0 = 0f
        var outLow1 = 0f
        var inLow0 = 0f
        var inLow1 = 0f
    }

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    private var session: MixSession? = null

    val mixing: Boolean
        get() = session != null

    val outputSampleRateHz: Int?
        get() = inputAudioFormat.takeIf { it != AudioProcessor.AudioFormat.NOT_SET }?.sampleRate

    val outputChannelCount: Int
        get() = inputAudioFormat.takeIf { it != AudioProcessor.AudioFormat.NOT_SET }?.channelCount ?: 2

    fun setEnabled(enabled: Boolean): Boolean {
        if (this.enabled == enabled) return false
        this.enabled = enabled
        if (!enabled) cancel()
        return true
    }

    fun arm(
        pcm: FloatArray,
        frames: Int,
        channels: Int,
        holdFrames: Int = 0,
        incomingGain: Float = 1f,
        bassSwap: Boolean = false,
    ) {
        if (frames <= 0 || pcm.isEmpty()) {
            cancel()
            return
        }
        val rate = outputSampleRateHz ?: 48_000
        val lowPass = (1.0 - kotlin.math.exp(-2.0 * Math.PI * 250.0 / rate.coerceAtLeast(8_000))).toFloat()
        session = MixSession(
            pcm = pcm,
            frames = frames,
            channels = channels.coerceIn(1, 2),
            holdFrames = holdFrames.coerceAtLeast(0),
            incomingGain = incomingGain.coerceIn(0.4f, 1.4f),
            bassSwap = bassSwap && channels.coerceIn(1, 2) == 2,
            lowPass = lowPass,
        )
    }

    fun cancel() {
        session = null
    }

    override fun isActive(): Boolean = enabled && super.isActive()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!enabled) return AudioProcessor.AudioFormat.NOT_SET
        if (!isSupportedEncoding(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val mix = session
        if (mix == null) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val format = inputAudioFormat
        val mixed = when (format.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN ->
                mixPcm16(inputBuffer, output, format.channelCount, mix)
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN ->
                mixPcm24(inputBuffer, output, format.channelCount, format.encoding, mix)
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN ->
                mixPcm32(inputBuffer, output, format.channelCount, mix)
            C.ENCODING_PCM_FLOAT ->
                mixPcmFloat(inputBuffer, output, format.channelCount, mix)
            else -> {
                output.put(inputBuffer)
                false
            }
        }
        output.flip()
        if (!mixed) session = null
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) = Unit

    override fun onReset() {
        cancel()
    }

    private fun mixPcm16(input: ByteBuffer, output: ByteBuffer, channels: Int, mix: MixSession): Boolean {
        val shortIn = input.asShortBuffer()
        val shortOut = output.asShortBuffer()
        val frames = shortIn.remaining() / channels
        var stillMixing = true
        repeat(frames) {
            stillMixing = mixFrame(
                channels,
                mix,
                incoming = { shortIn.get() / 32768f },
                outgoing = { _, sample ->
                    shortOut.put((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                },
            )
        }
        input.position(input.limit())
        output.position(output.position() + frames * channels * 2)
        return stillMixing
    }

    private fun mixPcm32(input: ByteBuffer, output: ByteBuffer, channels: Int, mix: MixSession): Boolean {
        val intIn = input.asIntBuffer()
        val intOut = output.asIntBuffer()
        val frames = intIn.remaining() / channels
        var stillMixing = true
        repeat(frames) {
            stillMixing = mixFrame(
                channels,
                mix,
                incoming = { intIn.get() * (1.0f / 2_147_483_648f) },
                outgoing = { _, sample ->
                    intOut.put((sample.coerceIn(-1f, 1f) * 2_147_483_647f).toInt())
                },
            )
        }
        input.position(input.limit())
        output.position(output.position() + frames * channels * 4)
        return stillMixing
    }

    private fun mixPcmFloat(input: ByteBuffer, output: ByteBuffer, channels: Int, mix: MixSession): Boolean {
        val floatIn = input.asFloatBuffer()
        val floatOut = output.asFloatBuffer()
        val frames = floatIn.remaining() / channels
        var stillMixing = true
        repeat(frames) {
            stillMixing = mixFrame(
                channels,
                mix,
                incoming = { floatIn.get() },
                outgoing = { _, sample -> floatOut.put(sample.coerceIn(-1f, 1f)) },
            )
        }
        input.position(input.limit())
        output.position(output.position() + frames * channels * 4)
        return stillMixing
    }

    private fun mixPcm24(
        input: ByteBuffer,
        output: ByteBuffer,
        channels: Int,
        encoding: Int,
        mix: MixSession,
    ): Boolean {
        val order = if (encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN) ByteOrder.BIG_ENDIAN else input.order()
        output.order(order)
        val frames = input.remaining() / (channels * 3)
        var stillMixing = true
        repeat(frames) {
            stillMixing = mixFrame(
                channels,
                mix,
                incoming = { input.getPcm24(order) / 8_388_608f },
                outgoing = { _, sample ->
                    output.putPcm24((sample.coerceIn(-1f, 1f) * 8_388_607f).toInt(), order)
                },
            )
        }
        return stillMixing
    }

    private inline fun mixFrame(
        channels: Int,
        mix: MixSession,
        incoming: () -> Float,
        outgoing: (Int, Float) -> Unit,
    ): Boolean {
        if (mix.held < mix.holdFrames) {
            for (channel in 0 until channels) outgoing(channel, incoming())
            mix.held += 1
            return true
        }
        val read = mix.readFrame
        if (read >= mix.frames) {
            for (channel in 0 until channels) outgoing(channel, incoming())
            return false
        }
        val progress = if (mix.frames <= 1) 1f else (read.toFloat() / (mix.frames - 1).toFloat())
        val outGain = EchoSmartTransitionPolicy.equalPowerOut(progress)
        val inGain = EchoSmartTransitionPolicy.equalPowerIn(progress) * mix.incomingGain
        val mixChannels = mix.channels
        val current0 = incoming()
        val current1 = if (channels > 1) incoming() else current0
        val incoming0 = mix.pcm[read * mixChannels]
        val incoming1 = mix.pcm[read * mixChannels + (mixChannels - 1).coerceAtLeast(0)]
        if (mix.bassSwap && channels == 2) {
            mix.outLow0 += mix.lowPass * (current0 - mix.outLow0)
            mix.outLow1 += mix.lowPass * (current1 - mix.outLow1)
            mix.inLow0 += mix.lowPass * (incoming0 - mix.inLow0)
            mix.inLow1 += mix.lowPass * (incoming1 - mix.inLow1)
            val lowOut = EchoSmartTransitionPolicy.equalPowerOut((progress * 1.35f).coerceIn(0f, 1f))
            val lowIn = EchoSmartTransitionPolicy.equalPowerIn((progress * 1.35f - 0.2f).coerceIn(0f, 1f)) * mix.incomingGain
            outgoing(0, mix.outLow0 * lowOut + (current0 - mix.outLow0) * outGain + mix.inLow0 * lowIn + (incoming0 - mix.inLow0) * inGain)
            outgoing(1, mix.outLow1 * lowOut + (current1 - mix.outLow1) * outGain + mix.inLow1 * lowIn + (incoming1 - mix.inLow1) * inGain)
        } else {
            outgoing(0, current0 * outGain + incoming0 * inGain)
            if (channels > 1) outgoing(1, current1 * outGain + incoming1 * inGain)
        }
        mix.readFrame = read + 1
        return mix.readFrame < mix.frames
    }

    private fun isSupportedEncoding(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_FLOAT

    private fun ByteBuffer.getPcm24(order: ByteOrder): Int {
        val b0 = get().toInt() and 0xff
        val b1 = get().toInt() and 0xff
        val b2 = get().toInt() and 0xff
        val packed = if (order == ByteOrder.LITTLE_ENDIAN) {
            b0 or (b1 shl 8) or (b2 shl 16)
        } else {
            (b0 shl 16) or (b1 shl 8) or (b2)
        }
        return (packed shl 8) shr 8
    }

    private fun ByteBuffer.putPcm24(sample: Int, order: ByteOrder) {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            put(sample.toByte())
            put((sample shr 8).toByte())
            put((sample shr 16).toByte())
        } else {
            put((sample shr 16).toByte())
            put((sample shr 8).toByte())
            put(sample.toByte())
        }
    }
}

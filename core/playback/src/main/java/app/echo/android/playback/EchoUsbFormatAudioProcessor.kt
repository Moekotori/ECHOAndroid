package app.echo.android.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.usbaudio.UsbAudioProbe
import app.echo.android.usbaudio.UsbPcmFormatSelector
import app.echo.android.usbaudio.UsbPcmFormatSpec
import java.nio.ByteBuffer
import kotlin.math.abs

/** Compatibility-only conversion; the strict renderer never enters this processor chain. */
@UnstableApi
internal class EchoUsbFormatAudioProcessor(
    private val selectOutput: (AudioProcessor.AudioFormat) -> AudioProcessor.AudioFormat?,
    private val backend: UsbResamplerBackend,
) : BaseAudioProcessor() {
    constructor(context: Context) : this(usbFormatSelector(context), EchoUsbResamplerNative)
    private var handle = 0L
    private var pendingConversion = false
    private var draining = false
    private var tailDrained = true
    private var bits = 16
    private var bigEndian = false
    private var inputFrameBytes = 4
    private var chunkFrames = 1024
    private var inputScratch = AudioProcessor.EMPTY_BUFFER

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        pendingConversion = false
        val target = selectOutput(format) ?: return AudioProcessor.AudioFormat.NOT_SET
        bits = when (format.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
            C.ENCODING_PCM_FLOAT -> -32
            else -> throw AudioProcessor.UnhandledAudioFormatException(format)
        }
        bigEndian = format.encoding in listOf(C.ENCODING_PCM_16BIT_BIG_ENDIAN,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN, C.ENCODING_PCM_32BIT_BIG_ENDIAN)
        if (format.sampleRate !in 8_000..384_000 || format.channelCount !in 1..8)
            throw AudioProcessor.UnhandledAudioFormatException(format)
        if (target == format)
            return AudioProcessor.AudioFormat.NOT_SET
        if (!backend.available) throw AudioProcessor.UnhandledAudioFormatException(format)
        inputFrameBytes = format.channelCount * abs(bits) / 8
        chunkFrames = minOf(1024, (6144L * format.sampleRate / target.sampleRate).toInt().coerceAtLeast(1))
        pendingConversion = true
        return target
    }

    override fun onFlush(metadata: AudioProcessor.StreamMetadata) {
        close()
        draining = false
        tailDrained = !pendingConversion
        if (!pendingConversion) return
        val outBits = when (outputAudioFormat.encoding) { C.ENCODING_PCM_16BIT -> 16; C.ENCODING_PCM_24BIT -> 24; else -> 32 }
        handle = backend.create(inputAudioFormat.sampleRate, inputAudioFormat.channelCount,
            bits, outputAudioFormat.sampleRate, outputAudioFormat.channelCount, outBits)
        check(handle != 0L) { "USB format converter initialization failed" }
        // Allocate once at the stream boundary, not once per decoded buffer.
        if (inputScratch.capacity() < 1024 * 8 * 4) inputScratch = ByteBuffer.allocateDirect(1024 * 8 * 4)
        replaceOutputBuffer(OUTPUT_BYTES).limit(0)
    }

    override fun queueInput(input: ByteBuffer) {
        if (!input.hasRemaining() || hasPendingOutput()) return
        check(handle != 0L && input.remaining() % inputFrameBytes == 0) { "Invalid USB PCM conversion input" }
        val frames = minOf(chunkFrames, input.remaining() / inputFrameBytes)
        val count = frames * inputFrameBytes
        val offset = input.position()
        val source = if (input.isDirect) input else {
            inputScratch.clear()
            val oldLimit = input.limit()
            input.limit(offset + count)
            inputScratch.put(input)
            input.limit(oldLimit)
            inputScratch
        }
        val output = replaceOutputBuffer(OUTPUT_BYTES)
        val written = backend.convert(handle, source,
            if (input.isDirect) offset else 0, frames, bigEndian, output, output.capacity())
        check(written >= 0) { "USB PCM conversion failed ($written)" }
        input.position(offset + count)
        output.limit(written)
    }

    override fun onQueueEndOfStream() {
        draining = true
        // The pipeline may still hold the buffer previously returned by getOutput().
        // Drain only on its next getOutput() call, once that buffer has been consumed.
    }
    override fun getOutput(): ByteBuffer {
        if (draining && !tailDrained && !hasPendingOutput()) drainTail()
        return super.getOutput()
    }
    override fun isEnded(): Boolean = tailDrained && super.isEnded()
    private fun drainTail() {
        if (handle == 0L) { tailDrained = true; return }
        val output = replaceOutputBuffer(OUTPUT_BYTES)
        val written = backend.convert(handle, null, 0, 0, false, output, output.capacity())
        check(written >= 0) { "USB PCM converter drain failed ($written)" }
        output.limit(written)
        tailDrained = written == 0
    }
    private fun close() { if (handle != 0L) backend.close(handle); handle = 0L }
    override fun onReset() { close(); inputScratch = AudioProcessor.EMPTY_BUFFER; pendingConversion = false; draining = false; tailDrained = true }
    companion object { private const val OUTPUT_BYTES = 8192 * 2 * 4 }
}

internal interface UsbResamplerBackend {
    val available: Boolean
    fun create(inRate: Int, inChannels: Int, inBits: Int, outRate: Int, outChannels: Int, outBits: Int): Long
    fun convert(handle: Long, input: ByteBuffer?, offset: Int, frames: Int,
        bigEndian: Boolean, output: ByteBuffer, capacity: Int): Int
    fun close(handle: Long)
}

internal object EchoUsbResamplerNative : UsbResamplerBackend {
    override val available: Boolean by lazy { runCatching { System.loadLibrary("ffmpegJNI") }.isSuccess }
    override fun create(inRate: Int, inChannels: Int, inBits: Int, outRate: Int, outChannels: Int, outBits: Int) =
        nativeCreate(inRate, inChannels, inBits, outRate, outChannels, outBits)
    override fun convert(handle: Long, input: ByteBuffer?, offset: Int, frames: Int,
        bigEndian: Boolean, output: ByteBuffer, capacity: Int) = nativeConvert(handle, input, offset, frames, bigEndian, output, capacity)
    override fun close(handle: Long) = nativeClose(handle)
    @JvmStatic external fun nativeCreate(inRate: Int, inChannels: Int, inBits: Int, outRate: Int, outChannels: Int, outBits: Int): Long
    @JvmStatic external fun nativeConvert(handle: Long, input: ByteBuffer?, offset: Int, frames: Int,
        bigEndian: Boolean, output: ByteBuffer, capacity: Int): Int
    @JvmStatic external fun nativeClose(handle: Long)
}

@UnstableApi
private fun usbFormatSelector(context: Context): (AudioProcessor.AudioFormat) -> AudioProcessor.AudioFormat? {
    val probe = UsbAudioProbe(context)
    return selector@ { format ->
        if (!EchoPlaybackProcessRuntime.usbExclusiveEnabled || EchoPlaybackProcessRuntime.usbBitPerfectEnabled) return@selector null
        val options = EchoPlaybackProcessRuntime.usbOutputOptions
        if (options.sampleRateHz == 0 && options.bitDepth == 0 && options.channelCount == 0) return@selector null
        val snapshot = probe.snapshot()
        if (!snapshot.connected || !snapshot.permissionGranted) return@selector null
        val sourceBits = when (format.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
            else -> 32
        }
        val rate = options.sampleRateHz.takeIf { it > 0 } ?: format.sampleRate
        val requestedDepth = options.bitDepth.takeIf { it > 0 } ?: sourceBits
        val channels = options.channelCount.takeIf { it > 0 } ?: format.channelCount.coerceIn(1, 2)
        val descriptor = snapshot.descriptor.copy(streamingFormats = snapshot.descriptor.streamingFormats
            .filter { it.formatType == 1 && it.pcmIntegerSupported == true &&
                it.bitResolution in listOf(16, 24, 32) && it.subslotSize in 2..4 && it.channelCount in 1..2 })
        val spec = UsbPcmFormatSpec(rate, channels, requestedDepth)
        val selected = if (options.bitDepth > 0) UsbPcmFormatSelector.chooseFormat(descriptor, spec)
            else UsbPcmFormatSelector.chooseClosestFormat(descriptor, spec)
        val depth = selected?.bitResolution ?: throw AudioProcessor.UnhandledAudioFormatException(format)
        val encoding = when (depth) { 16 -> C.ENCODING_PCM_16BIT; 24 -> C.ENCODING_PCM_24BIT; else -> C.ENCODING_PCM_32BIT }
        AudioProcessor.AudioFormat(rate, channels, encoding)
    }
}

package app.echo.android.playback

import android.content.Context
import android.media.AudioFormat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import app.echo.android.usbaudio.UsbAudioProbe
import app.echo.android.usbaudio.UsbExclusivePcmOutput
import app.echo.android.usbaudio.UsbPcmFormatSelector
import app.echo.android.usbaudio.UsbPcmFormatSpec
import app.echo.android.usbaudio.UsbPcmPacker
import app.echo.android.usbaudio.UsbPcmSourceEncoding

@UnstableApi
internal class EchoAudioOutputProvider(
    context: Context,
) : ForwardingAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(context).build()) {
    private val probe = UsbAudioProbe(context)
    private val usbOutput = UsbExclusivePcmOutput(context)

    override fun getFormatSupport(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.FormatSupport {
        if (exclusiveConfigOrNull(formatConfig) != null) {
            return AudioOutputProvider.FormatSupport.Builder()
                .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                .build()
        }
        return super.getFormatSupport(formatConfig)
    }

    override fun getOutputConfig(
        formatConfig: AudioOutputProvider.FormatConfig,
    ): AudioOutputProvider.OutputConfig {
        exclusiveConfigOrNull(formatConfig)?.let { return it.outputConfig }
        return super.getOutputConfig(formatConfig)
    }

    override fun getAudioOutput(outputConfig: AudioOutputProvider.OutputConfig): AudioOutput {
        if (EchoPlaybackProcessRuntime.usbExclusiveEnabled) {
            exclusiveOutputOrNull(outputConfig)?.let { return it }
        }
        EchoPlaybackProcessRuntime.setUsbExclusiveSinkStatus(null)
        return super.getAudioOutput(outputConfig)
    }

    private fun exclusiveConfigOrNull(
        formatConfig: AudioOutputProvider.FormatConfig,
    ): ExclusivePlan? {
        if (!EchoPlaybackProcessRuntime.usbExclusiveEnabled) return null
        val snapshot = probe.snapshot()
        if (!snapshot.connected || !snapshot.permissionGranted) return null
        val sampleRate = formatConfig.format.sampleRate.takeIf { it > 0 } ?: return null
        val channelCount = formatConfig.format.channelCount.takeIf { it > 0 } ?: 2
        if (channelCount > 2) return null
        val sourceEncoding = sourceEncoding(formatConfig.format.pcmEncoding) ?: return null
        val requestedBitDepth = bitDepthOf(sourceEncoding)
        val spec = UsbPcmFormatSpec(
            sampleRateHz = sampleRate,
            channelCount = channelCount,
            bitDepth = requestedBitDepth,
        )
        val usbFormat = UsbPcmFormatSelector.chooseClosestFormat(snapshot.descriptor, spec) ?: return null
        val destBytes = UsbPcmPacker.bytesPerSample(usbFormat.bitResolution ?: requestedBitDepth, usbFormat.subslotSize)
        val encoding = outputEncoding(sourceEncoding)
        val channelMask = if (channelCount <= 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val outputConfig = AudioOutputProvider.OutputConfig.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .setIsTunneling(false)
            .setIsOffload(false)
            .setBufferSize((sampleRate * channelCount * destBytes / 10).coerceAtLeast(4096))
            .setAudioAttributes(formatConfig.audioAttributes)
            .setUsePlaybackParameters(true)
            .setUseOffloadGapless(false)
            .build()
        return ExclusivePlan(spec = spec.copy(bitDepth = usbFormat.bitResolution ?: requestedBitDepth), outputConfig = outputConfig)
    }

    private fun exclusiveOutputOrNull(outputConfig: AudioOutputProvider.OutputConfig): AudioOutput? {
        val sourceEncoding = sourceEncoding(outputConfig.encoding) ?: return null
        val channelCount = Integer.bitCount(outputConfig.channelMask).coerceAtLeast(1)
        val bitDepth = bitDepthOf(sourceEncoding)
        val spec = UsbPcmFormatSpec(
            sampleRateHz = outputConfig.sampleRate,
            channelCount = channelCount,
            bitDepth = bitDepth,
        )
        val session = usbOutput.open(spec)
        if (!session.openResult.isReady) {
            session.close()
            return null
        }
        return EchoUsbExclusiveAudioOutput(
            session = session,
            outputConfig = outputConfig,
            sourceEncoding = sourceEncoding,
            destBytesPerSample = session.bytesPerSample,
        )
    }

    private fun sourceEncoding(pcmEncoding: Int): UsbPcmSourceEncoding? =
        when (pcmEncoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_16BIT_BIG_ENDIAN,
            -> UsbPcmSourceEncoding.Pcm16
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            -> UsbPcmSourceEncoding.Pcm24Packed
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN,
            -> UsbPcmSourceEncoding.Pcm32
            C.ENCODING_PCM_FLOAT -> UsbPcmSourceEncoding.PcmFloat
            else -> null
        }

    private fun outputEncoding(sourceEncoding: UsbPcmSourceEncoding): Int =
        when (sourceEncoding) {
            UsbPcmSourceEncoding.Pcm16 -> C.ENCODING_PCM_16BIT
            UsbPcmSourceEncoding.Pcm24Packed -> C.ENCODING_PCM_24BIT
            UsbPcmSourceEncoding.Pcm24In32,
            UsbPcmSourceEncoding.Pcm32,
            -> C.ENCODING_PCM_32BIT
            UsbPcmSourceEncoding.PcmFloat -> C.ENCODING_PCM_FLOAT
        }

    private fun bitDepthOf(sourceEncoding: UsbPcmSourceEncoding): Int =
        when (sourceEncoding) {
            UsbPcmSourceEncoding.Pcm16 -> 16
            UsbPcmSourceEncoding.Pcm24Packed,
            UsbPcmSourceEncoding.Pcm24In32,
            UsbPcmSourceEncoding.PcmFloat,
            -> 24
            UsbPcmSourceEncoding.Pcm32 -> 32
        }

    private data class ExclusivePlan(
        val spec: UsbPcmFormatSpec,
        val outputConfig: AudioOutputProvider.OutputConfig,
    )
}

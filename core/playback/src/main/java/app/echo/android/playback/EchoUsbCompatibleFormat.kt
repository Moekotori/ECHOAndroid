package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoUsbOutputOptions
import app.echo.android.usbaudio.UsbAudioDescriptorInfo
import app.echo.android.usbaudio.UsbPcmFormatSelector
import app.echo.android.usbaudio.UsbPcmFormatSpec
import kotlin.math.abs

@UnstableApi
internal fun selectUsbCompatibleFormat(
    input: AudioProcessor.AudioFormat,
    options: EchoUsbOutputOptions,
    descriptor: UsbAudioDescriptorInfo,
    reportedRates: List<Int>,
): AudioProcessor.AudioFormat? {
    val formats = descriptor.streamingFormats.filter {
        it.formatType == 1 && it.pcmIntegerSupported == true && it.channelCount in 1..2 &&
            it.bitResolution in listOf(16, 24, 32) && it.subslotSize in 2..4 && it.bitResolution!! <= it.subslotSize!! * 8
    }
    val automatic = options.sampleRateHz == 0 && options.bitDepth == 0 && options.channelCount == 0
    // Legacy descriptors may omit PCM details. Preserve their existing output negotiation.
    if (automatic && formats.isEmpty()) return null
    val pcmDescriptor = descriptor.copy(streamingFormats = formats)
    val channels = when {
        options.channelCount > 0 -> listOf(options.channelCount)
        input.channelCount == 1 -> listOf(1, 2)
        else -> listOf(2, 1)
    }
    val rates = if (options.sampleRateHz > 0) listOf(options.sampleRateHz) else {
        val advertised = reportedRates.filter { it in 8_000..384_000 }
        val knownRates = (advertised + pcmDescriptor.sampleRates + formats.flatMap {
            listOfNotNull(it.sampleRateMinHz, it.sampleRateMaxHz)
        }).filter { it in 8_000..384_000 }.distinct().sortedBy { abs(it - input.sampleRate) }
        val explicitlySupported = formats.any {
            input.sampleRate in it.sampleRates || (it.sampleRates.isEmpty() &&
                input.sampleRate >= (it.sampleRateMinHz ?: Int.MAX_VALUE) &&
                input.sampleRate <= (it.sampleRateMaxHz ?: Int.MIN_VALUE))
        }
        if (advertised.isEmpty() || input.sampleRate in advertised || explicitlySupported)
            (listOf(input.sampleRate) + knownRates).distinct()
        else knownRates + input.sampleRate
    }
    val depth = options.bitDepth.takeIf { it > 0 } ?: pcmBits(input.encoding) ?: 32
    for (count in channels) {
        for (rate in rates) {
            val spec = UsbPcmFormatSpec(rate, count, depth)
            val selected = if (options.bitDepth > 0) UsbPcmFormatSelector.chooseFormat(pcmDescriptor, spec)
                else UsbPcmFormatSelector.chooseClosestFormat(pcmDescriptor, spec)
            val selectedDepth = selected?.bitResolution ?: continue
            // The output packer already adapts integer container width without a resampler.
            if (rate == input.sampleRate && count == input.channelCount && options.bitDepth == 0) return input
            val encoding = when (selectedDepth) {
                16 -> C.ENCODING_PCM_16BIT
                24 -> C.ENCODING_PCM_24BIT
                else -> C.ENCODING_PCM_32BIT
            }
            return AudioProcessor.AudioFormat(rate, count, encoding)
        }
    }
    throw AudioProcessor.UnhandledAudioFormatException(input)
}

package app.echo.android.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbPcmFormatSelectorTest {
    @Test
    fun choosesExactIsochronousFormat() {
        val format = UsbAudioStreamingFormat(
            interfaceNumber = 1,
            alternateSetting = 2,
            audioClassVersion = UsbAudioClassVersion.Uac1,
            channelCount = 2,
            bitResolution = 24,
            sampleRates = listOf(44100, 48000),
            endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous,
        )

        val selected = UsbPcmFormatSelector.chooseFormat(
            UsbAudioDescriptorInfo(streamingFormats = listOf(format)),
            UsbPcmFormatSpec(sampleRateHz = 48000, channelCount = 2, bitDepth = 24),
        )

        assertEquals(format, selected)
    }

    @Test
    fun rejectsKnownMismatchedSampleRate() {
        val format = UsbAudioStreamingFormat(
            interfaceNumber = 1,
            alternateSetting = 1,
            audioClassVersion = UsbAudioClassVersion.Uac1,
            bitResolution = 16,
            sampleRates = listOf(44100),
            endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous,
        )

        val selected = UsbPcmFormatSelector.chooseFormat(
            UsbAudioDescriptorInfo(streamingFormats = listOf(format)),
            UsbPcmFormatSpec(sampleRateHz = 96000, channelCount = 2, bitDepth = 16),
        )

        assertNull(selected)
    }

    @Test
    fun allowsUac2FormatsWithoutDescriptorSampleRates() {
        val format = UsbAudioStreamingFormat(
            interfaceNumber = 2,
            alternateSetting = 1,
            audioClassVersion = UsbAudioClassVersion.Uac2,
            bitResolution = 32,
            sampleRates = emptyList(),
            endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous,
        )

        val selected = UsbPcmFormatSelector.chooseFormat(
            UsbAudioDescriptorInfo(streamingFormats = listOf(format)),
            UsbPcmFormatSpec(sampleRateHz = 192000, channelCount = 2, bitDepth = 32),
        )

        assertEquals(format, selected)
    }

    @Test
    fun closestFormatFallsBackToNearestBitDepthWhenExactIsMissing() {
        val twentyFour = UsbAudioStreamingFormat(
            interfaceNumber = 1,
            alternateSetting = 1,
            audioClassVersion = UsbAudioClassVersion.Uac2,
            bitResolution = 24,
            sampleRates = listOf(48_000),
            endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous,
        )

        val exact = UsbPcmFormatSelector.chooseFormat(
            UsbAudioDescriptorInfo(streamingFormats = listOf(twentyFour)),
            UsbPcmFormatSpec(sampleRateHz = 48_000, channelCount = 2, bitDepth = 16),
        )
        val closest = UsbPcmFormatSelector.chooseClosestFormat(
            UsbAudioDescriptorInfo(streamingFormats = listOf(twentyFour)),
            UsbPcmFormatSpec(sampleRateHz = 48_000, channelCount = 2, bitDepth = 16),
        )

        assertNull(exact)
        assertEquals(24, closest?.bitResolution)
    }
}

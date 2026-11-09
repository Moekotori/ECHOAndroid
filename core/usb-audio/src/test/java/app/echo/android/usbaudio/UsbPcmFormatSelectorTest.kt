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
    fun continuousUac1RangeAcceptsRatesBetweenMinAndMax() {
        val format = UsbAudioStreamingFormat(
            interfaceNumber = 1,
            alternateSetting = 1,
            audioClassVersion = UsbAudioClassVersion.Uac1,
            bitResolution = 16,
            sampleRates = emptyList(),
            sampleRateMinHz = 8_000,
            sampleRateMaxHz = 96_000,
            endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous,
        )
        val descriptor = UsbAudioDescriptorInfo(streamingFormats = listOf(format))

        assertEquals(
            format,
            UsbPcmFormatSelector.chooseFormat(
                descriptor,
                UsbPcmFormatSpec(sampleRateHz = 48_000, channelCount = 2, bitDepth = 16),
            ),
        )
        assertNull(
            UsbPcmFormatSelector.chooseFormat(
                descriptor,
                UsbPcmFormatSpec(sampleRateHz = 192_000, channelCount = 2, bitDepth = 16),
            ),
        )
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

    @Test
    fun choosesOnlyIntegerPcmWithSupportedFeedbackAndRequiresClockReadbackForStrictMode() {
        val pcm = UsbAudioStreamingFormat(1, 3, UsbAudioClassVersion.Uac1, formatType = 1,
            pcmIntegerSupported = true, channelCount = 2, subslotSize = 2, bitResolution = 16,
            sampleRates = listOf(48_000), endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous, uac1SampleRateControl = false)
        val descriptor = UsbAudioDescriptorInfo(streamingFormats = listOf(
            pcm.copy(alternateSetting = 1, pcmIntegerSupported = false),
            pcm.copy(alternateSetting = 2, requiresImplicitFeedback = true),
            pcm,
        ))
        val spec = UsbPcmFormatSpec(48_000, 2, 16)
        assertEquals(pcm, UsbPcmFormatSelector.chooseClosestFormat(descriptor, spec))
        assertNull(UsbPcmFormatSelector.chooseBitPerfectFormat(descriptor, spec))
    }

    @Test
    fun skipsAlternateSettingsThatCannotFitTheRateAtTheNegotiatedBusSpeed() {
        val narrow = UsbAudioStreamingFormat(1, 1, UsbAudioClassVersion.Uac2, formatType = 1,
            pcmIntegerSupported = true, channelCount = 2, subslotSize = 3, bitResolution = 24,
            endpointDirection = UsbEndpointDirection.Out, endpointTransferType = UsbEndpointTransferType.Isochronous,
            maxPacketSize = 72, endpointInterval = 2)
        val wide = narrow.copy(alternateSetting = 2, maxPacketSize = 144)
        val spec = UsbPcmFormatSpec(96_000, 2, 24)
        val descriptor = UsbAudioDescriptorInfo(streamingFormats = listOf(narrow, wide), busPacketsPerSecond = 8_000)
        assertEquals(wide, UsbPcmFormatSelector.chooseClosestFormat(descriptor, spec))
        assertEquals(wide, UsbPcmFormatSelector.chooseBitPerfectFormat(descriptor, spec))
        assertNull(UsbPcmFormatSelector.chooseClosestFormat(descriptor.copy(busPacketsPerSecond = 1_000), spec))
        assertEquals(narrow, UsbPcmFormatSelector.chooseClosestFormat(descriptor.copy(busPacketsPerSecond = null), spec))
    }
}

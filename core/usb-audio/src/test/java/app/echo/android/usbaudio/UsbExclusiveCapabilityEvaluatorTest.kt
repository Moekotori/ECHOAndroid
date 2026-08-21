package app.echo.android.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbExclusiveCapabilityEvaluatorTest {
    @Test
    fun reportsNativeWriterRequiredForIsochronousEndpoint() {
        val snapshot = snapshot(
            UsbAudioStreamingFormat(
                interfaceNumber = 1,
                alternateSetting = 1,
                audioClassVersion = UsbAudioClassVersion.Uac2,
                bitResolution = 24,
                sampleRates = listOf(96_000),
                endpointDirection = UsbEndpointDirection.Out,
                endpointTransferType = UsbEndpointTransferType.Isochronous,
            ),
        )

        val capability = UsbExclusiveCapabilityEvaluator.evaluate(
            snapshot,
            UsbPcmFormatSpec(sampleRateHz = 96_000, channelCount = 2, bitDepth = 24),
        )

        assertEquals(UsbExclusiveCapabilityState.NativeIsochronousWriterRequired, capability.state)
    }

    @Test
    fun reportsNativeWriterReadyWhenLibraryIsPresent() {
        val snapshot = snapshot(
            UsbAudioStreamingFormat(
                interfaceNumber = 1,
                alternateSetting = 1,
                audioClassVersion = UsbAudioClassVersion.Uac2,
                bitResolution = 24,
                sampleRates = listOf(96_000),
                endpointDirection = UsbEndpointDirection.Out,
                endpointTransferType = UsbEndpointTransferType.Isochronous,
            ),
        )

        val capability = UsbExclusiveCapabilityEvaluator.evaluate(
            snapshot,
            UsbPcmFormatSpec(sampleRateHz = 96_000, channelCount = 2, bitDepth = 24),
            nativeWriterAvailable = true,
        )

        assertEquals(UsbExclusiveCapabilityState.ReadyForNativeIsochronousWrite, capability.state)
    }

    @Test
    fun reportsBulkReadyForBulkEndpoint() {
        val snapshot = snapshot(
            UsbAudioStreamingFormat(
                interfaceNumber = 1,
                alternateSetting = 1,
                audioClassVersion = UsbAudioClassVersion.Uac1,
                bitResolution = 16,
                sampleRates = listOf(48_000),
                endpointDirection = UsbEndpointDirection.Out,
                endpointTransferType = UsbEndpointTransferType.Bulk,
            ),
        )

        val capability = UsbExclusiveCapabilityEvaluator.evaluate(
            snapshot,
            UsbPcmFormatSpec(sampleRateHz = 48_000, channelCount = 2, bitDepth = 16),
        )

        assertEquals(UsbExclusiveCapabilityState.ReadyForFrameworkBulkWrite, capability.state)
    }

    private fun snapshot(format: UsbAudioStreamingFormat): UsbAudioDeviceSnapshot =
        UsbAudioDeviceSnapshot(
            connected = true,
            permissionGranted = true,
            descriptor = UsbAudioDescriptorInfo(streamingFormats = listOf(format)),
        )
}

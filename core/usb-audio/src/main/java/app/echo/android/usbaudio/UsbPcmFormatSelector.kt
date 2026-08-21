package app.echo.android.usbaudio

object UsbPcmFormatSelector {
    fun chooseFormat(
        descriptor: UsbAudioDescriptorInfo,
        spec: UsbPcmFormatSpec,
    ): UsbAudioStreamingFormat? =
        candidates(descriptor, spec, requireBitDepth = true).firstOrNull()

    fun chooseClosestFormat(
        descriptor: UsbAudioDescriptorInfo,
        spec: UsbPcmFormatSpec,
    ): UsbAudioStreamingFormat? =
        chooseFormat(descriptor, spec) ?: candidates(descriptor, spec, requireBitDepth = false).firstOrNull()

    private fun candidates(
        descriptor: UsbAudioDescriptorInfo,
        spec: UsbPcmFormatSpec,
        requireBitDepth: Boolean,
    ): Sequence<UsbAudioStreamingFormat> =
        descriptor.streamingFormats
            .asSequence()
            .filter { it.isIsochronousOut || it.endpointTransferType == UsbEndpointTransferType.Bulk }
            .filter { it.endpointDirection == UsbEndpointDirection.Out }
            .filter { format ->
                !requireBitDepth || format.bitResolution == null || format.bitResolution == spec.bitDepth
            }
            .filter { format -> format.channelCount == null || format.channelCount == spec.channelCount }
            .filter { format ->
                format.sampleRates.isEmpty() || spec.sampleRateHz in format.sampleRates
            }
            .sortedWith(
                compareByDescending<UsbAudioStreamingFormat> { it.isIsochronousOut }
                    .thenBy {
                        kotlin.math.abs((it.bitResolution ?: spec.bitDepth) - spec.bitDepth)
                    }
                    .thenByDescending { it.sampleRates.isNotEmpty() }
                    .thenByDescending { it.channelCount == spec.channelCount }
                    .thenBy { it.interfaceNumber }
                    .thenBy { it.alternateSetting },
            )
}

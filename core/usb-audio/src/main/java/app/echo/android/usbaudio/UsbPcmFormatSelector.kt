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
            .filter { format -> supportsSampleRate(format, spec.sampleRateHz) }
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

    private fun supportsSampleRate(format: UsbAudioStreamingFormat, sampleRateHz: Int): Boolean {
        if (format.sampleRates.isNotEmpty()) return sampleRateHz in format.sampleRates
        val minHz = format.sampleRateMinHz
        val maxHz = format.sampleRateMaxHz
        if (minHz != null && maxHz != null && minHz > 0 && maxHz >= minHz) {
            return sampleRateHz in minHz..maxHz
        }
        return true
    }
}

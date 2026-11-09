package app.echo.android.usbaudio

object UsbPcmFormatSelector {
    // Strict mode permits lossless widening, never unknown precision or narrowing.
    fun chooseBitPerfectFormat(descriptor: UsbAudioDescriptorInfo, spec: UsbPcmFormatSpec): UsbAudioStreamingFormat? =
        candidates(descriptor, spec, requireBitDepth = false)
            .filter { it.isIsochronousOut && it.channelCount == spec.channelCount }
            .filter { it.uac1SampleRateControl != false }
            .filter { it.formatType == 1 && it.pcmIntegerSupported == true }
            .filter { (it.bitResolution ?: 0) in spec.bitDepth..32 }
            .filter { it.bitResolution in listOf(16, 24, 32) && it.subslotSize in 2..4 }
            .filter { it.bitResolution!! <= it.subslotSize!! * 8 }
            .sortedBy { it.bitResolution }
            .firstOrNull()

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

    internal fun hasImplicitFeedbackFormat(descriptor: UsbAudioDescriptorInfo, spec: UsbPcmFormatSpec): Boolean =
        candidates(descriptor, spec, requireBitDepth = false, implicitFeedback = true).any()

    private fun candidates(
        descriptor: UsbAudioDescriptorInfo,
        spec: UsbPcmFormatSpec,
        requireBitDepth: Boolean,
        implicitFeedback: Boolean = false,
    ): Sequence<UsbAudioStreamingFormat> =
        descriptor.streamingFormats
            .asSequence()
            .filter { it.isIsochronousOut || it.endpointTransferType == UsbEndpointTransferType.Bulk }
            .filter { it.endpointDirection == UsbEndpointDirection.Out }
            .filter { it.audioClassVersion != UsbAudioClassVersion.Uac3 }
            .filter { it.requiresImplicitFeedback == implicitFeedback }
            .filter { it.pcmIntegerSupported != false && (it.formatType == null || it.formatType == 1) }
            .filter { format ->
                !requireBitDepth || format.bitResolution == null || format.bitResolution == spec.bitDepth
            }
            .filter { format -> format.channelCount == null || format.channelCount == spec.channelCount }
            .filter { format -> supportsSampleRate(format, spec.sampleRateHz) }
            .filter { format -> supportsPacketSize(format, spec, descriptor.busPacketsPerSecond) }
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
        return !(minHz != null && maxHz != null && minHz > 0 && maxHz >= minHz) || sampleRateHz in minHz..maxHz
    }

    private fun supportsPacketSize(format: UsbAudioStreamingFormat, spec: UsbPcmFormatSpec, packetsPerSecond: Int?): Boolean {
        if (!format.isIsochronousOut || packetsPerSecond == null || packetsPerSecond <= 0) return true
        val rawMaxPacket = format.maxPacketSize ?: return true
        val interval = format.endpointInterval?.takeIf { it in 1..16 } ?: return true
        val ratePerService = spec.sampleRateHz.toLong() shl (interval - 1)
        val frames = (ratePerService + packetsPerSecond - 1) / packetsPerSecond
        val channels = format.channelCount ?: spec.channelCount
        val width = UsbPcmPacker.bytesPerSample(format.bitResolution ?: spec.bitDepth, format.subslotSize)
        return frames * channels * width <= UsbIsoPacketizer.maxPacketPayloadBytes(rawMaxPacket)
    }
}

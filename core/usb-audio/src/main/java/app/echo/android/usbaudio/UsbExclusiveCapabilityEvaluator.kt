package app.echo.android.usbaudio

object UsbExclusiveCapabilityEvaluator {
    fun evaluate(
        snapshot: UsbAudioDeviceSnapshot,
        spec: UsbPcmFormatSpec,
        nativeWriterAvailable: Boolean = UsbIsochronousNative.available,
    ): UsbExclusiveCapability {
        if (!snapshot.connected) {
            return UsbExclusiveCapability(
                state = UsbExclusiveCapabilityState.DeviceUnavailable,
                message = "No USB Audio Class device detected",
            )
        }
        if (!snapshot.permissionGranted) {
            return UsbExclusiveCapability(
                state = UsbExclusiveCapabilityState.PermissionRequired,
                message = "USB audio permission is required",
            )
        }
        val format = UsbPcmFormatSelector.chooseFormat(snapshot.descriptor, spec)
            ?: return UsbExclusiveCapability(
                state = UsbExclusiveCapabilityState.FormatUnavailable,
                message = "No UAC streaming format matches ${spec.sampleRateHz}Hz/${spec.bitDepth}bit/${spec.channelCount}ch",
            )

        return when (format.endpointTransferType) {
            UsbEndpointTransferType.Bulk -> UsbExclusiveCapability(
                state = UsbExclusiveCapabilityState.ReadyForFrameworkBulkWrite,
                selectedFormat = format,
                message = "Bulk OUT endpoint can use Android UsbDeviceConnection writes",
            )
            UsbEndpointTransferType.Isochronous -> UsbExclusiveCapability(
                state = if (nativeWriterAvailable) {
                    UsbExclusiveCapabilityState.ReadyForNativeIsochronousWrite
                } else {
                    UsbExclusiveCapabilityState.NativeIsochronousWriterRequired
                },
                selectedFormat = format,
                message = if (nativeWriterAvailable) {
                    "Isochronous OUT endpoint can use the exclusive USB writer"
                } else {
                    "Isochronous OUT endpoint requires native writer before playback"
                },
            )
            else -> UsbExclusiveCapability(
                state = UsbExclusiveCapabilityState.FormatUnavailable,
                selectedFormat = format,
                message = "Selected endpoint is not a PCM output endpoint",
            )
        }
    }
}

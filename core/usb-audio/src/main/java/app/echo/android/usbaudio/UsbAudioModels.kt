package app.echo.android.usbaudio

data class UsbAudioDeviceSnapshot(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val displayName: String? = null,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val permissionGranted: Boolean = false,
    val permissionPending: Boolean = false,
    val rawDescriptorAvailable: Boolean = false,
    val descriptor: UsbAudioDescriptorInfo = UsbAudioDescriptorInfo(),
    val descriptorError: String? = null,
) {
    val sampleRates: List<Int>
        get() = descriptor.sampleRates
}

data class UsbAudioDescriptorInfo(
    val audioInterfaceCount: Int = 0,
    val audioControlInterfaceCount: Int = 0,
    val audioStreamingInterfaceCount: Int = 0,
    val classVersions: Set<UsbAudioClassVersion> = emptySet(),
    val streamingFormats: List<UsbAudioStreamingFormat> = emptyList(),
    val hasIsochronousOut: Boolean = false,
    val hasFeedbackEndpoint: Boolean = false,
    val acInterfaceNumber: Int? = null,
    val clockSourceIds: List<Int> = emptyList(),
    val usbVersion: Int? = null,
) {
    val sampleRates: List<Int>
        get() = streamingFormats
            .flatMap { it.sampleRates }
            .filter { it > 0 }
            .distinct()
            .sorted()
}

enum class UsbAudioClassVersion(val label: String) {
    Uac1("UAC1"),
    Uac2("UAC2"),
    Uac3("UAC3"),
    Unknown("UAC"),
}

data class UsbAudioStreamingFormat(
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val audioClassVersion: UsbAudioClassVersion,
    val formatType: Int? = null,
    val channelCount: Int? = null,
    val subslotSize: Int? = null,
    val bitResolution: Int? = null,
    val sampleRates: List<Int> = emptyList(),
    val endpointAddress: Int? = null,
    val endpointDirection: UsbEndpointDirection? = null,
    val endpointTransferType: UsbEndpointTransferType? = null,
    val endpointSyncType: UsbEndpointSyncType? = null,
    val endpointUsageType: UsbEndpointUsageType? = null,
    val maxPacketSize: Int? = null,
    val endpointInterval: Int? = null,
    val synchAddress: Int? = null,
    val feedbackEndpointAddress: Int? = null,
    val feedbackMaxPacketSize: Int? = null,
    val acInterfaceNumber: Int? = null,
    val terminalLink: Int? = null,
    val clockSourceIds: List<Int> = emptyList(),
) {
    val isIsochronousOut: Boolean
        get() = endpointDirection == UsbEndpointDirection.Out &&
            endpointTransferType == UsbEndpointTransferType.Isochronous
}

enum class UsbEndpointDirection {
    In,
    Out,
}

enum class UsbEndpointTransferType {
    Control,
    Isochronous,
    Bulk,
    Interrupt,
}

enum class UsbEndpointSyncType {
    None,
    Asynchronous,
    Adaptive,
    Synchronous,
}

enum class UsbEndpointUsageType {
    Data,
    Feedback,
    ImplicitFeedback,
    Reserved,
}

data class UsbPcmFormatSpec(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepth: Int,
)

enum class UsbExclusiveOutputState {
    Idle,
    Opening,
    Ready,
    Streaming,
    UnsupportedTransport,
    PermissionDenied,
    DeviceUnavailable,
    FormatUnavailable,
    OpenFailed,
    Closed,
}

data class UsbExclusiveOpenResult(
    val state: UsbExclusiveOutputState,
    val selectedFormat: UsbAudioStreamingFormat? = null,
    val message: String? = null,
) {
    val isReady: Boolean
        get() = state == UsbExclusiveOutputState.Ready
}

data class UsbPcmWriteResult(
    val state: UsbExclusiveOutputState,
    val bytesWritten: Int = 0,
    val message: String? = null,
)

enum class UsbExclusiveCapabilityState {
    ReadyForFrameworkBulkWrite,
    ReadyForNativeIsochronousWrite,
    NativeIsochronousWriterRequired,
    PermissionRequired,
    DeviceUnavailable,
    FormatUnavailable,
}

data class UsbExclusiveCapability(
    val state: UsbExclusiveCapabilityState,
    val selectedFormat: UsbAudioStreamingFormat? = null,
    val message: String,
)

enum class PcmEncoding {
    PcmInteger,
    PcmFloat,
}

data class PcmStreamDescriptor(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepth: Int,
    val encoding: PcmEncoding,
    val dataOffset: Int,
    val dataSize: Int,
) {
    val formatSpec: UsbPcmFormatSpec
        get() = UsbPcmFormatSpec(
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitDepth = bitDepth,
        )
}

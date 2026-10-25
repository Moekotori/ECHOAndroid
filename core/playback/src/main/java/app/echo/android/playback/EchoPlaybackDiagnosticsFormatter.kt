package app.echo.android.playback

import app.echo.android.model.playback.EchoPlaybackDiagnostics

fun EchoPlaybackDiagnostics.toReadableLines(): List<String> = buildList {
    add("route=$outputRoute")
    add("outputDeviceKind=$outputDeviceKind")
    outputDeviceName?.let { add("outputDevice=$it") }
    bluetoothCodec?.let { add("bluetoothCodec=$it") }
    add("outputRouteVerified=$outputRouteVerified")
    if (routedDeviceNames.isNotEmpty()) add("routedDevices=${routedDeviceNames.joinToString(",")}")
    add("offload=$offloadActive")
    add("usbExclusiveEnabled=$usbExclusiveEnabled")
    add("usbConnected=$usbConnected")
    usbDeviceName?.let { add("usbDevice=$it") }
    add("usbHostPermissionGranted=$usbHostPermissionGranted")
    add("usbHostPermissionPending=$usbHostPermissionPending")
    usbAudioClass?.let { add("usbAudioClass=$it") }
    add("usbAudioInterfaceCount=$usbAudioInterfaceCount")
    add("usbAudioStreamingInterfaceCount=$usbAudioStreamingInterfaceCount")
    add("usbAudioHasIsochronousOut=$usbAudioHasIsochronousOut")
    add("usbAudioHasFeedbackEndpoint=$usbAudioHasFeedbackEndpoint")
    usbAudioEndpointSummary?.let { add("usbAudioEndpoint=$it") }
    usbAudioDescriptorError?.let { add("usbAudioDescriptorError=$it") }
    add("usbBitPerfectSupported=$usbBitPerfectSupported")
    add("usbBitPerfectActive=$usbBitPerfectActive")
    add("bitPerfectState=$bitPerfectState")
    add("bitPerfectPrecision=$bitPerfectSourceBits/$bitPerfectDecodedBits/$bitPerfectOutputBits")
    add("bitPerfectSampleRateHz=$bitPerfectSampleRateHz")
    add("usbExclusiveStreaming=$usbExclusiveStreaming")
    usbExclusiveTransport?.let { add("usbExclusiveTransport=$it") }
    if (usbSupportedSampleRates.isNotEmpty()) {
        add("usbSupportedSampleRates=${usbSupportedSampleRates.joinToString(",")}")
    }
    usbLastRequestedSampleRateHz?.let { add("usbLastRequestedSampleRateHz=$it") }
    usbLastRequestError?.let { add("usbLastRequestError=${it.kind}:${it.message}") }
    usbTransfer?.let { t ->
        add("usbTransfer.sessionClosed=${t.sessionClosed}")
        add("usbTransfer.sampledAtElapsedMs=${t.sampledAtElapsedMs}")
        add("usbTransfer.queuedFrames=${t.queuedFrames}")
        add("usbTransfer.queuedBytes=${t.queuedBytes}")
        add("usbTransfer.minQueuedFrames=${t.minQueuedFrames}")
        add("usbTransfer.capacityFrames=${t.capacityFrames}")
        add("usbTransfer.audioUrbs=${t.audioUrbs}")
        add("usbTransfer.feedbackUrbs=${t.feedbackUrbs}")
        add("usbTransfer.audioPacketErrors=${t.audioPacketErrors}")
        add("usbTransfer.feedbackPacketErrors=${t.feedbackPacketErrors}")
        add("usbTransfer.submitFailures=${t.submitFailures}")
        add("usbTransfer.reapFailures=${t.reapFailures}")
        add("usbTransfer.estimatedSupplyGaps=${t.supplyGapCount}")
        add("usbTransfer.estimatedSupplyGapBytes=${t.supplyGapBytes}")
        add("usbTransfer.invalidFeedback=${t.invalidFeedbackCount}")
        add("usbTransfer.lastError=${t.lastErrorSource}:${t.lastErrorCode}")
        add("usbTransfer.fatal=${t.fatal}")
    }
    usbCompatibilityRules.forEach { rule ->
        add("usbCompatibilityRule=${rule.id}: ${rule.reason} ${rule.verification}")
    }
    add("bufferedMs=$bufferedMs")
    add("requestToken=$requestToken")
    codec?.let { add("codec=$it") }
    sampleRateHz?.let { add("sampleRateHz=$it") }
    decodedSampleRateHz?.let { add("decodedSampleRateHz=$it") }
    channelCount?.let { add("channelCount=$it") }
    bitrate?.let { add("bitrate=$it") }
    lastCommand?.let { add("lastCommand=$it") }
    lastError?.let { add("lastError=${it.kind}:${it.message}") }
}

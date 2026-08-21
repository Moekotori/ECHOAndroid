package app.echo.android.playback

import android.content.Context
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.usbaudio.UsbExclusiveOutputState
import app.echo.android.usbaudio.UsbExclusivePcmOutput
import app.echo.android.usbaudio.UsbEndpointTransferType
import app.echo.android.usbaudio.UsbPcmFormatSpec

class EchoUsbExclusiveDriverTester(context: Context) {
    private val output = UsbExclusivePcmOutput(context.applicationContext)

    fun testOpen(diagnostics: EchoPlaybackDiagnostics): String {
        val sampleRate = diagnostics.sampleRateHz
            ?: diagnostics.usbLastRequestedSampleRateHz
            ?: diagnostics.usbSupportedSampleRates.firstOrNull()
            ?: 48_000
        val channelCount = diagnostics.channelCount?.takeIf { it > 0 } ?: 2
        val bitDepth = diagnostics.bitDepth?.takeIf { it > 0 } ?: 24
        val spec = UsbPcmFormatSpec(
            sampleRateHz = sampleRate,
            channelCount = channelCount,
            bitDepth = bitDepth,
        )
        val session = output.open(spec)
        return try {
            val result = session.openResult
            val format = result.selectedFormat
            val target = "${sampleRate}Hz/${bitDepth}bit/${channelCount}ch"
            val endpoint = format?.endpointAddress?.let { "0x${it.toString(16)}" } ?: "none"
            when (result.state) {
                UsbExclusiveOutputState.Ready ->
                    if (format?.endpointTransferType == UsbEndpointTransferType.Isochronous) {
                        "Ready: exclusive isochronous writer on interface ${format.interfaceNumber}:${format.alternateSetting}, endpoint=$endpoint, target=$target"
                    } else {
                        "Ready: exclusive bulk writer on interface ${format?.interfaceNumber}:${format?.alternateSetting}, endpoint=$endpoint, target=$target"
                    }
                UsbExclusiveOutputState.PermissionDenied ->
                    "Permission denied: enable USB exclusive and allow ECHO in the system USB dialog"
                UsbExclusiveOutputState.DeviceUnavailable ->
                    "No USB Audio Class device detected"
                UsbExclusiveOutputState.FormatUnavailable ->
                    "Format unavailable: ${result.message ?: target}"
                UsbExclusiveOutputState.OpenFailed ->
                    "Open failed: ${result.message ?: target}"
                UsbExclusiveOutputState.UnsupportedTransport ->
                    "Unsupported transport: native isochronous writer is required"
                UsbExclusiveOutputState.Idle,
                UsbExclusiveOutputState.Opening,
                UsbExclusiveOutputState.Streaming,
                UsbExclusiveOutputState.Closed,
                -> result.message ?: result.state.name
            }
        } finally {
            session.close()
        }
    }
}

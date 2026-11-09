package app.echo.android.playback

import android.content.Context
import android.os.SystemClock
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.usbaudio.UsbExclusiveOutputState
import app.echo.android.usbaudio.UsbExclusivePcmOutput
import app.echo.android.usbaudio.UsbExclusivePcmSession
import app.echo.android.usbaudio.UsbEndpointTransferType
import app.echo.android.usbaudio.UsbPcmFormatSpec

class EchoUsbExclusiveDriverTester(context: Context) {
    private val output = UsbExclusivePcmOutput(context.applicationContext)

    fun testOpen(diagnostics: EchoPlaybackDiagnostics,
        options: app.echo.android.model.playback.EchoUsbOutputOptions = app.echo.android.model.playback.EchoUsbOutputOptions()): String {
        val sampleRate = options.sampleRateHz.takeIf { it > 0 } ?: diagnostics.sampleRateHz
            ?: diagnostics.usbLastRequestedSampleRateHz
            ?: diagnostics.usbSupportedSampleRates.firstOrNull()
            ?: 48_000
        val channelCount = options.channelCount.takeIf { it > 0 } ?: diagnostics.channelCount?.takeIf { it in 1..2 } ?: 2
        val bitDepth = options.bitDepth.takeIf { it > 0 } ?: diagnostics.bitDepth?.takeIf { it > 0 } ?: 24
        val spec = UsbPcmFormatSpec(
            sampleRateHz = sampleRate,
            channelCount = channelCount,
            bitDepth = bitDepth,
        )
        val session = output.open(spec, transferCount = options.bufferMode.transferCount,
            keepAliveOnPause = options.keepAliveOnPause,
            keepAliveByDevice = options.keepAliveByDevice)
        return try {
            val result = session.openResult
            val format = result.selectedFormat
            val target = "${sampleRate}Hz/${bitDepth}bit/${channelCount}ch"
            val endpoint = format?.endpointAddress?.let { "0x${it.toString(16)}" } ?: "none"
            when (result.state) {
                UsbExclusiveOutputState.Ready ->
                    verifyTransfer(session, spec)?.let { "Open failed: $it ($target)" }
                        ?: "Ready: USB PCM silence transferred on interface ${format?.interfaceNumber}:${format?.alternateSetting}, endpoint=$endpoint, target=$target"
                UsbExclusiveOutputState.PermissionDenied ->
                    "Permission denied: enable USB exclusive and allow ECHO in the system USB dialog"
                UsbExclusiveOutputState.DeviceUnavailable ->
                    "No USB Audio Class device detected"
                UsbExclusiveOutputState.DopConfigurationRequired ->
                    "DoP configuration required: ${result.message ?: target}"
                UsbExclusiveOutputState.FormatUnavailable ->
                    "Format unavailable: ${result.message ?: target}"
                UsbExclusiveOutputState.OpenFailed ->
                    "Open failed: ${result.message ?: target}"
                UsbExclusiveOutputState.UnsupportedTransport ->
                    "Unsupported transport: ${result.message ?: "native isochronous writer is required"}"
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

    private fun verifyTransfer(session: UsbExclusivePcmSession, spec: UsbPcmFormatSpec): String? {
        val frameBytes = (session.openResult.selectedFormat?.channelCount ?: spec.channelCount) * session.bytesPerSample
        val frames = (spec.sampleRateHz / 100).coerceIn(1, MAX_PROBE_BYTES / frameBytes)
        val silence = ByteArray(frames * frameBytes)
        val deadline = SystemClock.elapsedRealtime() + PROBE_TIMEOUT_MS
        var submittedFrames = 0
        val isIsochronous = session.transport == UsbEndpointTransferType.Isochronous
        while (SystemClock.elapsedRealtime() < deadline) {
            if (submittedFrames == 0) {
                val write = session.writePcm(silence)
                if (write.state != UsbExclusiveOutputState.Streaming) {
                    return write.message ?: "USB test write failed"
                }
                submittedFrames = write.bytesWritten / frameBytes
                if (!isIsochronous && submittedFrames > 0) return null
            }
            if (isIsochronous) {
                val completed = session.completedFrames()
                if (completed < 0) {
                    val failure = session.transferSnapshot
                    return "USB test transfer failed (${failure?.lastErrorSource ?: "USB"}: ${failure?.lastErrorCode ?: "unknown"})"
                }
                if (submittedFrames > 0 && completed >= submittedFrames) return null
            }
            Thread.sleep(PROBE_POLL_MS)
        }
        return "USB test transfer timed out"
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 300L
        const val PROBE_POLL_MS = 4L
        const val MAX_PROBE_BYTES = 32_768
    }
}

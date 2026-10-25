package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoPlaybackDiagnostics

/** Reused verbatim by the USB screen, signal diagnostics and the copyable device report. */
@Composable
internal fun usbTransportRows(d: EchoPlaybackDiagnostics): List<Pair<String, String>> = buildList {
    d.usbTransfer?.let { t ->
        fun frames(value: Long) = "${t.milliseconds(value)} ms / $value frames / ${value * t.bytesPerFrame} B"
        add(stringResource(R.string.usb_health_snapshot) to stringResource(
            if (t.sessionClosed) R.string.usb_health_last_session else R.string.usb_health_current_session))
        add(stringResource(R.string.usb_health_queue) to frames(t.queuedFrames))
        add(stringResource(R.string.usb_health_minimum) to (t.minQueuedFrames?.let(::frames)
            ?: stringResource(R.string.diag_unreported)))
        add(stringResource(R.string.usb_health_capacity) to frames(t.capacityFrames))
        add(stringResource(R.string.usb_health_completions) to "${t.audioUrbs} / ${t.feedbackUrbs}")
        add(stringResource(R.string.usb_health_packet_errors) to "${t.audioPacketErrors} / ${t.feedbackPacketErrors}")
        add(stringResource(R.string.usb_health_io_errors) to "${t.submitFailures} / ${t.reapFailures}")
        add(stringResource(R.string.usb_health_supply_gaps) to "${t.supplyGapCount} / ${t.supplyGapBytes} B")
        add(stringResource(R.string.usb_health_bad_feedback) to t.invalidFeedbackCount.toString())
        add(stringResource(R.string.usb_health_error) to (t.lastErrorSource?.let { "$it (${t.lastErrorCode})" }
            ?: stringResource(R.string.diag_none)))
        add(stringResource(R.string.usb_health_fatal) to t.fatal.toString())
    }
    if (d.usbCompatibilityRules.isNotEmpty()) {
        add(stringResource(R.string.usb_health_quirks) to d.usbCompatibilityRules.joinToString("; ") { it.id })
    }
}

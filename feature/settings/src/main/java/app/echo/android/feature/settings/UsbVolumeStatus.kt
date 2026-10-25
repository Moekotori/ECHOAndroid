package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*

@Composable
internal fun UsbVolumeStatus(options: EchoUsbOutputOptions, diagnostics: EchoPlaybackDiagnostics,
    strict: Boolean, onChange: (EchoUsbOutputOptions) -> Unit) {
    if (!strict) {
        EchoUsbVolumeMode.entries.forEach { mode ->
            Row {
                RadioButton(selected = options.volumeMode == mode, onClick = { onChange(options.copy(volumeMode = mode)) })
                Text(stringResource(when (mode) {
                    EchoUsbVolumeMode.Software -> R.string.usb_volume_software
                    EchoUsbVolumeMode.Auto -> R.string.usb_volume_auto
                    EchoUsbVolumeMode.Hardware -> R.string.usb_volume_hardware
                }), Modifier.padding(top = 12.dp))
            }
        }
    }
    val status = diagnostics.usbVolume
    val label = when {
        strict -> R.string.usb_volume_fixed
        status == null -> R.string.usb_volume_pending
        status.handoff -> R.string.usb_volume_handoff
        status.effective == "hardware" -> R.string.usb_volume_hardware_verified
        status.effective == "muted" -> R.string.usb_volume_muted
        else -> R.string.usb_volume_software
    }
    Text(stringResource(if (status?.sessionClosed == true) R.string.usb_volume_previous else R.string.usb_volume_actual, stringResource(label)), style = MaterialTheme.typography.bodySmall)
    status?.hardwareDb?.let { Text(stringResource(R.string.usb_volume_db, it), style = MaterialTheme.typography.bodySmall) }
    status?.reason?.let { reason ->
        val message = when (reason) {
            "device_changed" -> R.string.usb_volume_device_changed
            "waiting_for_playback" -> R.string.usb_volume_waiting
            "strict" -> R.string.usb_volume_strict_reason
            "no_playback_master" -> R.string.usb_volume_no_master
            "mute_unavailable" -> R.string.usb_volume_no_mute
            "range_unavailable", "unsafe_range" -> R.string.usb_volume_no_range
            "device_muted" -> R.string.usb_volume_device_muted
            else -> R.string.usb_volume_failed
        }
        Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.usb_volume_handoff_note), style = MaterialTheme.typography.bodySmall)
}

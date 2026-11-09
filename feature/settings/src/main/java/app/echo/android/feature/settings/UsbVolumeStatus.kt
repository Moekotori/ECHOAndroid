package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.model.playback.EchoUsbOutputOptions
import app.echo.android.model.playback.EchoUsbVolumeDiagnostics
import app.echo.android.model.playback.EchoUsbVolumeMode

@Composable
internal fun UsbVolumeStatus(
    options: EchoUsbOutputOptions,
    status: EchoUsbVolumeDiagnostics?,
    streaming: Boolean,
    onChange: (EchoUsbOutputOptions) -> Unit,
) {
    val software = stringResource(R.string.usb_volume_software)
    UsbOutputParameterPicker(
        title = stringResource(R.string.usb_volume_control_mode),
        value = when (options.volumeMode) {
            EchoUsbVolumeMode.Software -> software
            EchoUsbVolumeMode.Auto -> stringResource(R.string.usb_volume_auto_short)
        },
        selected = options.volumeMode.id,
        choices = listOf(
            UsbParameterChoice(EchoUsbVolumeMode.Software.id, software),
            UsbParameterChoice(EchoUsbVolumeMode.Auto.id, stringResource(R.string.usb_volume_auto)),
        ),
        onSelect = { onChange(options.copy(volumeMode = EchoUsbVolumeMode.fromId(it))) },
    )
    val live = status?.takeIf { streaming && !it.sessionClosed }
    val currentLabel = when {
        !streaming -> stringResource(R.string.usb_volume_pending)
        live == null -> stringResource(R.string.usb_volume_checking)
        else -> stringResource(R.string.usb_volume_actual, stringResource(live.labelResource()))
    }
    SignalNote(currentLabel, error = live?.effective == "muted")
    live?.reason?.let { SignalNote(stringResource(volumeReasonResource(it)), error = live.effective == "muted") }

    var showDetails by rememberSaveable { mutableStateOf(false) }
    SettingsDisclosureRow(
        title = stringResource(R.string.usb_volume_control_details),
        detail = "",
        expanded = showDetails,
        onExpandedChange = { showDetails = it },
    )
    EchoExpand(showDetails) {
        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val previous = status?.takeIf { it.sessionClosed }
            if (previous != null) {
                SignalNote(stringResource(R.string.usb_volume_previous, stringResource(previous.labelResource())))
            }
            (live ?: previous)?.hardwareDb?.let { SignalNote(stringResource(R.string.usb_volume_db, it)) }
            SignalNote(stringResource(R.string.usb_fine_note))
            if (options.volumeMode == EchoUsbVolumeMode.Auto) {
                SignalNote(stringResource(R.string.usb_volume_handoff_note))
            }
        }
    }
}

private fun EchoUsbVolumeDiagnostics.labelResource(): Int = when {
    effective == "muted" -> R.string.usb_volume_muted
    handoff -> R.string.usb_volume_handoff
    effective == "hardware" && readbackVerified -> R.string.usb_volume_hardware_verified
    effective == "software" -> R.string.usb_volume_software
    else -> R.string.usb_volume_checking
}

private fun volumeReasonResource(reason: String): Int = when (reason) {
    "device_changed" -> R.string.usb_volume_device_changed
    "waiting_for_playback" -> R.string.usb_volume_waiting
    "strict" -> R.string.usb_volume_strict_reason
    "no_playback_master" -> R.string.usb_volume_no_master
    "mute_unavailable" -> R.string.usb_volume_no_mute
    "range_unavailable", "unsafe_range" -> R.string.usb_volume_no_range
    "device_muted" -> R.string.usb_volume_device_muted
    else -> R.string.usb_volume_failed
}

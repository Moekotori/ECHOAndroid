package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoUsbOutputOptions
import app.echo.android.model.playback.resetFormatAndBuffer

@Composable
internal fun UsbOutputResetControls(options: EchoUsbOutputOptions, onChange: (EchoUsbOutputOptions) -> Unit) {
    var confirmFull by remember { mutableStateOf<Boolean?>(null) }
    SettingsActionRow(stringResource(R.string.usb_reset_format), stringResource(R.string.usb_reset_format_note),
        onClick = { confirmFull = false })
    SettingsActionRow(stringResource(R.string.usb_reset_full), stringResource(R.string.usb_reset_full_note),
        onClick = { confirmFull = true })
    confirmFull?.let { full ->
        AlertDialog(
            onDismissRequest = { confirmFull = null },
            title = { Text(stringResource(if (full) R.string.usb_reset_full else R.string.usb_reset_format)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.usb_reset_format_changes))
                if (full) {
                    Text(stringResource(R.string.usb_reset_full_changes))
                    Text(pluralStringResource(R.plurals.usb_reset_dop_count, options.dopDeviceLimits.size, options.dopDeviceLimits.size))
                    Text(stringResource(R.string.usb_reset_output_mode_kept))
                } else Text(stringResource(R.string.usb_reset_format_kept))
            } },
            confirmButton = { TextButton(onClick = {
                // Use the latest options; do not restore a stale snapshot from when the dialog opened.
                onChange(if (full) EchoUsbOutputOptions() else options.resetFormatAndBuffer())
                confirmFull = null
            }) { Text(stringResource(R.string.usb_reset_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmFull = null }) { Text(stringResource(R.string.usb_reset_cancel)) } },
        )
    }
}

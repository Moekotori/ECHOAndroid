package app.echo.android.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/** The timer only unlocks confirmation; it never changes the output mode. */
@Composable
internal fun UsbPassthroughWarningDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var secondsRemaining by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1_000)
            secondsRemaining--
        }
    }
    AlertDialog(
        shape = SettingsShape,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usb_passthrough_warning_title)) },
        text = { Text(stringResource(R.string.usb_passthrough_warning_body)) },
        confirmButton = {
            TextButton(
                shape = SettingsShape,
                enabled = secondsRemaining == 0,
                onClick = { if (secondsRemaining == 0) onConfirm() },
            ) {
                Text(
                    if (secondsRemaining > 0) stringResource(R.string.usb_passthrough_warning_wait, secondsRemaining)
                    else stringResource(R.string.usb_passthrough_warning_confirm),
                )
            }
        },
        dismissButton = {
            TextButton(shape = SettingsShape, onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

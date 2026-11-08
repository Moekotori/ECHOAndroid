package app.echo.android.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

@Composable
internal fun AudioSettingsReset(onReset: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsSectionCard(title = stringResource(R.string.audio_reset_section), secondary = true) {
        SettingsActionRow(
            title = stringResource(R.string.audio_output_reset),
            detail = stringResource(R.string.audio_reset_scope),
            actionLabel = stringResource(R.string.audio_output_reset_action),
            onClick = { confirming = true },
        )
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.audio_output_reset)) },
        text = { Text(stringResource(R.string.audio_reset_confirmation)) },
        confirmButton = { TextButton(onClick = { confirming = false; onReset() }) {
            Text(stringResource(R.string.audio_output_reset_action))
        } },
        dismissButton = { TextButton(onClick = { confirming = false }) {
            Text(stringResource(R.string.usb_reset_cancel))
        } },
    )
}

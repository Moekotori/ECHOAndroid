package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun KeyboardShortcutsDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.keyboard_help_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.keyboard_help_scope))
            listOf("Ctrl + 1…4" to R.string.keyboard_tabs, "Ctrl + ," to R.string.keyboard_settings,
                "Ctrl + Space" to R.string.keyboard_play, "Ctrl + → / ←" to R.string.keyboard_tracks,
                "Ctrl + Shift + → / ←" to R.string.keyboard_seek,
                "Ctrl + F" to R.string.keyboard_search, "Ctrl + /" to R.string.keyboard_help_title).forEach { (key, label) ->
                Column { Text(key, style = MaterialTheme.typography.labelLarge); Text(stringResource(label)) }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.keyboard_close)) } })
}

@Composable
internal fun KeyboardShortcutsHelpRow() {
    var open by remember { mutableStateOf(false) }
    SettingsActionRow(stringResource(R.string.keyboard_help_title), stringResource(R.string.keyboard_help_entry), onClick = { open = true })
    if (open) KeyboardShortcutsDialog { open = false }
}

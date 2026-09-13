package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun SettingsUpdateRow(onCheck: () -> Unit) {
    SettingsActionRow(title = stringResource(R.string.update_check),
        detail = stringResource(R.string.update_source), onClick = onCheck)
}

@Composable
fun SettingsUpdateDialog(version: String?, notes: String, busy: Boolean, progress: Int?,
    ready: Boolean, error: Boolean, onDismiss: () -> Unit, onCheck: () -> Unit,
    onDownload: () -> Unit, onInstall: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when {
                    error -> R.string.update_error
                    progress != null -> R.string.update_downloading
                    busy -> R.string.update_checking
                    ready -> R.string.update_ready
                    version != null -> R.string.update_available
                    else -> R.string.update_current
                }))
                if (version != null) Text(version)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress / 100f })
                    Text("$progress%")
                }
                if (notes.isNotBlank()) Text(notes)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = when {
                ready -> onInstall
                version != null -> onDownload
                else -> onCheck
            }) { Text(stringResource(when {
                ready -> R.string.update_install
                version != null -> R.string.update_download
                else -> R.string.update_check
            })) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

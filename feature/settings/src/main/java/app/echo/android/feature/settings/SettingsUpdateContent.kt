package app.echo.android.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

enum class UpdateProblem { Check, Download, Verification, Permission, Install }

@Composable
fun SettingsUpdateRow(onCheck: () -> Unit) {
    SettingsActionRow(title = stringResource(R.string.update_check),
        detail = stringResource(R.string.update_source), onClick = onCheck)
}

@Composable
fun SettingsUpdateDialog(currentVersion: String, version: String?, sizeBytes: Long?, notes: String,
    busy: Boolean, progress: Int?, ready: Boolean, error: UpdateProblem?, onDismiss: () -> Unit,
    onCheck: () -> Unit, onCancel: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (version != null) R.string.update_available else R.string.update_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.update_version_current, currentVersion),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (version != null) {
                    Text(version, style = MaterialTheme.typography.headlineSmall)
                    if (sizeBytes != null) Text(stringResource(R.string.update_package_size,
                        Formatter.formatFileSize(LocalContext.current, sizeBytes)),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(when {
                    error != null -> when (error) {
                        UpdateProblem.Check -> R.string.update_error_check
                        UpdateProblem.Download -> R.string.update_error_download
                        UpdateProblem.Verification -> R.string.update_error_verification
                        UpdateProblem.Permission -> R.string.update_error_permission
                        UpdateProblem.Install -> R.string.update_error_install
                    }
                    progress == 100 && busy -> R.string.update_verifying
                    progress != null -> R.string.update_downloading
                    busy -> R.string.update_checking
                    ready -> R.string.update_ready
                    version != null -> R.string.update_install_hint
                    else -> R.string.update_current
                }), color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.update_percent, progress))
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.update_cancel_download)) }
                } else if (busy) CircularProgressIndicator()
                if (notes.isNotBlank()) {
                    Text(stringResource(R.string.update_notes), style = MaterialTheme.typography.titleSmall)
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (!busy) TextButton(onClick = when {
                ready -> onInstall
                version != null -> onDownload
                error != null -> onCheck
                else -> onDismiss
            }) { Text(stringResource(when {
                ready -> R.string.update_install
                error != null -> R.string.update_retry
                version != null -> R.string.update_download
                else -> R.string.update_done
            })) }
        },
        dismissButton = {
            if (busy || version != null || error != null) TextButton(onClick = onDismiss) {
                Text(stringResource(if (progress != null) R.string.update_hide_download else R.string.update_later))
            }
        },
    )
}

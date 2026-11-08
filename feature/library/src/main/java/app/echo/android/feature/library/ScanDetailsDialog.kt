package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*

@Composable
internal fun ScanDetailsDialog(state: LibraryScanProgress, onRules: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.scan_details)) },
        text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryScanSkipReason.entries.forEach { reason ->
                val count = state.skipReasons[reason] ?: 0
                if (count > 0) item(key = "reason:$reason") {
                    Text(stringResource(R.string.scan_details_count, stringResource(scanReasonLabel(reason)), count), style = MaterialTheme.typography.titleSmall)
                }
            }
            item { Text(stringResource(R.string.scan_details_sample_hint), style = MaterialTheme.typography.bodySmall) }
            itemsIndexed(state.skippedSamples) { _, sample ->
                Column {
                    Text(sample.name.ifBlank { stringResource(R.string.scan_details_unreadable_item) }, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(scanReasonLabel(sample.reason)), style = MaterialTheme.typography.bodySmall)
                    sample.path?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { onDismiss(); onRules() }) { Text(stringResource(R.string.add_music_rules)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_import_review_done)) } })
}

private fun scanReasonLabel(reason: LibraryScanSkipReason) = when (reason) {
    LibraryScanSkipReason.Duration -> R.string.scan_reason_duration
    LibraryScanSkipReason.Size -> R.string.scan_reason_size
    LibraryScanSkipReason.Format -> R.string.scan_reason_format
    LibraryScanSkipReason.Directory -> R.string.scan_reason_directory
    LibraryScanSkipReason.Name -> R.string.scan_reason_name
    LibraryScanSkipReason.ReadFailure -> R.string.scan_reason_read
}

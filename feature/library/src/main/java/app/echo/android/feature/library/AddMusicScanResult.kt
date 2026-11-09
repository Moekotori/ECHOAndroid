package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress

@Composable
internal fun AddMusicScanResult(state: LibraryScanProgress, onCancel: () -> Unit, onRules: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    if (details) ScanDetailsDialog(state, onRules) { details = false }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.isScanning -> {
                    LibraryScanStatus(state, onCancel)
                    state.currentTitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                state.phase == LibraryScanPhase.Completed -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EchoIcon(Icons.Rounded.CheckCircleOutline, null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.feature_library_scan_complete_fbdf16),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.add_music_scan_summary, state.scannedCount),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScanResultMetric(state.insertedCount, stringResource(R.string.add_music_result_added), Modifier.weight(1f))
                            ScanResultMetric(state.updatedCount, stringResource(R.string.add_music_result_updated), Modifier.weight(1f))
                            ScanResultMetric(state.skippedCount, stringResource(R.string.add_music_result_skipped), Modifier.weight(1f))
                        }
                    }
                    if (state.deletedCount > 0) Text(stringResource(R.string.add_music_result_removed, state.deletedCount),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.scannedCount == 0) Text(stringResource(R.string.add_music_result_empty),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.unmatchedCueCount > 0) Text(stringResource(R.string.scan_unmatched_cue, state.unmatchedCueCount),
                        style = MaterialTheme.typography.bodySmall)
                    if (state.importedPlaylistCount > 0) Text(
                        stringResource(R.string.scan_folder_playlists_imported, state.importedPlaylistCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.playlistImportPartial) Text(
                        stringResource(R.string.scan_folder_playlists_partial),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.phase == LibraryScanPhase.Cancelled -> {
                    Text(stringResource(R.string.feature_library_scan_cancelled_1eefcb),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.feature_library_scan_cancelled_the_existing_library_was_kept_266734),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.phase == LibraryScanPhase.Error -> {
                    Text(stringResource(R.string.feature_library_scan_failed_f4c0ae),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
                else -> Unit
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.skipReasons.isNotEmpty()) TextButton(onClick = { details = true }) { Text(stringResource(R.string.scan_details)) }
        }
    }
}

@Composable
private fun ScanResultMetric(count: Int, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

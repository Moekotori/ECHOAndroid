package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.EchoPlaylist

@Composable
fun PlaylistImportDialog(
    state: PlaylistImportUiState?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpen: ((EchoPlaylist) -> Unit)?,
    searchCandidates: suspend (String) -> List<app.echo.android.model.library.PlaylistImportCandidate> = { emptyList() },
    onCorrect: (Int, app.echo.android.model.library.PlaylistImportCandidate?) -> Unit = { _, _ -> },
) {
    if (state == null) return
    val preview = state as? PlaylistImportUiState.Preview
    var name by rememberSaveable(preview?.name) { mutableStateOf(preview?.name.orEmpty()) }
    val saving = preview?.saving == true
    val exporting = state == PlaylistImportUiState.Exporting
    var reviewing by rememberSaveable(preview?.name) { mutableStateOf(false) }
    if (reviewing && preview != null) {
        PlaylistImportCorrections(preview.result, searchCandidates, onCorrect) { reviewing = false }
        return
    }
    val title = when {
        state is PlaylistImportUiState.Failed && state.reason == PlaylistImportFailure.Cover -> R.string.playlist_change_cover
        exporting || state == PlaylistImportUiState.Exported ||
            (state is PlaylistImportUiState.Failed && state.reason == PlaylistImportFailure.Export) -> R.string.playlist_export_format
        else -> R.string.playlist_import
    }
    AlertDialog(
        onDismissRequest = { if (!saving && !exporting) onDismiss() },
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    PlaylistImportUiState.Reading -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.playlist_import_reading))
                    }
                    PlaylistImportUiState.Exporting -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.playlist_export_writing))
                    }
                    is PlaylistImportUiState.Preview -> {
                        val result = state.result
                        Text(stringResource(R.string.playlist_import_summary, result.trackIds.size, result.totalEntries),
                            style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.playlist_import_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (result.duplicateEntries > 0) Text(pluralStringResource(R.plurals.playlist_import_duplicates, result.duplicateEntries, result.duplicateEntries))
                        if (result.unmatchedEntries > 0) Text(pluralStringResource(R.plurals.playlist_import_missing, result.unmatchedEntries, result.unmatchedEntries))
                        if (result.ambiguousEntries > 0) Text(pluralStringResource(R.plurals.playlist_import_ambiguous, result.ambiguousEntries, result.ambiguousEntries))
                        result.unresolvedNames.forEach { entry ->
                            Text(entry, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (result.trackIds.isEmpty()) Text(stringResource(R.string.playlist_import_no_matches))
                        if (result.entries.isNotEmpty()) TextButton(onClick = { reviewing = true }, enabled = !saving) { Text(stringResource(R.string.playlist_import_review)) }
                        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, enabled = !saving,
                            label = { Text(stringResource(R.string.playlist_import_name)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        if (state.saveFailed) Text(
                            stringResource(R.string.playlist_import_save_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is PlaylistImportUiState.Imported -> Text(pluralStringResource(R.plurals.playlist_imported,
                        state.playlist.trackCount, state.playlist.trackCount))
                    PlaylistImportUiState.Exported -> Text(stringResource(R.string.playlist_exported))
                    is PlaylistImportUiState.Failed -> Text(stringResource(when (state.reason) {
                        PlaylistImportFailure.TooLarge -> R.string.playlist_import_too_large
                        PlaylistImportFailure.TooMany -> R.string.playlist_import_too_many
                        PlaylistImportFailure.Empty -> R.string.playlist_import_empty
                        PlaylistImportFailure.NoMatches -> R.string.playlist_import_no_matches
                        PlaylistImportFailure.Export -> R.string.playlist_export_failed
                        PlaylistImportFailure.Read -> R.string.playlist_import_failed
                        PlaylistImportFailure.Cover -> R.string.playlist_action_error
                    }))
                }
            }
        },
        confirmButton = {
            when (state) {
                PlaylistImportUiState.Exporting -> Unit
                is PlaylistImportUiState.Preview -> TextButton(onClick = { onConfirm(name.trim()) },
                    enabled = !saving && name.isNotBlank() && state.result.trackIds.isNotEmpty()) {
                    Text(stringResource(if (saving) R.string.playlist_import_saving else R.string.playlist_import_confirm))
                }
                is PlaylistImportUiState.Imported -> TextButton(onClick = { onDismiss(); onOpen?.invoke(state.playlist) }) {
                    Text(stringResource(if (onOpen != null) R.string.playlist_import_view else R.string.feature_library_done_a5e659))
                }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_done_a5e659)) }
            }
        },
        dismissButton = {
            if (state is PlaylistImportUiState.Preview) TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.feature_library_cancel_4c5fa5))
            }
        },
    )
}

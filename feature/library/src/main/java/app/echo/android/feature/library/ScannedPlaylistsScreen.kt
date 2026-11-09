package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoPageBackHandler
import app.echo.android.model.library.EchoDiscoveredPlaylist
import app.echo.android.model.library.EchoPlaylistDiscoveryResult
import app.echo.android.model.library.PlaylistImportPreview

@Composable
fun ScannedPlaylistsScreen(
    result: EchoPlaylistDiscoveryResult?,
    searching: Boolean,
    waitingForScan: Boolean,
    importing: Boolean,
    searchError: Boolean,
    importError: Boolean,
    imported: Set<String>,
    canSearch: Boolean,
    customFolder: Boolean,
    folderName: String?,
    hasSavedFolders: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onChooseFile: () -> Unit,
    onUseSavedFolders: () -> Unit,
    onDismissImportError: () -> Unit,
    onImport: (EchoDiscoveredPlaylist) -> Unit,
) {
    var selectedKey by remember(result) { mutableStateOf<String?>(null) }
    val busy = searching || waitingForScan || importing
    val goBack = { if (!importing) onBack() }
    EchoPageBackHandler(onBack = goBack)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AddMusicPageHeader(
            stringResource(R.string.scan_playlist_import_title), stringResource(R.string.scan_rules_back), goBack,
        ) {
            if (canSearch) IconButton(enabled = !busy, onClick = onRefresh) {
                EchoIcon(Icons.Rounded.Refresh, stringResource(R.string.add_music_rescan))
            }
        }
        LazyColumn(
            Modifier.weight(1f).widthIn(max = AddMusicContentWidth).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("search-scope") {
                PlaylistSearchScope(
                    folderName, customFolder, hasSavedFolders, !importing,
                    onChooseFolder, onChooseFile, onUseSavedFolders,
                )
            }
            when {
                waitingForScan || searching || (canSearch && result == null && !searchError) -> item("searching") {
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(if (waitingForScan) R.string.scan_playlist_waiting else R.string.scan_playlist_searching),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                searchError -> item("error") {
                    LibraryCollectionEmpty(
                        title = stringResource(R.string.scanned_playlists_error),
                        actionLabel = stringResource(R.string.library_retry), onAction = onRefresh,
                    )
                }
                !canSearch -> Unit
                result?.items?.isEmpty() == true -> item("empty") {
                    Text(stringResource(R.string.scan_playlist_empty), Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result?.let { it.truncated || it.unavailableDirectories > 0 } == true) item("partial") {
                Text(stringResource(R.string.scanned_playlists_partial), Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(result?.items.orEmpty(), key = { it.selectionKey }) { candidate ->
                val importedHere = candidate.selectionKey in imported
                val summary = when {
                    importedHere -> stringResource(R.string.scanned_playlists_imported)
                    candidate.error -> stringResource(R.string.scanned_playlists_read_error)
                    else -> candidate.preview?.let { stringResource(R.string.scan_playlist_matched_count, it.trackIds.size) }
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    AddMusicActionRow(
                        Icons.AutoMirrored.Rounded.PlaylistPlay, candidate.name, summary,
                        enabled = !busy && !importedHere && candidate.preview != null,
                        value = if (candidate.alreadyImported && !importedHere) {
                            stringResource(R.string.scan_playlist_previously_imported)
                        } else null,
                        onClick = { selectedKey = candidate.selectionKey },
                    )
                }
            }
        }
    }
    val selected = result?.items?.firstOrNull { it.selectionKey == selectedKey }
    val preview = selected?.preview
    if (selected != null && preview != null) {
        if (selected.selectionKey in imported) {
            LaunchedEffect(selected.selectionKey) { selectedKey = null }
        } else {
            ScannedPlaylistReview(selected, preview, importing, importError, onDismiss = {
                selectedKey = null
                onDismissImportError()
            }) { onImport(selected) }
        }
    }
}

@Composable
private fun PlaylistSearchScope(
    folderName: String?,
    customFolder: Boolean,
    hasSavedFolders: Boolean,
    enabled: Boolean,
    onChooseFolder: () -> Unit,
    onChooseFile: () -> Unit,
    onUseSavedFolders: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val scopeLabel = when {
                customFolder -> folderName ?: stringResource(R.string.scan_playlist_selected_folder)
                hasSavedFolders -> stringResource(R.string.add_music_folders)
                else -> null
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = enabled, onClick = onChooseFile) {
                    Text(stringResource(R.string.add_music_import_playlist))
                }
                OutlinedButton(enabled = enabled, onClick = onChooseFolder) {
                    EchoIcon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (customFolder) R.string.scan_folder_change else R.string.scanned_playlists_choose))
                }
                if (customFolder && hasSavedFolders) TextButton(enabled = enabled, onClick = onUseSavedFolders) {
                    Text(stringResource(R.string.scan_playlist_use_saved_folders))
                }
            }
            if (scopeLabel != null) Text(stringResource(R.string.scan_playlist_scope, scopeLabel), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScannedPlaylistReview(
    candidate: EchoDiscoveredPlaylist,
    preview: PlaylistImportPreview,
    importing: Boolean,
    error: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(candidate.name) },
        text = {
            Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.scanned_playlists_counts, preview.trackIds.size, preview.unmatchedEntries,
                    preview.ambiguousEntries, preview.duplicateEntries), style = MaterialTheme.typography.titleSmall)
                val hint = when {
                    preview.trackIds.isEmpty() -> R.string.add_music_playlist_no_match
                    candidate.alreadyImported -> R.string.scanned_playlists_replace_warning
                    else -> R.string.scanned_playlists_confirm
                }
                Text(stringResource(hint))
                preview.unresolvedNames.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error) Text(stringResource(R.string.scanned_playlists_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            if (preview.trackIds.isNotEmpty()) Button(enabled = !importing, onClick = onImport) {
                Text(stringResource(if (candidate.alreadyImported) R.string.scanned_playlists_update else R.string.scanned_playlists_import))
            }
        },
        dismissButton = {
            TextButton(enabled = !importing, onClick = onDismiss) { Text(stringResource(R.string.media_tools_close)) }
        },
    )
}

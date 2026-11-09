package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibraryScanPhase

/** Presentation of an already authorized directory; I/O and permissions belong to the app host. */
data class AddMusicFolder(val uri: String, val path: String, val lastScanLabel: String?, val watch: Boolean)

@Composable
fun AddMusicScreen(
    initialOptions: LibraryScanOptions?,
    scanState: LibraryScanProgress,
    folders: List<AddMusicFolder>,
    foldersLoaded: Boolean,
    autoRescan: Boolean,
    actionBusy: Boolean,
    actionError: String?,
    onBack: () -> Unit,
    onScanFolder: () -> Unit,
    onScanAll: () -> Unit,
    onRescanFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onEditFolder: (String) -> Unit,
    onSaveOptions: (LibraryScanOptions, onSaved: () -> Unit) -> Unit,
    onCancelScan: () -> Unit,
    selectedExcludedFolder: String?,
    onChooseExcludedFolder: () -> Unit,
    onExcludedFolderConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    playlistDiscovery: @Composable (LibraryScanOptions, onBack: () -> Unit) -> Unit,
) {
    val options = initialOptions
    var folderToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showDiscovery by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val enabled = (!scanState.isScanning || scanState.isAutomatic) && !actionBusy
    if (showRules && options != null) {
        LibraryScanRulesScreen(
            initialOptions = options,
            enabled = enabled,
            saving = actionBusy,
            saveError = actionError,
            selectedExcludedFolder = selectedExcludedFolder,
            onChooseExcludedFolder = onChooseExcludedFolder,
            onExcludedFolderConsumed = onExcludedFolderConsumed,
            onBack = { showRules = false },
            onSave = { updated -> onSaveOptions(updated) { showRules = false } },
            modifier = modifier,
        )
        return
    }
    if (showDiscovery && options != null) {
        playlistDiscovery(options) { showDiscovery = false }
        return
    }
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AddMusicPageHeader(stringResource(R.string.library_add_music), stringResource(R.string.add_music_back), onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).widthIn(max = AddMusicContentWidth).fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (scanState.phase != LibraryScanPhase.Idle && !scanState.isAutomatic) item("scan-progress") {
                AddMusicScanResult(scanState, onCancelScan, onRules = { showRules = true })
            }
            item("add-folder") {
                AddMusicFolderAction(enabled && foldersLoaded, onScanFolder)
            }
            actionError?.let { error -> item("action-error") {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp))
            } }
            item("rules") {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    AddMusicActionRow(Icons.Rounded.Tune, stringResource(R.string.add_music_rules),
                        hint = scanRulesEntrySummary(options),
                        enabled = enabled && options != null) { showRules = true }
                }
            }
            item("folder-heading") {
                AddMusicSectionTitle(stringResource(R.string.add_music_folders), count = folders.size.takeIf { foldersLoaded && it > 0 })
            }
            if (!foldersLoaded) item("folders-loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (folders.isEmpty()) item("folders-empty") {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(stringResource(R.string.add_music_no_folders), Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(folders, key = { it.uri }) { folder ->
                AddMusicFolderRow(folder, enabled, autoRescan && folder.watch,
                    onEdit = { onEditFolder(folder.uri) },
                    onRescan = { onRescanFolder(folder.uri) },
                    onRemove = { folderToRemove = folder.uri })
            }
            item("other-imports") {
                AddMusicSectionTitle(stringResource(R.string.scan_folder_other_imports))
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        AddMusicActionRow(Icons.Rounded.LibraryMusic, stringResource(R.string.add_music_scan_device),
                            stringResource(R.string.add_music_device_hint), enabled, onClick = onScanAll)
                        HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                        AddMusicActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.scan_playlist_import_title),
                            null, enabled && foldersLoaded && options != null) { showDiscovery = true }
                    }
                }
            }
        }
    }
    folderToRemove?.let { uri ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text(stringResource(R.string.add_music_remove_folder)) },
            text = { Text(stringResource(R.string.add_music_remove_hint)) },
            confirmButton = { TextButton(enabled = enabled, onClick = {
                folderToRemove = null
                onRemoveFolder(uri)
            }) { Text(stringResource(R.string.add_music_remove)) } },
            dismissButton = { TextButton(onClick = { folderToRemove = null }) {
                Text(stringResource(R.string.feature_library_cancel_4c5fa5))
            } },
        )
    }
}

@Composable
private fun scanRulesEntrySummary(options: LibraryScanOptions?): String {
    val blocked = options?.blockedExtensions?.size ?: 0
    val ignored = options?.excludedRelativePaths?.size ?: 0
    val formatLabel = if (blocked > 0) stringResource(R.string.scan_rules_blocked_count, blocked) else null
    val folderLabel = if (ignored > 0) stringResource(R.string.scan_rules_ignored_count, ignored) else null
    return listOfNotNull(formatLabel, folderLabel).joinToString(" · ")
        .ifEmpty { stringResource(R.string.scan_rules_summary) }
}

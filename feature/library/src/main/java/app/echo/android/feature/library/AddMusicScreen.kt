package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibraryScanPhase

/** Presentation of an already authorized directory; I/O and permissions belong to the app host. */
data class AddMusicFolder(val uri: String, val path: String, val lastScanLabel: String?)

@Composable
fun AddMusicScreen(
    initialOptions: LibraryScanOptions,
    scanState: LibraryScanProgress,
    folders: List<AddMusicFolder>,
    foldersLoaded: Boolean,
    autoRescan: Boolean,
    actionBusy: Boolean,
    actionError: String?,
    onBack: () -> Unit,
    onScanFolder: (LibraryScanOptions) -> Unit,
    onScanAll: (LibraryScanOptions) -> Unit,
    onRescanFolder: (String, LibraryScanOptions) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onAutoRescanChange: (Boolean) -> Unit,
    onSaveOptions: (LibraryScanOptions) -> Unit,
    onImportPlaylist: () -> Unit,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var options by rememberSaveable { mutableStateOf(initialOptions) }
    var folderToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    val enabled = !scanState.isScanning && !actionBusy
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.add_music_back))
            }
            Text(stringResource(R.string.library_add_music), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            modifier = Modifier.weight(1f).imePadding().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (scanState.phase != LibraryScanPhase.Idle) item("scan-progress") {
                AddMusicScanResult(scanState, onCancelScan, onBack)
            }
            actionError?.let { error -> item("action-error") {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            } }
            item("sources") {
                AddMusicActionRow(Icons.Rounded.FolderOpen, stringResource(R.string.scan_choose_folder),
                    stringResource(R.string.add_music_folder_hint), enabled) { onScanFolder(options) }
                AddMusicActionRow(Icons.Rounded.LibraryMusic, stringResource(R.string.add_music_scan_device),
                    stringResource(R.string.add_music_device_hint), enabled) { onScanAll(options) }
                AddMusicActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_music_import_playlist),
                    stringResource(R.string.add_music_playlist_hint), enabled, onClick = onImportPlaylist)
            }
            item("rules") {
                HorizontalDivider()
                AddMusicActionRow(Icons.Rounded.Tune, stringResource(R.string.add_music_rules),
                    hint = null, enabled = enabled, value = libraryScanPresetLabel(options)) { showRules = true }
            }
            item("folder-heading") {
                HorizontalDivider()
                AddMusicSectionTitle(stringResource(R.string.add_music_folders))
                if (folders.isNotEmpty()) AddMusicToggle(stringResource(R.string.add_music_auto_scan),
                    null, autoRescan, !actionBusy, onAutoRescanChange)
            }
            if (!foldersLoaded) item("folders-loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (folders.isEmpty()) item("folders-empty") {
                Text(stringResource(R.string.add_music_no_folders), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(folders, key = { it.uri }) { folder ->
                AddMusicFolderRow(folder, enabled,
                    onRescan = { onRescanFolder(folder.uri, options) },
                    onRemove = { folderToRemove = folder.uri })
            }
        }
    }
    if (showRules) LibraryScanRulesDialog(
        initialOptions = options,
        enabled = enabled,
        onDismiss = { showRules = false },
        onSave = { updated ->
            options = updated
            showRules = false
            onSaveOptions(updated)
        },
    )
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

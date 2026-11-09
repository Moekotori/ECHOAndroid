package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIconPlate
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.model.library.LibraryFolderScanRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFolderScanDialog(
    path: String,
    initialRules: LibraryFolderScanRules?,
    autoRescan: Boolean,
    enabled: Boolean,
    error: String?,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (LibraryFolderScanRules) -> Unit,
) {
    var draft by rememberSaveable(initialRules) {
        mutableStateOf(initialRules ?: LibraryFolderScanRules(importITunes = true, importOtherPlaylists = true))
    }
    val adding = initialRules == null
    val height = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    EchoModalSheet(
        onDismissRequest = { if (enabled) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = enabled,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = height).imePadding()) {
            EchoSheetHeader(
                title = stringResource(if (adding) R.string.scan_folder_add_title else R.string.scan_folder_edit_title),
            )
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        EchoIconPlate(Icons.Rounded.FolderOpen)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val name = path.substringAfterLast('/')
                            Text(name, style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (name != path) Text(path, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (adding) TextButton(enabled = enabled, onClick = onChooseFolder) {
                            Text(stringResource(R.string.scan_folder_change))
                        }
                    }
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(vertical = 2.dp)) {
                        val inset = Modifier.padding(horizontal = 14.dp)
                        val watchHint = if (autoRescan) R.string.scan_folder_watch_hint else R.string.scan_folder_watch_paused
                        AddMusicToggle(stringResource(R.string.scan_folder_watch), stringResource(watchHint),
                            draft.watch, enabled, inset) { draft = draft.copy(watch = it) }
                        HorizontalDivider(inset)
                        AddMusicToggle(stringResource(R.string.scan_folder_itunes), stringResource(R.string.scan_folder_itunes_hint),
                            draft.importITunes, enabled, inset) { draft = draft.copy(importITunes = it) }
                        HorizontalDivider(inset)
                        AddMusicToggle(stringResource(R.string.scan_folder_other_playlists),
                            stringResource(R.string.scan_folder_other_playlists_hint),
                            draft.importOtherPlaylists, enabled, inset) { draft = draft.copy(importOtherPlaylists = it) }
                    }
                }
                OutlinedTextField(
                    value = draft.ignoredPaths,
                    onValueChange = { draft = draft.copy(ignoredPaths = it.take(LibraryFolderScanRules.MaxIgnoredPathsLength)) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.scan_folder_ignored_paths)) },
                    placeholder = { Text("tmp; @eaDir; Extras/Demos") },
                    supportingText = { Text(stringResource(R.string.scan_folder_ignored_paths_hint)) },
                    maxLines = 3,
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(enabled = enabled, onClick = onDismiss) {
                    Text(stringResource(R.string.feature_library_cancel_4c5fa5))
                }
                Button(enabled = enabled, onClick = { onSave(draft) }) {
                    Text(stringResource(R.string.scan_folder_save_and_scan))
                }
            }
        }
    }
}

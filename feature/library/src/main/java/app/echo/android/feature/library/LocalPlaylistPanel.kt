package app.echo.android.feature.library

import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import app.echo.android.design.echoClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import app.echo.android.design.EchoIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoTextButton
import app.echo.android.design.EmptyState
import app.echo.android.design.echoTheme
import app.echo.android.model.library.EchoPlaylist

@Composable
internal fun LocalPlaylistPanel(
    playlists: List<EchoPlaylist>,
    query: String,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onPlayPlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    experienceActions: LibraryExperienceActions? = null,
    remoteManagement: RemoteLibraryWritebackActions? = null,
    onRenamePlaylist: (EchoPlaylist, String) -> Unit,
    onDeletePlaylist: (EchoPlaylist) -> Unit,
    onImportM3uPlaylist: () -> Unit = {},
    onExportM3uPlaylist: (EchoPlaylist) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var remoteVisible by remember { mutableStateOf(false) }
    var createVisible by remember { mutableStateOf(false) }
    var smartEditorVisible by remember { mutableStateOf(false) }
    var editingSmart by remember { mutableStateOf<EchoPlaylist?>(null) }
    val toolsScope = rememberCoroutineScope()
    var toolsError by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<EchoPlaylist?>(null) }
    var deleting by remember { mutableStateOf<EchoPlaylist?>(null) }
    var sortName by rememberSaveable { mutableStateOf(PlaylistCatalogSort.Updated.name) }
    val sort = PlaylistCatalogSort.entries.firstOrNull { it.name == sortName } ?: PlaylistCatalogSort.Updated
    val sections by rememberPlaylistCatalog(playlists, query, sort)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        item {
            LocalPlaylistHeader(
                playlistCount = playlists.size,
                sort = sort,
                onSort = { sortName = it.name },
                onCreatePlaylist = { createVisible = true },
                onImportM3uPlaylist = onImportM3uPlaylist,
                onCreateSmartPlaylist = experienceActions?.let { { editingSmart = null; smartEditorVisible = true } },
            )
            if (remoteManagement != null) TextButton(onClick = { remoteVisible = true }) {
                Text(stringResource(L10nR.string.nav_manage))
            }
            if (toolsError) Text(stringResource(L10nR.string.smart_pin_error), color = MaterialTheme.colorScheme.error)
        }
        if (sections.isEmpty() && query.isNotBlank()) {
            item {
                EmptyState(stringResource(L10nR.string.playlist_no_search_results))
            }
        } else {
            sections.forEach { section ->
            section.label?.let { label -> item(key = "section-${section.key}", contentType = "section") {
                Text(stringResource(label), modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if (section.playlists.isEmpty()) item(key = "empty-${section.key}") {
                EmptyState(stringResource(L10nR.string.feature_library_no_local_playlists_yet_create_one_from_the_e13a98))
            }
            items(
                items = section.playlists,
                key = { it.id },
                contentType = { "playlist" },
            ) { playlist ->
                val exportName = playlistDisplayName(playlist)
                LocalPlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpenPlaylist(playlist) },
                    onPlay = { onPlayPlaylist(playlist) },
                    onRename = { if (playlist.isCustomSmartPlaylist) { editingSmart = playlist; smartEditorVisible = true }
                        else if (playlist.canEdit) renaming = playlist },
                    onDelete = { if (playlist.canEdit || playlist.isCustomSmartPlaylist) deleting = playlist },
                    onExportM3u = { onExportM3uPlaylist(playlist.copy(name = exportName)) },
                    onPinToHome = { experienceActions?.let { actions -> toolsScope.launch {
                        try { toolsError = !actions.pin(playlist.id, !playlist.pinnedToHome) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { toolsError = true }
                    } } },
                )
            }
            }
        }
    }

    if (remoteVisible && remoteManagement != null) NavidromeLibraryManager(remoteManagement) { remoteVisible = false }
    if (smartEditorVisible && experienceActions != null) SmartPlaylistEditor(editingSmart, experienceActions) { smartEditorVisible = false }
    if (createVisible) {
        PlaylistNameDialog(
            title = stringResource(L10nR.string.feature_library_new_playlist_22cdbd),
            confirmLabel = stringResource(L10nR.string.feature_library_create_7b4bc9),
            initialName = "",
            onDismiss = { createVisible = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                createVisible = false
            },
        )
    }
    renaming?.let { playlist ->
        PlaylistNameDialog(
            title = stringResource(L10nR.string.feature_library_rename_playlist_757bb7),
            confirmLabel = stringResource(L10nR.string.feature_library_save_68ae20),
            initialName = playlist.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onRenamePlaylist(playlist, name)
                renaming = null
            },
        )
    }
    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(L10nR.string.feature_library_delete_playlist_4d9753)) },
            text = {
                Text(
                    stringResource(L10nR.string.feature_library_delete_playlist_name_songs_in_the_library_will_3541db, (playlist.name).toString()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist(playlist)
                        deleting = null
                    },
                ) {
                    Text(stringResource(L10nR.string.feature_library_delete_138ccf))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(L10nR.string.feature_library_cancel_4c5fa5))
                }
            },
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<EchoPlaylist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    busy: Boolean = false,
    error: String? = null,
    awaitResult: Boolean = false,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    val editablePlaylists = remember(playlists) { playlists.filter { it.canEdit || it.isLikedSongs } }
    if (creating) {
        PlaylistNameDialog(
            title = stringResource(L10nR.string.feature_library_new_playlist_and_add_314ff2),
            confirmLabel = stringResource(L10nR.string.feature_library_create_7b4bc9),
            initialName = draftName,
            onNameChange = { draftName = it },
            busy = busy,
            error = error,
            onDismiss = { if (busy) onDismiss() else creating = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                if (!awaitResult) creating = false
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(L10nR.string.feature_library_add_to_playlist_0f8338)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "create") {
                        TextButton(onClick = { creating = true }, enabled = !busy) {
                            Text(stringResource(L10nR.string.feature_library_new_playlist_22cdbd))
                        }
                    }
                    if (editablePlaylists.isEmpty()) item(key = "empty") {
                        Text(
                            stringResource(L10nR.string.feature_library_no_playlists_yet_create_one_first_6af586),
                        )
                    }
                    items(editablePlaylists, key = { "playlist:${it.id}" }) { playlist ->
                        Text(
                            stringResource(L10nR.string.feature_library_playlistdisplayname_playlist_playlist_trackcount_tracks_2afcb8, (playlistDisplayName(playlist)).toString(), (playlist.trackCount).toString()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .echoClickable(enabled = !busy) { onSelectPlaylist(playlist) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(L10nR.string.feature_library_cancel_4c5fa5))
            }
        },
    )
}

@Composable
internal fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    supportingText: String? = null,
    busy: Boolean = false,
    error: String? = null,
    onNameChange: (String) -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val canConfirm = name.trim().isNotEmpty() && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(
                value = name,
                enabled = !busy,
                onValueChange = { name = it.take(80); onNameChange(name) },
                singleLine = true,
                label = { Text(stringResource(L10nR.string.feature_library_name_57335e)) },
            )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { if (canConfirm) onConfirm(name.trim()) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(L10nR.string.feature_library_cancel_4c5fa5))
            }
        },
    )
}

@Composable
private fun LocalPlaylistHeader(
    playlistCount: Int,
    sort: PlaylistCatalogSort,
    onSort: (PlaylistCatalogSort) -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportM3uPlaylist: () -> Unit,
    onCreateSmartPlaylist: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(L10nR.string.feature_library_playlistcount_playlists_220ceb, playlistCount.toString()),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlaylistCatalogSortControl(sort, onSort)
        Box {
            androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                EchoIcon(Icons.Rounded.Add, contentDescription = stringResource(L10nR.string.feature_library_new_playlist_22cdbd))
            }
            app.echo.android.design.EchoDropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(L10nR.string.feature_library_new_playlist_22cdbd)) },
                    onClick = { menuOpen = false; onCreatePlaylist() },
                )
                onCreateSmartPlaylist?.let { createSmart ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(L10nR.string.smart_create)) },
                        onClick = { menuOpen = false; createSmart() },
                    )
                }
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(L10nR.string.playlist_import)) },
                    onClick = { menuOpen = false; onImportM3uPlaylist() },
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistRow(
    playlist: EchoPlaylist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExportM3u: () -> Unit,
    onPinToHome: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().echoClickable(onClick = onOpen).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtworkTile(playlist.artworkUri, Modifier.size(56.dp), accent = rememberLibraryArtworkAccent(), cornerRadius = 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(playlistDisplayName(playlist), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playlistCaption(playlist), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.IconButton(onClick = onPlay) {
            EchoIcon(Icons.Rounded.PlayArrow, contentDescription = stringResource(L10nR.string.feature_library_play_38419a))
        }
        Box {
            androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                EchoIcon(Icons.Rounded.MoreVert, contentDescription = stringResource(L10nR.string.library_playlist_actions))
            }
            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (playlist.isCustomSmartPlaylist) androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(if (playlist.pinnedToHome) L10nR.string.smart_unpin_home else L10nR.string.smart_pin_home)) },
                    onClick = { menuOpen = false; onPinToHome() })
                if (playlist.canEdit || playlist.isCustomSmartPlaylist) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(if (playlist.isCustomSmartPlaylist) L10nR.string.smart_edit_rules else L10nR.string.feature_library_rename_playlist_757bb7)) },
                        onClick = { menuOpen = false; onRename() })
                }
                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(L10nR.string.feature_library_export_m3u_6f18a4)) },
                    onClick = { menuOpen = false; onExportM3u() })
                if (playlist.canEdit || playlist.isCustomSmartPlaylist) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(L10nR.string.feature_library_delete_playlist_4d9753)) },
                        onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun IconButtonLite(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val accent = rememberLibraryControlColor()
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .echoClickable(onClick = onClick),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, echoTheme().glassBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

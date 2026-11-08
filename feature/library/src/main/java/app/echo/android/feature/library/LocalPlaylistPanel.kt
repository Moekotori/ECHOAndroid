package app.echo.android.feature.library

import app.echo.android.design.echoSharedCover
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalResources
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
    onShufflePlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    experienceActions: LibraryExperienceActions? = null,
    remoteManagement: RemoteLibraryWritebackActions? = null,
    management: PlaylistManagementActions? = null,
    onRenamePlaylist: (EchoPlaylist, String) -> Unit,
    onDeletePlaylist: (EchoPlaylist) -> Unit,
    onImportM3uPlaylist: () -> Unit = {},
    onExportM3uPlaylist: (EchoPlaylist) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var remoteVisible by remember { mutableStateOf(false) }
    var createVisible by remember { mutableStateOf(false) }
    var fileCreateVisible by remember { mutableStateOf(false) }
    var smartEditorVisible by remember { mutableStateOf(false) }
    var editingSmart by remember { mutableStateOf<EchoPlaylist?>(null) }
    val toolsScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val actionError = stringResource(L10nR.string.playlist_action_error)
    val pinError = stringResource(L10nR.string.smart_pin_error)
    val resources = LocalResources.current
    var menuPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val menuPlaylist = remember(playlists, menuPlaylistId) { playlists.firstOrNull { it.id == menuPlaylistId } }
    var adding by remember { mutableStateOf<EchoPlaylist?>(null) }
    var copying by remember { mutableStateOf<EchoPlaylist?>(null) }
    var copyBusy by remember { mutableStateOf(false) }
    var copyFailed by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<EchoPlaylist?>(null) }
    var deleting by remember { mutableStateOf<EchoPlaylist?>(null) }
    var sortName by rememberSaveable { mutableStateOf(PlaylistCatalogSort.Updated.name) }
    val sort = PlaylistCatalogSort.entries.firstOrNull { it.name == sortName } ?: PlaylistCatalogSort.Updated
    val sections by rememberPlaylistCatalog(playlists, query, sort)

    if (fileCreateVisible) FilePlaylistCreateDialog(onCreate = { name, folder ->
        fileCreateVisible = false; management?.createFilePlaylist?.invoke(name, folder)
    }, onDismiss = { fileCreateVisible = false })
    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
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
                if (management?.createFilePlaylist != null) TextButton(onClick = { fileCreateVisible = true }) { Text(stringResource(L10nR.string.file_playlist_create)) }
            }
            if (sections.isEmpty() && query.isNotBlank()) {
                item { EmptyState(stringResource(L10nR.string.playlist_no_search_results)) }
            } else {
                sections.forEach { section ->
                    section.label?.let { label -> item(key = "section-${section.key}", contentType = "section") {
                        Text(stringResource(label), modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    if (section.playlists.isEmpty()) item(key = "empty-${section.key}") {
                        EmptyState(stringResource(L10nR.string.feature_library_no_local_playlists_yet_create_one_from_the_e13a98))
                    }
                    items(items = section.playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                        LocalPlaylistRow(
                            playlist = playlist,
                            onOpen = { onOpenPlaylist(playlist) },
                            onPlay = { onPlayPlaylist(playlist) },
                            onMore = { menuPlaylistId = playlist.id },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LibraryBottomControlsPadding))
    }

    menuPlaylist?.let { playlist ->
        val exportName = playlistDisplayName(playlist)
        fun dismissThen(action: () -> Unit): () -> Unit = { menuPlaylistId = null; action() }
        PlaylistActionsSheet(
            playlist = playlist,
            onDismiss = { menuPlaylistId = null },
            onOpen = dismissThen { onOpenPlaylist(playlist) },
            onPlay = dismissThen { onPlayPlaylist(playlist) },
            onShuffle = dismissThen { onShufflePlaylist(playlist) },
            onExport = dismissThen { onExportM3uPlaylist(playlist.copy(name = exportName)) },
            onAdd = management?.takeIf { playlist.canEdit }?.let { dismissThen { adding = playlist } },
            onCopy = management?.let { dismissThen { copyFailed = false; copying = playlist } },
            onArtwork = management?.takeIf { playlist.canEdit }?.let { actions -> dismissThen { actions.pickArtwork(playlist.id) } },
            onRename = if (playlist.canEdit) dismissThen { renaming = playlist } else null,
            onEditRules = experienceActions?.takeIf { playlist.isCustomSmartPlaylist }?.let {
                dismissThen { editingSmart = playlist; smartEditorVisible = true }
            },
            onPin = experienceActions?.takeIf { playlist.isCustomSmartPlaylist }?.let { actions -> dismissThen {
                toolsScope.launch {
                    try { if (!actions.pin(playlist.id, !playlist.pinnedToHome)) snackbar.showSnackbar(pinError) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { snackbar.showSnackbar(pinError) }
                }
            } },
            onDelete = if (playlist.canEdit || playlist.isCustomSmartPlaylist) dismissThen { deleting = playlist } else null,
        )
    }
    adding?.let { playlist ->
        if (management != null) PlaylistAddTracksSheet(playlist.id, management,
            onAdded = { count -> toolsScope.launch {
                snackbar.showSnackbar(resources.getQuantityString(L10nR.plurals.playlist_added, count, count))
            } }, onDismiss = { adding = null })
    }
    copying?.let { playlist ->
        if (management != null) PlaylistNameDialog(
            title = stringResource(L10nR.string.playlist_copy),
            confirmLabel = stringResource(L10nR.string.feature_library_create_7b4bc9),
            initialName = stringResource(L10nR.string.playlist_copy_name, playlistDisplayName(playlist)).take(80),
            supportingText = stringResource(L10nR.string.playlist_copy_hint),
            busy = copyBusy,
            error = if (copyFailed) actionError else null,
            onDismiss = { if (!copyBusy) copying = null },
            onConfirm = { name ->
                copyBusy = true; copyFailed = false
                toolsScope.launch {
                    try {
                        val created = checkNotNull(management.copy(playlist.id, name))
                        copying = null
                        onOpenPlaylist(created)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { copyFailed = true }
                    finally { copyBusy = false }
                }
            },
        )
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
    onMore: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().echoClickable(onClick = onOpen).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtworkTile(playlist.artworkUri, Modifier.echoSharedCover("playlist", playlist.id).size(56.dp), accent = rememberLibraryArtworkAccent(), cornerRadius = 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(playlistDisplayName(playlist), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playlistCaption(playlist), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.IconButton(onClick = onPlay, enabled = playlist.trackCount > 0) {
            EchoIcon(Icons.Rounded.PlayArrow, contentDescription = stringResource(L10nR.string.feature_library_play_38419a))
        }
        androidx.compose.material3.IconButton(onClick = onMore) {
            EchoIcon(Icons.Rounded.MoreVert, contentDescription = stringResource(L10nR.string.library_playlist_actions))
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

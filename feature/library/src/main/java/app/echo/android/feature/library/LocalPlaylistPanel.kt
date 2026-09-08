package app.echo.android.feature.library

import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import app.echo.android.design.echoClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoTextButton
import app.echo.android.design.EmptyState
import app.echo.android.model.library.EchoPlaylist

@Composable
internal fun LocalPlaylistPanel(
    playlists: List<EchoPlaylist>,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onPlayPlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (EchoPlaylist, String) -> Unit,
    onDeletePlaylist: (EchoPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var createVisible by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<EchoPlaylist?>(null) }
    var deleting by remember { mutableStateOf<EchoPlaylist?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        item {
            LocalPlaylistHeader(
                playlistCount = playlists.size,
                onCreatePlaylist = { createVisible = true },
            )
        }
        if (playlists.isEmpty()) {
            item {
                EmptyState(
                    stringResource(L10nR.string.feature_library_no_local_playlists_yet_create_one_from_the_e13a98),
                )
            }
        } else {
            items(
                items = playlists,
                key = { it.id },
            ) { playlist ->
                LocalPlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpenPlaylist(playlist) },
                    onPlay = { onPlayPlaylist(playlist) },
                    onRename = { if (playlist.canEdit) renaming = playlist },
                    onDelete = { if (playlist.canEdit) deleting = playlist },
                )
            }
        }
    }

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
internal fun AddToPlaylistDialog(
    playlists: List<EchoPlaylist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        PlaylistNameDialog(
            title = stringResource(L10nR.string.feature_library_new_playlist_and_add_314ff2),
            confirmLabel = stringResource(L10nR.string.feature_library_create_7b4bc9),
            initialName = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                creating = false
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(L10nR.string.feature_library_add_to_playlist_0f8338)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { creating = true }) {
                    Text(stringResource(L10nR.string.feature_library_new_playlist_22cdbd))
                }
                if (playlists.isEmpty()) {
                    Text(
                        stringResource(L10nR.string.feature_library_no_playlists_yet_create_one_first_6af586),
                    )
                } else {
                    playlists.filter { it.canEdit || it.isLikedSongs }.forEach { playlist ->
                        Text(
                            stringResource(L10nR.string.feature_library_playlistdisplayname_playlist_playlist_trackcount_tracks_2afcb8, (playlistDisplayName(playlist)).toString(), (playlist.trackCount).toString()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .echoClickable { onSelectPlaylist(playlist) }
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
) {
    var name by remember { mutableStateOf(initialName) }
    val canConfirm = name.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                label = { Text(stringResource(L10nR.string.feature_library_name_57335e)) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(name) },
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
    onCreatePlaylist: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(L10nR.string.feature_library_playlistcount_playlists_220ceb, playlistCount.toString()),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onCreatePlaylist) {
            Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(L10nR.string.feature_library_new_playlist_22cdbd))
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
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(L10nR.string.feature_library_play_38419a))
        }
        if (playlist.canEdit) Box {
            androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(L10nR.string.library_playlist_actions))
            }
            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(L10nR.string.feature_library_rename_playlist_757bb7)) },
                    onClick = { menuOpen = false; onRename() })
                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(L10nR.string.feature_library_delete_playlist_4d9753)) },
                    onClick = { menuOpen = false; onDelete() })
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
        border = BorderStroke(1.dp, EchoGlassBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

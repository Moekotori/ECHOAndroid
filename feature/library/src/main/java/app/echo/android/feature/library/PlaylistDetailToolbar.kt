package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoDropdownMenuDivider
import app.echo.android.model.library.*

@Composable
internal fun PlaylistDetailToolbar(
    playlist: EchoPlaylist,
    order: PlaylistTrackOrder,
    editing: Boolean,
    busy: Boolean,
    onSort: ((PlaylistTrackOrder) -> Unit)?,
    onEdit: (() -> Unit)?,
    onAdd: (() -> Unit)?,
) {
    val colors = rememberPlaylistDetailColors()
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.feature_library_tracks_2d80e8), color = colors.muted,
                style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            if (onSort != null) Box {
                TextButton(onClick = { menu = true }, enabled = !busy) {
                    EchoIcon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(17.dp), tint = colors.content)
                    Spacer(Modifier.width(5.dp))
                    Text(playlistSortLabel(order.sort, playlist.canEdit), color = colors.content,
                        style = MaterialTheme.typography.labelMedium)
                }
                EchoDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PlaylistTrackSort.entries.forEach { sort ->
                        EchoDropdownMenuItem(text = { Text(playlistSortLabel(sort, playlist.canEdit)) },
                            selected = sort == order.sort,
                            onClick = { menu = false; onSort(PlaylistTrackOrder(sort, sort == PlaylistTrackSort.RecentlyAdded)) })
                    }
                    if (!order.isManual) {
                        EchoDropdownMenuDivider()
                        listOf(false, true).forEach { descending ->
                            EchoDropdownMenuItem(text = { Text(stringResource(if (descending) R.string.playlist_sort_descending else R.string.playlist_sort_ascending)) },
                                selected = descending == order.descending,
                                onClick = { menu = false; onSort(order.copy(descending = descending)) })
                        }
                    }
                }
            }
            if (onEdit != null) TextButton(onClick = onEdit, enabled = !busy) {
                Text(stringResource(if (editing) R.string.feature_library_done_a5e659 else R.string.playlist_edit_tracks),
                    color = colors.content, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (editing) {
            if (onAdd != null) TextButton(onClick = onAdd, enabled = !busy) {
                Text(stringResource(R.string.playlist_add_tracks), color = colors.content)
            }
            if (playlist.canEdit && !order.isManual) Text(stringResource(R.string.playlist_manual_hint),
                color = colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun playlistSortLabel(sort: PlaylistTrackSort, editable: Boolean): String = stringResource(when (sort) {
    PlaylistTrackSort.Default -> if (editable) R.string.playlist_sort_manual else R.string.playlist_sort_default
    PlaylistTrackSort.Title -> R.string.artist_sort_title
    PlaylistTrackSort.Artist -> R.string.playlist_sort_artist
    PlaylistTrackSort.Album -> R.string.playlist_sort_album
    PlaylistTrackSort.Duration -> R.string.artist_sort_duration
    PlaylistTrackSort.RecentlyAdded -> R.string.smart_sort_added
})

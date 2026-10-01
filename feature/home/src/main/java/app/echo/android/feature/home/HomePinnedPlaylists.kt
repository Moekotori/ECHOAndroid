package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.model.library.EchoPlaylist

@Composable
internal fun HomePinnedPlaylists(playlists: List<EchoPlaylist>, onOpen: (EchoPlaylist) -> Unit, onPlay: (EchoPlaylist) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.home_pinned_playlists), Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleLarge)
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                Column(Modifier.width(164.dp).clickable { onOpen(playlist) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArtworkTile(playlist.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), accent = MaterialTheme.colorScheme.primary, cornerRadius = 4.dp)
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(pluralStringResource(R.plurals.home_pinned_track_count, playlist.trackCount, playlist.trackCount),
                            Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { onPlay(playlist) }, enabled = playlist.trackCount > 0) {
                            EchoIcon(Icons.Rounded.PlayArrow, stringResource(R.string.home_play_pinned))
                        }
                    }
                }
            }
        }
    }
}

package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.echoClickable
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary

@Composable
internal fun ArtistProfileHeader(artist: ArtistSummary, palette: ArtworkPalette) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (artist.artworkUri.isNullOrBlank()) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ArtworkTile(artworkUri = artist.artworkUri, modifier = Modifier.size(100.dp).clip(CircleShape),
                accent = palette.vibrant, showSignal = false, cornerRadius = 50.dp, elevation = 0.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(displayMetadataOrUnknown(artist.name, unknownArtistLabel()), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            Text(pluralStringResource(R.plurals.artist_album_count, artist.albumCount, artist.albumCount) + " · " + artistTrackCountLabel(artist.trackCount),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (artist.durationMs > 0) {
                Spacer(Modifier.height(4.dp))
                val minutes = artist.durationMs / 60_000
                Text(if (minutes < 60) stringResource(R.string.artist_total_minutes, minutes)
                    else stringResource(R.string.artist_total_time, minutes / 60, minutes % 60),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ArtistPlaybackActions(enabled: Boolean, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onPlayAll, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.feature_library_play_all_55c80e))
        }
        FilledTonalButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.feature_library_shuffle_34e7ce))
        }
    }
}

@Composable
internal fun ArtistAlbums(albums: LazyPagingItems<AlbumSummary>, palette: ArtworkPalette, state: LazyListState, onOpen: (AlbumSummary) -> Unit) {
    Text(stringResource(R.string.artist_albums), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    when {
        albums.loadState.refresh is LoadState.Loading && albums.itemCount == 0 -> LinearProgressIndicator(Modifier.fillMaxWidth())
        albums.loadState.refresh is LoadState.Error -> ArtistRetry(albums::retry)
        albums.itemCount == 0 -> Text(stringResource(R.string.artist_no_albums), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(albums.itemCount, key = { albums.peek(it)?.albumKey ?: "album-placeholder-$it" }) { index ->
                val album = albums[index]
                if (album != null) {
                    Column(Modifier.width(128.dp).echoClickable { onOpen(album) }) {
                        ArtworkTile(artworkUri = album.artworkUri, modifier = Modifier.size(128.dp), accent = palette.vibrant,
                            showSignal = album.artworkUri == null, cornerRadius = 12.dp, elevation = 0.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(displayMetadataOrUnknown(album.title, unknownAlbumLabel()), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(listOfNotNull(album.year?.takeIf { it > 0 }?.toString(), artistTrackCountLabel(album.trackCount)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "album-paging") {
                when (albums.loadState.append) {
                    is LoadState.Error -> ArtistRetry(albums::retry)
                    is LoadState.Loading -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                    else -> Unit
                }
            }
        }
    }
}

@Composable
internal fun ArtistRetry(onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = 12.dp, horizontal = 20.dp)) {
        Text(stringResource(R.string.artist_load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.artist_retry)) }
    }
}

@Composable
internal fun ArtistTracksHeading(count: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.artist_tracks), color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(artistTrackCountLabel(count), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun artistTrackCountLabel(count: Int): String =
    pluralStringResource(R.plurals.artist_track_count, count, count)

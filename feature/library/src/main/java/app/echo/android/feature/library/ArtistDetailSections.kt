package app.echo.android.feature.library

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
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
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.echoClickable
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary

@Composable
internal fun ArtistProfileHeader(artist: ArtistSummary) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EchoArtworkImage(
                artworkUri = artist.artworkUri,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                sizeClass = EchoArtworkSize.Card,
                placeholder = {
                    Box(Modifier.fillMaxSize().background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        EchoIcon(Icons.Rounded.Person, null, Modifier.size(36.dp), tint = colors.onSurfaceVariant)
                    }
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    displayMetadataOrUnknown(artist.name, unknownArtistLabel()),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.artist_album_count, artist.albumCount, artist.albumCount) + " · " + artistTrackCountLabel(artist.trackCount),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                if (artist.durationMs > 0) {
                    val minutes = artist.durationMs / 60_000
                    Text(
                        if (minutes < 60) stringResource(R.string.artist_total_minutes, minutes)
                        else stringResource(R.string.artist_total_time, minutes / 60, minutes % 60),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .45f))
    }
}

@Composable
internal fun ArtistAlbums(albums: LazyPagingItems<AlbumSummary>, albumCount: Int, palette: ArtworkPalette, state: LazyListState, onOpen: (AlbumSummary) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.artist_albums), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(pluralStringResource(R.plurals.artist_album_count, albumCount, albumCount),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(14.dp))
    when {
        albums.loadState.refresh is LoadState.Loading && albums.itemCount == 0 -> LinearProgressIndicator(Modifier.fillMaxWidth())
        albums.loadState.refresh is LoadState.Error -> ArtistRetry(albums::retry)
        albums.itemCount == 0 -> Text(stringResource(R.string.artist_no_albums), color = MaterialTheme.colorScheme.onSurfaceVariant)
        albums.itemCount == 1 && albums.loadState.append.endOfPaginationReached -> {
            albums[0]?.let { album ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .echoClickable { onOpen(album) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ArtworkTile(artworkUri = album.artworkUri, modifier = Modifier.size(80.dp), accent = palette.vibrant,
                        showSignal = album.artworkUri == null, cornerRadius = 10.dp, elevation = 0.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(displayMetadataOrUnknown(album.title, unknownAlbumLabel()), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        Text(listOfNotNull(album.year?.takeIf { it > 0 }?.toString(), artistTrackCountLabel(album.trackCount)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        else -> LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(albums.itemCount, key = { albums.peek(it)?.albumKey ?: "album-placeholder-$it" }) { index ->
                val album = albums[index]
                if (album != null) {
                    Column(Modifier.width(144.dp).clip(RoundedCornerShape(12.dp)).echoClickable { onOpen(album) }.padding(bottom = 4.dp)) {
                        ArtworkTile(artworkUri = album.artworkUri, modifier = Modifier.size(144.dp), accent = palette.vibrant,
                            showSignal = album.artworkUri == null, cornerRadius = 12.dp, elevation = 0.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(displayMetadataOrUnknown(album.title, unknownAlbumLabel()), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(artistTrackCountLabel(count), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun artistTrackCountLabel(count: Int): String =
    pluralStringResource(R.plurals.artist_track_count, count, count)

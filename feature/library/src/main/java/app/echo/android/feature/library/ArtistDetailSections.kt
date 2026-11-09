package app.echo.android.feature.library

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import app.echo.android.design.EchoContentMaxWidth
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
import app.echo.android.design.echoPageSection
import app.echo.android.design.echoSharedCover
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary

@Composable
internal fun ArtistHero(artist: ArtistSummary) {
    ArtistSectionItem {
        ArtistProfileHeader(artist)
    }
}

@Composable
private fun ArtistProfileHeader(artist: ArtistSummary) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        EchoArtworkImage(
            artworkUri = app.echo.android.design.artistArtwork(artist.name, artist.artworkUri),
            contentDescription = null,
            modifier = Modifier.echoSharedCover("artist", artist.artistKey)
                .size(96.dp).shadow(6.dp, CircleShape, clip = false),
            shape = CircleShape,
            sizeClass = EchoArtworkSize.Card,
            placeholder = {
                Box(Modifier.fillMaxSize().background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    EchoIcon(Icons.Rounded.Person, null, Modifier.size(40.dp), tint = colors.onSurfaceVariant)
                }
            },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                displayMetadataOrUnknown(artist.name, unknownArtistLabel()),
                modifier = Modifier.echoPageSection(0),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val minutesLabel = artist.durationMs.takeIf { it > 0 }?.let { ms ->
                val minutes = ms / 60_000
                if (minutes < 60) stringResource(R.string.artist_total_minutes, minutes)
                else stringResource(R.string.artist_total_time, minutes / 60, minutes % 60)
            }
            Text(
                listOf(
                    pluralStringResource(R.plurals.artist_album_count, artist.albumCount, artist.albumCount),
                    artistTrackCountLabel(artist.trackCount),
                ).joinToString(" · "),
                modifier = Modifier.echoPageSection(1),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (minutesLabel != null) {
                Text(minutesLabel, Modifier.echoPageSection(1), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

private const val CollapsedAlbumRows = 4

/**
 * The artist's albums as plain rows, one lazy item each. Four show at first; the rest sit behind a toggle so a
 * long discography does not push the tracks off screen.
 */
internal fun LazyListScope.artistAlbumsSection(
    albums: LazyPagingItems<AlbumSummary>,
    albumCount: Int,
    palette: ArtworkPalette,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpen: (AlbumSummary) -> Unit,
) {
    item(key = "albums-heading") {
        ArtistSectionItem {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.artist_albums), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(pluralStringResource(R.plurals.artist_album_count, albumCount, albumCount),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    when {
        albums.loadState.refresh is LoadState.Loading && albums.itemCount == 0 -> item(key = "albums-loading") {
            ArtistSectionItem { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        albums.loadState.refresh is LoadState.Error -> item(key = "albums-error") {
            ArtistSectionItem { ArtistRetry(albums::retry) }
        }
        albums.itemCount == 0 -> item(key = "albums-empty") {
            ArtistSectionItem { Text(stringResource(R.string.artist_no_albums), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        else -> {
            val shown = if (expanded) albums.itemCount else minOf(albums.itemCount, CollapsedAlbumRows)
            items(count = shown, key = { albums.peek(it)?.albumKey ?: "album-placeholder-$it" }) { index ->
                albums[index]?.let { album ->
                    ArtistSectionItem { ArtistAlbumRow(album, palette) { onOpen(album) } }
                }
            }
            if (expanded && albums.loadState.append is LoadState.Loading) item(key = "albums-paging") {
                ArtistSectionItem { CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp)) }
            }
            if (expanded && albums.loadState.append is LoadState.Error) item(key = "albums-paging-error") {
                ArtistSectionItem { ArtistRetry(albums::retry) }
            }
            if (albums.itemCount > CollapsedAlbumRows || !albums.loadState.append.endOfPaginationReached) item(key = "albums-toggle") {
                ArtistSectionItem {
                    TextButton(onClick = onToggleExpanded, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(stringResource(if (expanded) R.string.album_online_less else R.string.album_online_more))
                    }
                }
            }
        }
    }
}

/** Same centered, padded column the track rows use. */
@Composable
internal fun ArtistSectionItem(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = EchoContentMaxWidth)
            .padding(horizontal = 24.dp),
        content = content,
    )
}

@Composable
private fun ArtistAlbumRow(album: AlbumSummary, palette: ArtworkPalette, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .echoClickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier.size(58.dp),
            accent = palette.vibrant,
            showSignal = album.artworkUri == null,
            cornerRadius = 12.dp,
            elevation = 0.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                displayMetadataOrUnknown(album.title, unknownAlbumLabel()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(album.year?.takeIf { it > 0 }?.toString(), artistTrackCountLabel(album.trackCount)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(artistTrackCountLabel(count), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun artistTrackCountLabel(count: Int): String =
    pluralStringResource(R.plurals.artist_track_count, count, count)

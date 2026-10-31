package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.echoClickable
import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.flow.Flow

/** Row under the tracks that opens [ArtistAlbumsSheet]; tinted by the cover accent. */
@Composable
internal fun ArtistAlbumsEntry(artistName: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .echoClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.album_more_by_artist, artistName),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        EchoIcon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Every album by the artist in its own sheet; paging is only collected while the sheet is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistAlbumsSheet(
    artistName: String,
    albums: Flow<PagingData<AlbumSummary>>,
    accent: Color,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val paged = remember(albums) { albums }.collectAsLazyPagingItems()
    EchoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            displayMetadataOrUnknown(artistName, unknownArtistLabel()),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            paged.loadState.refresh is LoadState.Loading && paged.itemCount == 0 ->
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))
            paged.loadState.refresh is LoadState.Error -> Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.artist_load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = paged::retry) { Text(stringResource(R.string.artist_retry)) }
            }
            paged.itemCount == 0 -> Text(
                stringResource(R.string.artist_no_albums),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                items(
                    count = paged.itemCount,
                    key = { index -> paged.peek(index)?.albumKey ?: "album-placeholder-$index" },
                ) { index ->
                    paged[index]?.let { album -> ArtistAlbumRow(album, accent) { onOpenAlbum(album) } }
                }
                if (paged.loadState.append is LoadState.Loading) {
                    item(key = "album-paging") {
                        CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistAlbumRow(album: AlbumSummary, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .echoClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier.size(60.dp),
            accent = accent,
            showSignal = album.artworkUri == null,
            cornerRadius = 8.dp,
            elevation = 0.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                displayMetadataOrUnknown(album.title, unknownAlbumLabel()),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(album.year?.takeIf { it > 0 }?.toString(), libraryTrackCountLabel(album.trackCount))
                    .joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

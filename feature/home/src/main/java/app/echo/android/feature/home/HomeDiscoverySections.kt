package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.echoPressFeedback
import app.echo.android.model.library.AlbumSummary
import java.time.LocalDate

@Composable
internal fun HomeDailyAlbumSection(
    albums: List<AlbumSummary>,
    onPlay: (AlbumSummary) -> Unit,
    onOpen: (AlbumSummary) -> Unit,
) {
    val day = LocalDate.now().toEpochDay()
    var selectedKey by rememberSaveable(day) { mutableStateOf<String?>(null) }
    val album = albums.firstOrNull { it.albumKey == selectedKey }
        ?: albums.getOrNull(if (albums.isEmpty()) 0 else Math.floorMod(day, albums.size.toLong()).toInt())
        ?: return
    LaunchedEffect(album.albumKey) { selectedKey = album.albumKey }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeSectionHeader(stringResource(R.string.home_daily_album)) {
            TextButton(enabled = albums.size > 1, onClick = {
                val index = albums.indexOfFirst { it.albumKey == album.albumKey }
                selectedKey = albums[(index + 1) % albums.size].albumKey
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_daily_album_another), style = MaterialTheme.typography.labelLarge)
            }
        }
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = { onOpen(album) }, color = Color.Transparent, shape = RoundedCornerShape(4.dp),
            interactionSource = interaction,
            modifier = Modifier.echoPressFeedback(interaction),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ArtworkTile(album.artworkUri,
                    modifier = Modifier.weight(0.42f).aspectRatio(1f),
                    accent = MaterialTheme.colorScheme.primary, cornerRadius = 4.dp, elevation = 0.dp)
                Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(album.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(album.albumArtist ?: album.artist ?: stringResource(R.string.feature_home_unknown_artist_85ee30),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    FilledTonalButton(onClick = { onPlay(album) }, shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        EchoIcon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.home_daily_album_play), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeRediscoverySection(albums: List<AlbumSummary>, onOpen: (AlbumSummary) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HomeSectionHeader(stringResource(R.string.home_rediscover), Modifier.padding(horizontal = 24.dp))
        LazyRow(modifier = Modifier.homeCarouselScroll(), contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(albums, key = { it.albumKey }) { album ->
                RecommendedAlbumCard(album, onClick = { onOpen(album) })
            }
        }
    }
}

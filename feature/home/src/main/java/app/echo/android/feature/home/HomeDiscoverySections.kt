package app.echo.android.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoClickable
import app.echo.android.design.formatDuration
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import java.time.LocalDate

@Composable
internal fun HomeDailyAlbumSection(
    albums: List<AlbumSummary>,
    onPlay: (AlbumSummary) -> Unit,
    onOpen: (AlbumSummary) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val day = LocalDate.now().toEpochDay()
    var selectedKey by rememberSaveable(day) { mutableStateOf<String?>(null) }
    val album = albums.firstOrNull { it.albumKey == selectedKey }
        ?: albums.getOrNull(if (albums.isEmpty()) 0 else Math.floorMod(day, albums.size.toLong()).toInt())
    LaunchedEffect(album?.albumKey) { if (album != null) selectedKey = album.albumKey }
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    HomeDiscoverySection(R.string.home_daily_album, R.string.home_daily_album_detail) {
        if (album == null) {
            DiscoveryEmpty(R.string.home_daily_album_empty, onOpenLibrary)
        } else {
            Crossfade(album, animationSpec = tween(if (lightweight) 0 else 200), label = "daily-album") { shown ->
                Surface(
                    onClick = { onOpen(shown) },
                    shape = RoundedCornerShape(24.dp),
                    color = homePanelColor(),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ArtworkTile(
                            artworkUri = shown.artworkUri,
                            modifier = Modifier.widthIn(max = 144.dp).weight(0.44f).aspectRatio(1f),
                            accent = MaterialTheme.colorScheme.primary,
                            cornerRadius = 16.dp, elevation = 0.dp,
                        )
                        Column(Modifier.weight(0.56f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(shown.title, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(shown.albumArtist ?: shown.artist ?: stringResource(R.string.feature_home_unknown_artist_85ee30),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onPlay(album) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_daily_album_play))
                }
                TextButton(
                    enabled = albums.size > 1,
                    onClick = {
                        val index = albums.indexOfFirst { it.albumKey == album.albumKey }
                        selectedKey = albums[(index + 1) % albums.size].albumKey
                    },
                ) { Text(stringResource(R.string.home_daily_album_another)) }
            }
        }
    }
}

@Composable
internal fun HomeRediscoverySection(albums: List<AlbumSummary>, onOpen: (AlbumSummary) -> Unit, onOpenLibrary: () -> Unit) {
    HomeDiscoverySection(R.string.home_rediscover, R.string.home_rediscover_detail) {
        if (albums.isEmpty()) {
            DiscoveryEmpty(R.string.home_rediscover_empty, onOpenLibrary)
        } else {
            LazyRow(
                modifier = Modifier.homeCarouselScroll(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(albums, key = { it.albumKey }) { album ->
                    RecommendedAlbumCard(album, onClick = { onOpen(album) })
                }
            }
        }
    }
}

@Composable
internal fun HomeResumeSection(
    status: EchoPlaybackStatus,
    positionState: State<PlaybackPositionState>?,
    onResume: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    HomeDiscoverySection(R.string.home_resume, R.string.home_resume_detail) {
        val track = status.track
        if (track == null || status.state == EchoPlaybackState.Error) {
            DiscoveryEmpty(R.string.home_resume_empty, onOpenLibrary)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .echoClickable(onClick = onResume)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkTile(track.artworkUri, modifier = Modifier.size(88.dp), accent = MaterialTheme.colorScheme.primary, cornerRadius = 18.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(track.album?.takeIf { it.isNotBlank() } ?: track.title,
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.title, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ResumePosition(positionState, status.positionMs, status.durationMs)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (status.isPlaying) R.string.home_resume_open_player else R.string.home_resume_play))
                }
                TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.home_listening_open_library)) }
            }
        }
    }
}

@Composable
private fun ResumePosition(positionState: State<PlaybackPositionState>?, fallbackPosition: Long, fallbackDuration: Long) {
    // Only this text observes progress ticks, not the artwork or the whole home page.
    val position = positionState?.value
    val positionMs = position?.positionMs ?: fallbackPosition
    val durationMs = position?.durationMs?.takeIf { it > 0 } ?: fallbackDuration
    Text(stringResource(R.string.home_resume_position, formatDuration(positionMs.coerceAtLeast(0)), formatDuration(durationMs.coerceAtLeast(0))),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun HomeDiscoverySection(title: Int, subtitle: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun DiscoveryEmpty(text: Int, onOpenLibrary: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.home_listening_open_library)) }
    }
}

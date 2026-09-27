package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.*
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.feature.home.R as L10nR

@Composable
internal fun HomeAlbumRecommendationsSection(
    albums: List<AlbumSummary>,
    onRefresh: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeSectionHeader(stringResource(L10nR.string.feature_home_recommended_for_you_8335d9)) {
            IconButton(onClick = onRefresh, enabled = albums.isNotEmpty(), modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(L10nR.string.feature_home_refresh_828c69),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        LazyRow(
            modifier = Modifier.homeCarouselScroll(),
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (albums.isEmpty()) {
                item {
                    EmptyRecentAlbumsCard(
                        title = stringResource(L10nR.string.feature_home_no_recommendations_yet_040329),
                        subtitle = stringResource(L10nR.string.feature_home_play_or_favorite_albums_to_fill_this_row_cf6bca),
                        onClick = onOpenLibrary,
                    )
                }
            } else {
                items(albums, key = { it.albumKey }) { album ->
                    RecommendedAlbumCard(
                        album = album,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeArtistRankingSection(
    artists: List<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeSectionHeader(stringResource(L10nR.string.feature_home_artist_ranking_80100b))
        if (artists.isEmpty()) {
            EmptyRankingNotice(
                title = stringResource(L10nR.string.feature_home_no_ranking_data_yet_810ddb),
                subtitle = stringResource(L10nR.string.feature_home_appears_after_you_play_an_artist_8a9384),
                onClick = onOpenLibrary,
            )
        } else {
            val maxTracks = artists.maxOf { it.trackCount.coerceAtLeast(1) }
            artists.take(5).forEachIndexed { index, artist ->
                ArtistRankRow(
                    rank = index + 1,
                    artist = artist,
                    progress = artist.trackCount.coerceAtLeast(1).toFloat() / maxTracks.toFloat(),
                    onClick = { onOpenArtist(artist) },
                )
            }
        }
    }
}

@Composable
private fun ArtistRankRow(
    rank: Int,
    artist: ArtistSummary,
    progress: Float,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val durationLabel = artistReadableDuration(artist.durationMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .echoClickable(role = Role.Button, onClick = onClick)
            .background(
                if (rank == 1) {
                    Brush.horizontalGradient(
                        listOf(
                            scheme.primary.copy(alpha = if (dark) 0.12f else 0.12f),
                            if (dark) Color.White.copy(alpha = 0.025f) else echoTheme().accentDeep.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            rank.toString().padStart(2, '0'),
            color = if (rank == 1) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(34.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                artist.name,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(L10nR.string.feature_home_artist_trackcount_tracks_durationlabel_3431c6, (artist.trackCount).toString(), (durationLabel).toString()),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (dark) Color.White.copy(alpha = 0.09f) else scheme.surfaceVariant.copy(alpha = 0.52f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(scheme.primary.copy(alpha = 0.86f), scheme.primary.copy(alpha = 0.42f)),
                            ),
                        ),
                )
            }
        }
        Surface(
            color = Color.Transparent,
        ) {
            Text(
                stringResource(L10nR.string.feature_home_artist_albumcount_coerceatleast_0_albums_3a30a7, (artist.albumCount.coerceAtLeast(0)).toString()),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun HomeFavoriteAlbumsSection(
    albums: List<AlbumSummary>,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HomeSectionHeader(stringResource(L10nR.string.feature_home_albums_you_like_95a2b9))
        if (albums.isEmpty()) {
            HomeLibraryNotice(
                title = stringResource(L10nR.string.feature_home_no_favorite_albums_yet_7c8d3b),
                subtitle = stringResource(L10nR.string.feature_home_star_an_album_on_the_player_to_see_edd836),
                onClick = onOpenLibrary,
                )
        } else {
            LazyRow(
                modifier = Modifier.homeCarouselScroll(),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(albums.take(4), key = { it.albumKey }) { album ->
                    RecommendedAlbumCard(album = album, onClick = { onOpenAlbum(album) })
                }
            }
        }
    }
}

@Composable
private fun EmptyRankingNotice(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) echoTheme().panel.copy(alpha = 0.28f) else echoTheme().mist.copy(alpha = 0.42f))
            .border(if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.Transparent), RoundedCornerShape(18.dp))
            .echoClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = if (dark) scheme.onSurface else echoTheme().heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(subtitle, color = if (dark) scheme.onSurfaceVariant else echoTheme().muted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun artistReadableDuration(durationMs: Long): String {
    val minutes = (durationMs / 60000L).toInt()
    return if (minutes >= 1) {
        stringResource(L10nR.string.feature_home_minutes_min_c3e281, (minutes).toString())
    } else {
        formatDuration(durationMs)
    }
}


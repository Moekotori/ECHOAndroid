package app.echo.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackHeatmapDay

@Composable
fun HomeScreen(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    recentPlayedAlbums: List<AlbumSummary>,
    recentlyAddedAlbums: List<AlbumSummary>,
    recommendedAlbums: List<AlbumSummary>,
    topArtists: List<ArtistSummary>,
    favoriteAlbums: List<AlbumSummary>,
    heatmapDays: List<PlaybackHeatmapDay>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onRefreshRecommendations: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val compactViewport = configuration.screenHeightDp < 620 ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val scrollState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState),
        ) {
            RoonHomeHeader(
                status = status,
                compact = compactViewport,
                onOpenSearch = onOpenSearch,
            )
            Spacer(Modifier.height(if (compactViewport) 14.dp else 22.dp))
            Box(Modifier.padding(horizontal = 24.dp)) {
                LibraryOverview(
                    trackCount = trackCount,
                    albumCount = albumCount,
                    artistCount = artistCount,
                )
            }
            Spacer(Modifier.height(if (compactViewport) 14.dp else 22.dp))
            RoonRecentActivitySection(
                recentPlayedAlbums = recentPlayedAlbums,
                recentlyAddedAlbums = recentlyAddedAlbums,
                onOpenAlbum = onOpenAlbum,
                onOpenLibrary = onOpenLibrary,
            )
            Spacer(Modifier.height(if (compactViewport) 14.dp else 20.dp))
            HomeAlbumRecommendationsSection(
                albums = recommendedAlbums,
                onRefresh = onRefreshRecommendations,
                onOpenLibrary = onOpenLibrary,
                onOpenAlbum = onOpenAlbum,
            )
            Spacer(Modifier.height(if (compactViewport) 14.dp else 20.dp))
            HomeArtistRankingSection(
                artists = topArtists,
                onOpenArtist = onOpenArtist,
                onOpenLibrary = onOpenLibrary,
            )
            Spacer(Modifier.height(if (compactViewport) 14.dp else 20.dp))
            HomeFavoriteAlbumsSection(
                albums = favoriteAlbums,
                heatmapDays = heatmapDays,
                onOpenAlbum = onOpenAlbum,
                onOpenLibrary = onOpenLibrary,
            )
            Spacer(Modifier.height(if (compactViewport) 252.dp else 304.dp))
        }
    }
}


package app.echo.android.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibraryScanProgress
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
    scanState: LibraryScanProgress = LibraryScanProgress(),
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
    val sectionGap = if (compactViewport) 14.dp else 22.dp
    val blockGap = if (compactViewport) 14.dp else 20.dp
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        item(key = "header") {
            RoonHomeHeader(
                status = status,
                compact = compactViewport,
                onOpenSearch = onOpenSearch,
            )
        }
        item(key = "overview") {
            Spacer(Modifier.height(sectionGap))
            Box(Modifier.padding(horizontal = 24.dp)) {
                LibraryOverview(
                    trackCount = trackCount,
                    albumCount = albumCount,
                    artistCount = artistCount,
                    scanState = scanState,
                    onOpenLibrary = onOpenLibrary,
                )
            }
        }
        item(key = "recent") {
            Spacer(Modifier.height(sectionGap))
            RoonRecentActivitySection(
                recentPlayedAlbums = recentPlayedAlbums,
                recentlyAddedAlbums = recentlyAddedAlbums,
                onOpenAlbum = onOpenAlbum,
                onOpenLibrary = onOpenLibrary,
            )
        }
        item(key = "recommended") {
            Spacer(Modifier.height(blockGap))
            HomeAlbumRecommendationsSection(
                albums = recommendedAlbums,
                onRefresh = onRefreshRecommendations,
                onOpenLibrary = onOpenLibrary,
                onOpenAlbum = onOpenAlbum,
            )
        }
        item(key = "artists") {
            Spacer(Modifier.height(blockGap))
            HomeArtistRankingSection(
                artists = topArtists,
                onOpenArtist = onOpenArtist,
                onOpenLibrary = onOpenLibrary,
            )
        }
        item(key = "favorites") {
            Spacer(Modifier.height(blockGap))
            HomeFavoriteAlbumsSection(
                albums = favoriteAlbums,
                heatmapDays = heatmapDays,
                onOpenAlbum = onOpenAlbum,
                onOpenLibrary = onOpenLibrary,
            )
        }
        item(key = "bottom-inset") {
            Spacer(Modifier.height(if (compactViewport) 252.dp else 304.dp))
        }
    }
}


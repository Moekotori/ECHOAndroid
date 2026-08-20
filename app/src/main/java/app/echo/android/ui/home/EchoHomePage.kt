package app.echo.android.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.home.HomeScreen
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
internal fun EchoHomePage(
    viewModel: EchoAndroidViewModel,
    playbackStatus: EchoPlaybackStatus,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle(LibraryStats())
    val recentPlaybackAlbums by viewModel.recentPlaybackAlbums.collectAsStateWithLifecycle()
    val recentPlaybackArtists by viewModel.recentPlaybackArtists.collectAsStateWithLifecycle()
    val recentPlaybackHeatmap by viewModel.recentPlaybackHeatmap.collectAsStateWithLifecycle()
    val recentlyAddedAlbums by viewModel.recentlyAddedAlbums.collectAsStateWithLifecycle(emptyList())
    var homeRecommendationSeed by remember { mutableIntStateOf(0) }
    val homeRecommendedAlbums = remember(homeRecommendationSeed, recentlyAddedAlbums) {
        if (recentlyAddedAlbums.isEmpty()) {
            emptyList()
        } else {
            recentlyAddedAlbums.shuffled().take(8)
        }
    }
    HomeScreen(
        status = playbackStatus,
        trackCount = libraryStats.trackCount,
        albumCount = libraryStats.albumCount,
        artistCount = libraryStats.artistCount,
        recentPlayedAlbums = recentPlaybackAlbums,
        recentlyAddedAlbums = recentlyAddedAlbums,
        recommendedAlbums = homeRecommendedAlbums,
        topArtists = recentPlaybackArtists,
        favoriteAlbums = recentPlaybackAlbums.take(4),
        heatmapDays = recentPlaybackHeatmap,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::skipNext,
        onPrevious = viewModel::skipPrevious,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onToggleShuffle = viewModel::toggleShuffle,
        onRefreshRecommendations = { homeRecommendationSeed += 1 },
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onOpenLibrary = onOpenLibrary,
        onOpenConnect = onOpenConnect,
        onOpenSearch = onOpenSearch,
    )
}

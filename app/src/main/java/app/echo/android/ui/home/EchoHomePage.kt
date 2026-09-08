package app.echo.android.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.home.HomeScreen
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
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
    bottomInset: Dp,
) {
    val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle()
    val recentPlaybackAlbums by viewModel.recentPlaybackAlbums.collectAsStateWithLifecycle()
    val recentPlaybackArtists by viewModel.recentPlaybackArtists.collectAsStateWithLifecycle()
    val recentPlaybackHeatmap by viewModel.recentPlaybackHeatmap.collectAsStateWithLifecycle()
    val recentlyAddedAlbums by viewModel.recentlyAddedAlbums.collectAsStateWithLifecycle()
    val favoriteAlbums by viewModel.favoriteAlbums.collectAsStateWithLifecycle()
    val homeRecommendedAlbums by viewModel.recommendedAlbums.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    HomeScreen(
        status = playbackStatus,
        bottomInset = bottomInset,
        trackCount = libraryStats.trackCount,
        albumCount = libraryStats.albumCount,
        artistCount = libraryStats.artistCount,
        recentPlayedAlbums = recentPlaybackAlbums,
        recentlyAddedAlbums = recentlyAddedAlbums,
        recommendedAlbums = homeRecommendedAlbums,
        topArtists = recentPlaybackArtists,
        favoriteAlbums = favoriteAlbums,
        heatmapDays = recentPlaybackHeatmap,
        scanState = scanState,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::skipNext,
        onPrevious = viewModel::skipPrevious,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onToggleShuffle = viewModel::toggleShuffle,
        onRefreshRecommendations = viewModel::refreshHomeRecommendations,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onOpenLibrary = onOpenLibrary,
        onOpenConnect = onOpenConnect,
        onOpenSearch = onOpenSearch,
    )
}

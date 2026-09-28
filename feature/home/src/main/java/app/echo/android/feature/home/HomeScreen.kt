package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.echo.android.design.LocalEchoCustomBackgroundActive
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.State
import app.echo.android.model.playback.PlaybackPositionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackHeatmapDay
import java.time.DayOfWeek
import java.time.LocalDate

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
    onOpenListening: (() -> Unit)? = null,
    bottomInset: Dp = 0.dp,
    rediscoveredAlbums: List<AlbumSummary> = emptyList(),
    positionState: State<PlaybackPositionState>? = null,
    onPlayAlbum: (AlbumSummary) -> Unit = onOpenAlbum,
    onResumePlayback: () -> Unit = onPlayPause,
    recentPlayedTracks: List<app.echo.android.model.library.EchoTrack> = emptyList(),
    onPlayTrack: (app.echo.android.model.library.EchoTrack) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val compactViewport = configuration.screenHeightDp < 620 ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val distinctRecommendations = remember(recommendedAlbums, recentPlayedAlbums, recentlyAddedAlbums) {
        val recentKeys = (recentPlayedAlbums + recentlyAddedAlbums).mapTo(hashSetOf()) { it.albumKey }
        recommendedAlbums.filterNot { it.albumKey in recentKeys }
    }
    val dailyAlbums = remember(recommendedAlbums, recentlyAddedAlbums, favoriteAlbums) {
        (recommendedAlbums.take(24) + recentlyAddedAlbums.take(24) + favoriteAlbums.take(24))
            .distinctBy { it.albumKey }.take(24)
    }
    val hasRecentMusic = recentPlayedAlbums.isNotEmpty() || recentlyAddedAlbums.isNotEmpty() || recentPlayedTracks.isNotEmpty()
    val today = LocalDate.now()
    val hasListeningHistory = remember(heatmapDays, today) {
        val firstDay = today.with(DayOfWeek.MONDAY).minusWeeks(11).toEpochDay()
        heatmapDays.any { it.playCount > 0 && it.epochDay in firstDay..today.toEpochDay() }
    }
    HomeAppearance {
        val base = MaterialTheme.colorScheme.background
        val background = base.copy(alpha = if (LocalEchoCustomBackgroundActive.current) 0.94f else 1f)
        Box(Modifier.fillMaxSize().background(background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(top = 12.dp, bottom = bottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactViewport) 24.dp else 28.dp),
            ) {
                item(key = "header") { HomeSectionEntrance(0) { HomeHeader(onOpenSearch) } }
                if (onOpenListening != null) {
                    item(key = "listen-together") {
                        HomeListeningEntry(onOpenListening)
                    }
                }
                if (status.state == EchoPlaybackState.Error &&
                    status.diagnostics.lastError?.kind == EchoAudioErrorKind.FileMissing) {
                    item(key = "missing-file") {
                        HomeLibraryNotice(
                            title = stringResource(R.string.home_missing_file_title),
                            subtitle = stringResource(R.string.home_missing_file_detail),
                            onClick = onOpenLibrary,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                if (status.track != null && status.state != EchoPlaybackState.Error) {
                    item(key = "resume-listening") {
                        HomeSectionEntrance(1) { HomeResumeSection(status, positionState, onResumePlayback) }
                    }
                }
                if (trackCount == 0 && !hasRecentMusic && dailyAlbums.isEmpty()) {
                    item(key = "empty-library") {
                        HomeEmptyLibrary(scanState, onOpenLibrary)
                    }
                }
                if (hasRecentMusic) {
                    item(key = "recent") {
                        HomeSectionEntrance(2) {
                            RoonRecentActivitySection(
                                recentPlayedAlbums = recentPlayedAlbums,
                                recentlyAddedAlbums = recentlyAddedAlbums,
                                recentPlayedTracks = recentPlayedTracks,
                                onOpenAlbum = onOpenAlbum,
                                onOpenLibrary = onOpenLibrary,
                                onPlayTrack = onPlayTrack,
                            )
                        }
                    }
                }
                if (dailyAlbums.isNotEmpty()) {
                    item(key = "daily-album") {
                        HomeSectionEntrance(3) { HomeDailyAlbumSection(dailyAlbums, onPlayAlbum, onOpenAlbum) }
                    }
                }
                if (distinctRecommendations.isNotEmpty()) {
                    item(key = "recommended") {
                        HomeAlbumRecommendationsSection(distinctRecommendations, onRefreshRecommendations, onOpenLibrary, onOpenAlbum)
                    }
                }
                if (favoriteAlbums.isNotEmpty()) {
                    item(key = "favorites") {
                        HomeFavoriteAlbumsSection(favoriteAlbums, onOpenAlbum, onOpenLibrary)
                    }
                }
                if (rediscoveredAlbums.isNotEmpty()) {
                    item(key = "rediscover") { HomeRediscoverySection(rediscoveredAlbums, onOpenAlbum) }
                }
                if (topArtists.isNotEmpty()) {
                    item(key = "artists") { HomeArtistRankingSection(topArtists, onOpenArtist, onOpenLibrary) }
                }
                if (hasListeningHistory) {
                    item(key = "listening-summary") {
                        Box(Modifier.padding(horizontal = 24.dp)) {
                            HomeListeningSummary(heatmapDays, onOpenLibrary)
                        }
                    }
                }
                if (trackCount > 0 || hasRecentMusic) {
                    item(key = "overview") {
                        Box(Modifier.padding(horizontal = 24.dp)) {
                            LibraryOverview(trackCount, albumCount, artistCount, scanState, onOpenLibrary)
                        }
                    }
                }
            }
            // A static scrim keeps scrolled text from showing through the floating player/dock.
            // This overlay has no input handlers, so list and carousel gestures remain available.
            if (bottomInset > 0.dp) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomInset + 32.dp)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.10f to base, 1f to base)))
            }
        }
    }
}

private object HomeCarouselNestedScroll : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (source == NestedScrollSource.UserInput && available.x != 0f) {
            Offset(available.x, 0f)
        } else {
            Offset.Zero
        }
    }
}

/** Keep leftover horizontal drags on album rows instead of turning them into tab swipes. */
internal fun Modifier.homeCarouselScroll(): Modifier = nestedScroll(HomeCarouselNestedScroll)

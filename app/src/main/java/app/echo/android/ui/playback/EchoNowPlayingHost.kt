package app.echo.android.ui.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import app.echo.android.feature.player.LyricsManagerDialog
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.data.EchoAppSettings
import app.echo.android.feature.player.NowPlayingScreen
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
internal fun EchoNowPlayingHost(
    viewModel: EchoAndroidViewModel,
    playbackStatus: EchoPlaybackStatus,
    appSettings: EchoAppSettings,
    lyricsFontFamily: FontFamily?,
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
    onPlayPause: () -> Unit = viewModel::playPause,
    onNext: () -> Unit = viewModel::skipNext,
    onPrevious: () -> Unit = viewModel::skipPrevious,
    onSeek: (Long) -> Unit = viewModel::seekTo,
    onImportLyrics: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onImportLyricsFont: () -> Unit,
    modifier: Modifier = Modifier,
    openLyricsRequestId: Int = 0,
    predictiveBackProgress: () -> Float = { 0f },
) {
    // 传 State 引用而非值:进度 tick 不在宿主层触发重组,由页内叶子订阅
    val playbackPosition = viewModel.playbackPosition.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val isCurrentTrackFavorite = playbackStatus.track?.id?.let { it in favoriteTrackIds } == true
    var showLyricsManager by remember(playbackStatus.track?.id) { mutableStateOf(false) }
    if (showLyricsManager) {
        val candidates by viewModel.lyricsCandidates.collectAsStateWithLifecycle()
        val searching by viewModel.lyricsSearching.collectAsStateWithLifecycle()
        val error by viewModel.lyricsManagementError.collectAsStateWithLifecycle()
        DisposableEffect(Unit) { onDispose { viewModel.cancelLyricsSearch() } }
        LyricsManagerDialog(
            trackTitle = playbackStatus.track?.title.orEmpty(), candidates = candidates,
            searching = searching, error = error,
            selectedId = (lyricsState as? app.echo.android.model.lyrics.EchoLyricsLoadState.Ready)?.lyrics?.metadata?.get("selection_id"),
            onSearch = viewModel::searchLyrics,
            onChoose = viewModel::selectLyricsCandidate,
            onImport = { showLyricsManager = false; onImportLyrics() },
            onRemove = viewModel::removeLyricsSelection,
            onAdjustOffset = viewModel::adjustLyricsOffset,
            onDismiss = { showLyricsManager = false },
        )
    }
    NowPlayingScreen(
        status = playbackStatus,
        positionState = playbackPosition,
        lyricsState = lyricsState,
        showLyricsControlDeck = appSettings.showLyricsControlDeck,
        lyricsFontFamily = lyricsFontFamily,
        lyricsFontMode = appSettings.lyricsFontFamily,
        lyricsFontScale = appSettings.lyricsFontScale,
        lyricsColorMode = appSettings.lyricsColorMode,
        lyricsAlignment = appSettings.lyricsAlignment,
        lyricsLineSpacing = appSettings.lyricsLineSpacing,
        lyricsBackgroundDim = appSettings.lyricsBackgroundDim,
        lyricsWordHighlightEnabled = appSettings.lyricsWordHighlightEnabled,
        lyricsWordHighlightIntensity = appSettings.lyricsWordHighlightIntensity,
        lyricsImmersiveModeEnabled = appSettings.lyricsImmersiveModeEnabled,
        lyricsMotionMode = appSettings.lyricsMotionMode,
        lyricsShowTranslation = appSettings.lyricsShowTranslation,
        lyricsShowRomanization = appSettings.lyricsShowRomanization,
        lyricsFocusGlowEnabled = appSettings.lyricsFocusGlowEnabled,
        importedFontUri = appSettings.importedFontUri,
        onlineLyricsEnabled = appSettings.onlineLyricsEnabled,
        onDismiss = onDismiss,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onSeek = onSeek,
        onOpenQueue = onOpenQueue,
        onCast = onCast,
        castActive = castActive,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onToggleShuffle = viewModel::toggleShuffle,
        onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
        onSetSleepTimer = viewModel::setSleepTimer,
        onSetSleepTimerEndOfTrack = viewModel::setSleepTimerEndOfTrack,
        onCancelSleepTimer = viewModel::cancelSleepTimer,
        onSetReplayGain = viewModel::setReplayGain,
        onSetReplayGainMode = viewModel::setReplayGainMode,
        onAdjustReplayGainPreamp = viewModel::adjustReplayGainPreamp,
        replayGainScanState = viewModel.replayGainScanState.collectAsStateWithLifecycle().value,
        onScanReplayGain = viewModel::scanReplayGainForCurrentTrack,
        onSetSkipSilenceEnabled = viewModel::setSkipSilenceEnabled,
        onImportLyrics = { showLyricsManager = true },
        onAdjustLyricsOffset = viewModel::adjustLyricsOffset,
        onResetLyricsOffset = viewModel::resetLyricsOffset,
        onOpenArtist = onOpenArtist,
        onOpenAlbum = onOpenAlbum,
        onImportLyricsFont = onImportLyricsFont,
        onLyricsFontFamilyChange = viewModel::setLyricsFontFamily,
        onLyricsFontScaleChange = viewModel::setLyricsFontScale,
        onLyricsColorModeChange = viewModel::setLyricsColorMode,
        onLyricsAlignmentChange = viewModel::setLyricsAlignment,
        onLyricsLineSpacingChange = viewModel::setLyricsLineSpacing,
        onLyricsBackgroundDimChange = viewModel::setLyricsBackgroundDim,
        onLyricsWordHighlightEnabledChange = viewModel::setLyricsWordHighlightEnabled,
        onLyricsWordHighlightIntensityChange = viewModel::setLyricsWordHighlightIntensity,
        onLyricsImmersiveModeChange = viewModel::setLyricsImmersiveModeEnabled,
        onLyricsMotionModeChange = viewModel::setLyricsMotionMode,
        onLyricsShowTranslationChange = viewModel::setLyricsShowTranslation,
        onLyricsShowRomanizationChange = viewModel::setLyricsShowRomanization,
        onLyricsFocusGlowChange = viewModel::setLyricsFocusGlowEnabled,
        onShowLyricsControlDeckChange = viewModel::setShowLyricsControlDeck,
        onOnlineLyricsEnabledChange = viewModel::setOnlineLyricsEnabled,
        isCurrentTrackFavorite = isCurrentTrackFavorite,
        onToggleFavorite = { viewModel.toggleFavorite() },
        openLyricsRequestId = openLyricsRequestId,
        predictiveBackProgress = predictiveBackProgress,
        modifier = modifier.fillMaxSize(),
    )
}

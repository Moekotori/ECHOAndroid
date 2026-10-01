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
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.radio.EchoRadioStation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    onImportLyricsFont: () -> Unit,
    modifier: Modifier = Modifier,
    openLyricsRequestId: Int = 0,
    predictiveBackProgress: () -> Float = { 0f },
    presentationExpanded: Boolean = true,
    onDragProgress: (Float) -> Unit = {},
    /** 投送到 DLNA / Chromecast 时换成远端进度。 */
    positionFlow: StateFlow<PlaybackPositionState> = viewModel.playbackPosition,
    onOpenArtist: (ArtistSummary) -> Unit = {},
) {
    // 传 State 引用而非值:进度 tick 不在宿主层触发重组,由页内叶子订阅
    val playbackPosition = positionFlow.collectAsStateWithLifecycle()
    val artistNavigationScope = rememberCoroutineScope()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val isCurrentTrackFavorite = playbackStatus.track?.id?.let { it in favoriteTrackIds } == true
    var showLyricsManager by remember(playbackStatus.track?.id) { mutableStateOf(false) }
    var showLyricsEditor by remember(playbackStatus.track?.id) { mutableStateOf(false) }
    if (showLyricsEditor) playbackStatus.track?.let { track ->
        val source = (lyricsState as? app.echo.android.model.lyrics.EchoLyricsLoadState.Ready)?.lyrics
            ?: app.echo.android.model.lyrics.EchoLyrics(lines = listOf(app.echo.android.model.lyrics.EchoLyricLine(-1, text = "")))
        app.echo.android.feature.player.LyricsEditorSheet(source,
            position = { if (castActive) playbackPosition.value.positionMs else viewModel.currentPlaybackPositionMs() },
            onPlayPause = onPlayPause, onSeek = onSeek,
            onSave = { viewModel.saveEditedLyrics(track.id, it) }, onDismiss = { showLyricsEditor = false })
    }
    var showTrackTools by remember(playbackStatus.track?.id) { mutableStateOf(false) }
    if (showTrackTools) playbackStatus.track?.let { track ->
        val loop by viewModel.abLoop.collectAsStateWithLifecycle()
        val tools = remember(viewModel) {
            val library = viewModel.libraryExperience
            app.echo.android.feature.player.TrackToolsActions(library::bookmarks, library::saveBookmark,
                library::deleteBookmark, viewModel::setAbLoop)
        }
        val libraryTrack = remember(track) { app.echo.android.model.library.EchoTrack(track.id, track.uri, track.title, track.artist,
            album = track.album, artworkUri = track.artworkUri, durationMs = track.durationMs, sampleRateHz = track.sampleRateHz,
            clipStartMs = track.clipStartMs, clipEndMs = track.clipEndMs) }
        app.echo.android.feature.player.TrackToolsSheet(libraryTrack, {
            if (castActive) playbackPosition.value.positionMs else viewModel.currentPlaybackPositionMs()
        }, loop,
            (lyricsState as? app.echo.android.model.lyrics.EchoLyricsLoadState.Ready)?.lyrics, tools,
            onSeek = onSeek, onDismiss = { showTrackTools = false }, loopAvailable = !castActive)
    }
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
            onEdit = { showLyricsManager = false; showLyricsEditor = true },
        )
    }
    NowPlayingScreen(
        status = playbackStatus,
        positionState = playbackPosition,
        lyricsState = lyricsState,
        showLyricsControlDeck = appSettings.showLyricsControlDeck,
        playerPageStyle = appSettings.playerPageStyle,
        playerTextScale = appSettings.playerTextScale,
        playerArtworkScale = appSettings.playerArtworkScale,
        onPlayerAppearanceChange = viewModel::setPlayerAppearance,
        lyricsFontFamily = lyricsFontFamily,
        lyricsPageStyle = appSettings.lyricsPageStyle,
        lyricsFontMode = appSettings.lyricsFontFamily,
        lyricsFontScale = appSettings.lyricsFontScale,
        lyricsColorMode = appSettings.lyricsColorMode,
        lyricsAlignment = appSettings.lyricsAlignment,
        lyricsLineSpacing = appSettings.lyricsLineSpacing,
        lyricsBackgroundDim = appSettings.lyricsBackgroundDim,
        lyricsWordHighlightEnabled = appSettings.lyricsWordHighlightEnabled,
        lyricsEstimatedWordHighlightEnabled = appSettings.lyricsEstimatedWordHighlightEnabled,
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
        onOpenTrackTools = { showTrackTools = true },
        onCast = onCast,
        castActive = castActive,
        onSetRepeatMode = viewModel::setRepeatMode,
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
        onImportLyrics = { showLyricsManager = true },
        onAdjustLyricsOffset = viewModel::adjustLyricsOffset,
        onResetLyricsOffset = viewModel::resetLyricsOffset,
        onImportLyricsFont = onImportLyricsFont,
        onLyricsPageStyleChange = viewModel::setLyricsPageStyle,
        onLyricsFontFamilyChange = viewModel::setLyricsFontFamily,
        onLyricsFontScaleChange = viewModel::setLyricsFontScale,
        onLyricsColorModeChange = viewModel::setLyricsColorMode,
        onLyricsAlignmentChange = viewModel::setLyricsAlignment,
        onLyricsLineSpacingChange = viewModel::setLyricsLineSpacing,
        onLyricsBackgroundDimChange = viewModel::setLyricsBackgroundDim,
        onLyricsWordHighlightEnabledChange = viewModel::setLyricsWordHighlightEnabled,
        onLyricsEstimatedWordHighlightEnabledChange = viewModel::setLyricsEstimatedWordHighlightEnabled,
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
        onOpenArtist = { trackId, artistName ->
            artistNavigationScope.launch {
                if (EchoRadioStation.isRadio(trackId)) return@launch
                val artwork = playbackStatus.track?.takeIf { it.id == trackId }?.artworkUri
                val artist = viewModel.artistForTrack(trackId)
                    ?: viewModel.artistNavigationTarget(artistName, artwork)
                    ?: return@launch
                onOpenArtist(artist)
            }
        },
        openLyricsRequestId = openLyricsRequestId,
        predictiveBackProgress = predictiveBackProgress,
        presentationExpanded = presentationExpanded,
        onDragProgress = onDragProgress,
        modifier = modifier.fillMaxSize(),
    )
}

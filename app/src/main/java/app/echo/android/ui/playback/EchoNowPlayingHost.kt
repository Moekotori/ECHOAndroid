package app.echo.android.ui.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
    onImportLyrics: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onImportLyricsFont: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
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
        onPlayPause = viewModel::playPause,
        onNext = viewModel::skipNext,
        onPrevious = viewModel::skipPrevious,
        onSeek = viewModel::seekTo,
        onOpenQueue = onOpenQueue,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onToggleShuffle = viewModel::toggleShuffle,
        onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
        onSetSleepTimer = viewModel::setSleepTimer,
        onCancelSleepTimer = viewModel::cancelSleepTimer,
        onSetReplayGain = viewModel::setReplayGain,
        onAdjustReplayGainPreamp = viewModel::adjustReplayGainPreamp,
        onSetSkipSilenceEnabled = viewModel::setSkipSilenceEnabled,
        onImportLyrics = onImportLyrics,
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
        modifier = modifier.fillMaxSize(),
    )
}

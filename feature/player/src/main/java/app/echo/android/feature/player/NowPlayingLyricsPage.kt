package app.echo.android.feature.player

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.settings.EchoLyricsPageStyle
import app.echo.android.feature.player.R as L10nR

@Composable
internal fun NowPlayingLyricsPage(
    status: EchoPlaybackStatus,
    lyricsState: EchoLyricsLoadState,
    showLyricsControlDeck: Boolean,
    lyricsFontFamily: FontFamily?,
    lyricsFontScale: Float,
    lyricsColorMode: String,
    lyricsAlignment: String,
    lyricsLineSpacing: Float,
    lyricsBackgroundDim: Float,
    lyricsWordHighlightEnabled: Boolean,
    lyricsWordHighlightIntensity: Float,
    lyricsImmersiveModeEnabled: Boolean,
    lyricsMotionMode: String,
    lyricsShowTranslation: Boolean,
    lyricsShowRomanization: Boolean,
    lyricsFocusGlowEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onImportLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenLyricsSettings: () -> Unit,
    lyricsPageStyle: EchoLyricsPageStyle,
    palette: ArtworkPalette,
    showBackdrop: Boolean = false,
    showTransportDock: Boolean = true,
    modifier: Modifier = Modifier,
    animationsVisible: Boolean = true,
) {
    LyricsPageTheme(lyricsPageStyle) {
        val paper = lyricsPageStyle == EchoLyricsPageStyle.Paper
        val readyLyrics = (lyricsState as? EchoLyricsLoadState.Ready)?.lyrics
        val displayPosition = rememberLyricsDisplayPosition(
            positionMsState, status.track?.id, status.isPlaying, status.playbackSpeed,
            animationsVisible && !LocalEchoEffectivePerformanceMode.current.isLightweight,
        )
        val lyricAccent = lyricsColorForMode(lyricsColorMode)
        val lyricsDimAlpha by animateFloatAsState(
            targetValue = lyricsBackgroundDim.coerceIn(0f, 0.78f),
            animationSpec = tween(durationMillis = 240, easing = LyricsSettingsMotionEasing),
            label = "lyrics-page-dim",
        )
        Box(modifier = modifier.fillMaxWidth()) {
            if (showBackdrop) LyricsPageBackdrop(status.track?.artworkUri, palette, { 1f }, animationsVisible, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background((if (LocalEchoDarkTheme.current) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = lyricsDimAlpha)),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LyricsTrackHeading(status.track, paper)
                Box(
                    modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (lyricsState) {
                        EchoLyricsLoadState.Idle -> LyricsEmptyState(
                            stringResource(L10nR.string.feature_player_lyrics_appear_after_you_pick_a_song_1622d2),
                            onImportLyrics,
                        )
                        EchoLyricsLoadState.Loading -> LyricsEmptyState(
                            stringResource(L10nR.string.feature_player_reading_local_lyrics_807f08),
                        )
                        EchoLyricsLoadState.Missing -> LyricsEmptyState(
                            stringResource(L10nR.string.feature_player_no_matching_lyrics_found_7ffc7e),
                            onImportLyrics,
                        )
                        is EchoLyricsLoadState.Error -> LyricsEmptyState(lyricsState.message, onImportLyrics)
                        is EchoLyricsLoadState.Ready -> LyricsLineList(
                            lyrics = lyricsState.lyrics,
                            onAdjustOffset = onAdjustLyricsOffset,
                            positionMsState = displayPosition,
                            onSeek = onSeek,
                            lyricsFontFamily = lyricsFontFamily,
                            lyricsFontScale = lyricsFontScale,
                            lyricAccent = lyricAccent,
                            highlightColor = if (lyricsColorMode == "white") MaterialTheme.colorScheme.primary else lyricAccent,
                            paper = paper,
                            lyricsAlignment = lyricsAlignment,
                            lyricsLineSpacing = lyricsLineSpacing,
                            lyricsWordHighlightEnabled = lyricsWordHighlightEnabled,
                            lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
                            lyricsImmersiveModeEnabled = lyricsImmersiveModeEnabled,
                            lyricsMotionMode = lyricsMotionMode,
                            showTranslation = lyricsShowTranslation,
                            showRomanization = lyricsShowRomanization,
                            focusGlowEnabled = lyricsFocusGlowEnabled,
                            animationsVisible = animationsVisible,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showLyricsControlDeck && readyLyrics != null,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = EchoMotion.silkSize(360),
                    ) + fadeIn(tween(durationMillis = 220, delayMillis = 40, easing = LyricsSettingsMotionEasing)) +
                        slideInVertically(EchoMotion.silkOffset(360)) { -it / 4 },
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = EchoMotion.silkSize(240),
                    ) + fadeOut(tween(durationMillis = 160, easing = LyricsSettingsMotionEasing)) +
                        slideOutVertically(EchoMotion.silkOffset(240)) { -it / 5 },
                ) {
                    readyLyrics?.let { lyrics ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(tween(durationMillis = 260, easing = LyricsSettingsMotionEasing)),
                        ) {
                            LyricsControlDeck(
                                lyrics = lyrics,
                                onImportLyrics = onImportLyrics,
                                onAdjustLyricsOffset = onAdjustLyricsOffset,
                                onResetLyricsOffset = onResetLyricsOffset,
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                if (showTransportDock) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                            if (paper) {
                                LyricsTrackIdentity(status.track, Modifier.padding(bottom = 12.dp), artworkSize = 36)
                            }
                            LyricsScrubber(
                                trackKey = status.track?.id,
                                positionMsState = positionMsState,
                                durationMsState = durationMsState,
                                onSeek = onSeek,
                            )
                            Spacer(Modifier.height(6.dp))
                            LyricsTransportControls(
                                isPlaying = status.isPlaying,
                                onOpenSettings = onOpenLyricsSettings,
                                onPlayPause = onPlayPause,
                                onNext = onNext,
                                onPrevious = onPrevious,
                                onOpenQueue = onOpenQueue,
                                onCast = onCast,
                                castActive = castActive,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    PlayerControlButton(
                        icon = PlayerControlIcons.Settings,
                        description = stringResource(L10nR.string.feature_player_lyrics_settings_843bc9),
                        onClick = onOpenLyricsSettings,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

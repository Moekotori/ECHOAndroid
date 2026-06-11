package app.echo.android.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.BlurredArtworkBackground
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassNight
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.PlaybackPositionState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 封面毛玻璃背景上的前景色：白色为主，半透明分级
private val OnArt = Color.White
private val OnArtMuted = Color.White.copy(alpha = 0.84f)
private val OnArtFaint = Color.White.copy(alpha = 0.42f)
private val OnArtChip = Color.White.copy(alpha = 0.24f)
private val LyricsSettingsMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

private data class LyricsColorOption(
    val value: String,
    val label: String,
    val color: Color,
)

private data class LyricsTextOption(
    val value: String,
    val label: String,
)

private val LyricsColorOptions = listOf(
    LyricsColorOption("white", "白", Color.White),
    LyricsColorOption("warm", "暖", Color(0xFFFFD6A0)),
    LyricsColorOption("blue", "蓝", Color(0xFF9ED8FF)),
    LyricsColorOption("violet", "紫", Color(0xFFD9C2FF)),
    LyricsColorOption("mint", "绿", Color(0xFFA9F3D0)),
)

private val LyricsAlignmentOptions = listOf(
    LyricsTextOption("center", "居中"),
    LyricsTextOption("start", "左对齐"),
    LyricsTextOption("dynamic", "舞台"),
)

private val LyricsMotionOptions = listOf(
    LyricsTextOption("calm", "安静"),
    LyricsTextOption("smooth", "顺滑"),
    LyricsTextOption("stage", "舞台"),
)

private val PlaybackSpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SleepTimerOptions = listOf(15, 30, 60)

private enum class NowPlayingPage {
    Cover,
    Lyrics,
}

@Composable
fun NowPlayingScreen(
    status: EchoPlaybackStatus,
    lyricsState: EchoLyricsLoadState,
    showLyricsControlDeck: Boolean,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetPlaybackSpeed: (Float, Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onAdjustReplayGainPreamp: (Float) -> Unit,
    onSetSkipSilenceEnabled: (Boolean) -> Unit,
    onImportLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    positionState: PlaybackPositionState? = null,
    lyricsFontFamily: FontFamily? = null,
    lyricsFontMode: String = "system",
    lyricsFontScale: Float = 1f,
    lyricsColorMode: String = "white",
    lyricsAlignment: String = "center",
    lyricsLineSpacing: Float = 1f,
    lyricsBackgroundDim: Float = 0f,
    lyricsWordHighlightEnabled: Boolean = true,
    lyricsWordHighlightIntensity: Float = 1f,
    lyricsImmersiveModeEnabled: Boolean = false,
    lyricsMotionMode: String = "smooth",
    lyricsShowTranslation: Boolean = true,
    lyricsShowRomanization: Boolean = true,
    lyricsFocusGlowEnabled: Boolean = false,
    importedFontUri: String? = null,
    onlineLyricsEnabled: Boolean = false,
    onImportLyricsFont: () -> Unit = {},
    onLyricsFontFamilyChange: (String) -> Unit = {},
    onLyricsFontScaleChange: (Float) -> Unit = {},
    onLyricsColorModeChange: (String) -> Unit = {},
    onLyricsAlignmentChange: (String) -> Unit = {},
    onLyricsLineSpacingChange: (Float) -> Unit = {},
    onLyricsBackgroundDimChange: (Float) -> Unit = {},
    onLyricsWordHighlightEnabledChange: (Boolean) -> Unit = {},
    onLyricsWordHighlightIntensityChange: (Float) -> Unit = {},
    onLyricsImmersiveModeChange: (Boolean) -> Unit = {},
    onLyricsMotionModeChange: (String) -> Unit = {},
    onLyricsShowTranslationChange: (Boolean) -> Unit = {},
    onLyricsShowRomanizationChange: (Boolean) -> Unit = {},
    onLyricsFocusGlowChange: (Boolean) -> Unit = {},
    onShowLyricsControlDeckChange: (Boolean) -> Unit = {},
    onOnlineLyricsEnabledChange: (Boolean) -> Unit = {},
) {
    val track = status.track
    val effectivePerformanceMode = LocalEchoEffectivePerformanceMode.current
    val effectiveLyricsFocusGlowEnabled = lyricsFocusGlowEnabled && !effectivePerformanceMode.isLightweight
    val palette = rememberArtworkPalette(track?.artworkUri, seedKey = track?.id)
    val pagerState = rememberPagerState(
        initialPage = NowPlayingPage.Cover.ordinal,
        pageCount = { NowPlayingPage.entries.size },
    )
    val pageScope = rememberCoroutineScope()
    val activePositionMs = positionState?.positionMs ?: status.positionMs
    val activeDurationMs = positionState?.durationMs?.takeIf { it > 0L } ?: status.durationMs
    val lyricsPageOffset = (pagerState.currentPage - NowPlayingPage.Lyrics.ordinal) +
        pagerState.currentPageOffsetFraction
    val lyricsReveal = (1f - abs(lyricsPageOffset)).coerceIn(0f, 1f)
    val readyLyrics = (lyricsState as? EchoLyricsLoadState.Ready)?.lyrics
    val hasTranslation = remember(readyLyrics) {
        readyLyrics?.lines?.any { !it.translation.isNullOrBlank() } == true
    }
    val hasRomanization = remember(readyLyrics) {
        readyLyrics?.lines?.any { !it.romanization.isNullOrBlank() } == true
    }
    var lyricsSettingsVisible by remember { mutableStateOf(false) }
    var playbackSettingsVisible by remember { mutableStateOf(false) }
    val lyricAccent = lyricsColorForMode(lyricsColorMode)

    Box(modifier = modifier.fillMaxSize()) {
        BlurredArtworkBackground(
            artworkUri = track?.artworkUri,
            palette = palette,
            modifier = Modifier.fillMaxSize(),
            artworkScale = 1.04f + 0.16f * lyricsReveal,
            artworkBlur = 30.dp * lyricsReveal,
            artworkAlpha = 0.92f - 0.12f * lyricsReveal,
            overlayStartAlpha = 0.18f + 0.14f * lyricsReveal,
            overlayMidAlpha = 0.34f + 0.14f * lyricsReveal,
            overlayEndAlpha = 0.78f,
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(170.dp)
                .background(
                    Brush.verticalGradient(
                        0f to EchoGlassNight.copy(alpha = 0.34f - 0.08f * lyricsReveal),
                        0.48f to EchoGlassInk.copy(alpha = 0.16f - 0.04f * lyricsReveal),
                        1f to Color.Transparent,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 560.dp)
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NowPlayingTopBar(onDismiss = onDismiss)

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (NowPlayingPage.entries[page]) {
                    NowPlayingPage.Cover -> NowPlayingCoverPage(
                        status = status,
                        positionMs = activePositionMs,
                        durationMs = activeDurationMs,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onOpenQueue = onOpenQueue,
                        playbackSettingsExpanded = playbackSettingsVisible,
                        onOpenPlaybackSettings = { playbackSettingsVisible = true },
                        onOpenLyrics = {
                            pageScope.launch {
                                pagerState.animateScrollToPage(NowPlayingPage.Lyrics.ordinal)
                            }
                        },
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        modifier = Modifier.fillMaxSize(),
                    )
                    NowPlayingPage.Lyrics -> NowPlayingLyricsPage(
                        status = status,
                        lyricsState = lyricsState,
                        showLyricsControlDeck = showLyricsControlDeck,
                        lyricsFontFamily = lyricsFontFamily,
                        lyricsFontMode = lyricsFontMode,
                        lyricsFontScale = lyricsFontScale,
                        lyricsColorMode = lyricsColorMode,
                        lyricsAlignment = lyricsAlignment,
                        lyricsLineSpacing = lyricsLineSpacing,
                        lyricsBackgroundDim = lyricsBackgroundDim,
                        lyricsWordHighlightEnabled = lyricsWordHighlightEnabled,
                        lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
                        lyricsImmersiveModeEnabled = lyricsImmersiveModeEnabled,
                        lyricsMotionMode = lyricsMotionMode,
                        lyricsShowTranslation = lyricsShowTranslation,
                        lyricsShowRomanization = lyricsShowRomanization,
                        lyricsFocusGlowEnabled = effectiveLyricsFocusGlowEnabled,
                        importedFontUri = importedFontUri,
                        onlineLyricsEnabled = onlineLyricsEnabled,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onOpenQueue = onOpenQueue,
                        positionMs = activePositionMs,
                        durationMs = activeDurationMs,
                        onCloseLyrics = {
                            pageScope.launch {
                                pagerState.animateScrollToPage(NowPlayingPage.Cover.ordinal)
                            }
                        },
                        onImportLyrics = onImportLyrics,
                        onImportLyricsFont = onImportLyricsFont,
                        onAdjustLyricsOffset = onAdjustLyricsOffset,
                        onResetLyricsOffset = onResetLyricsOffset,
                        onLyricsFontFamilyChange = onLyricsFontFamilyChange,
                        onLyricsFontScaleChange = onLyricsFontScaleChange,
                        onLyricsColorModeChange = onLyricsColorModeChange,
                        onLyricsAlignmentChange = onLyricsAlignmentChange,
                        onLyricsLineSpacingChange = onLyricsLineSpacingChange,
                        onLyricsBackgroundDimChange = onLyricsBackgroundDimChange,
                        onLyricsWordHighlightEnabledChange = onLyricsWordHighlightEnabledChange,
                        onLyricsWordHighlightIntensityChange = onLyricsWordHighlightIntensityChange,
                        onLyricsImmersiveModeChange = onLyricsImmersiveModeChange,
                        onLyricsMotionModeChange = onLyricsMotionModeChange,
                        onLyricsShowTranslationChange = onLyricsShowTranslationChange,
                        onLyricsShowRomanizationChange = onLyricsShowRomanizationChange,
                        onLyricsFocusGlowChange = onLyricsFocusGlowChange,
                        onShowLyricsControlDeckChange = onShowLyricsControlDeckChange,
                        onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
                        onOpenLyricsSettings = { lyricsSettingsVisible = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        LyricsSettingsDrawer(
            visible = lyricsSettingsVisible,
            lyricsFontMode = lyricsFontMode,
            importedFontUri = importedFontUri,
            lyricsFontScale = lyricsFontScale,
            lyricsColorMode = lyricsColorMode,
            lyricsAlignment = lyricsAlignment,
            lyricsLineSpacing = lyricsLineSpacing,
            lyricsBackgroundDim = lyricsBackgroundDim,
            lyricsWordHighlightEnabled = lyricsWordHighlightEnabled,
            lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
            lyricsImmersiveModeEnabled = lyricsImmersiveModeEnabled,
            lyricsMotionMode = lyricsMotionMode,
            lyricAccent = lyricAccent,
            showTranslation = lyricsShowTranslation,
            showRomanization = lyricsShowRomanization,
            focusGlowEnabled = effectiveLyricsFocusGlowEnabled,
            hasTranslation = hasTranslation,
            hasRomanization = hasRomanization,
            showLyricsControlDeck = showLyricsControlDeck,
            onlineLyricsEnabled = onlineLyricsEnabled,
            onDismiss = { lyricsSettingsVisible = false },
            onCloseLyrics = {
                lyricsSettingsVisible = false
                pageScope.launch {
                    pagerState.animateScrollToPage(NowPlayingPage.Cover.ordinal)
                }
            },
            onImportLyrics = onImportLyrics,
            onImportLyricsFont = onImportLyricsFont,
            onLyricsFontFamilyChange = onLyricsFontFamilyChange,
            onLyricsFontScaleChange = onLyricsFontScaleChange,
            onLyricsColorModeChange = onLyricsColorModeChange,
            onLyricsAlignmentChange = onLyricsAlignmentChange,
            onLyricsLineSpacingChange = onLyricsLineSpacingChange,
            onLyricsBackgroundDimChange = onLyricsBackgroundDimChange,
            onLyricsWordHighlightEnabledChange = onLyricsWordHighlightEnabledChange,
            onLyricsWordHighlightIntensityChange = onLyricsWordHighlightIntensityChange,
            onLyricsImmersiveModeChange = onLyricsImmersiveModeChange,
            onLyricsMotionModeChange = onLyricsMotionModeChange,
            onLyricsShowTranslationChange = onLyricsShowTranslationChange,
            onLyricsShowRomanizationChange = onLyricsShowRomanizationChange,
            onLyricsFocusGlowChange = onLyricsFocusGlowChange,
            onShowLyricsControlDeckChange = onShowLyricsControlDeckChange,
            onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
            modifier = Modifier.fillMaxSize(),
        )
        PlaybackSettingsDrawer(
            visible = playbackSettingsVisible,
            status = status,
            onCycleRepeatMode = onCycleRepeatMode,
            onToggleShuffle = onToggleShuffle,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSetSleepTimer = onSetSleepTimer,
            onCancelSleepTimer = onCancelSleepTimer,
            onSetReplayGain = onSetReplayGain,
            onAdjustReplayGainPreamp = onAdjustReplayGainPreamp,
            onSetSkipSilenceEnabled = onSetSkipSilenceEnabled,
            lyricsOffsetMs = readyLyrics?.metadata?.get("user_offset_ms")?.toLongOrNull() ?: 0L,
            onAdjustLyricsOffset = onAdjustLyricsOffset,
            onResetLyricsOffset = onResetLyricsOffset,
            onOpenQueue = onOpenQueue,
            onDismiss = { playbackSettingsVisible = false },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun NowPlayingCoverPage(
    status: EchoPlaybackStatus,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    playbackSettingsExpanded: Boolean,
    onOpenPlaybackSettings: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = status.track

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        NowPlayingTrackInfo(
            title = track?.title ?: "未在播放",
            artist = track?.artist ?: "选择一首歌开始",
            album = track?.album,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
            playbackSettingsExpanded = playbackSettingsExpanded,
            onOpenPlaybackSettings = onOpenPlaybackSettings,
        )

        Spacer(Modifier.height(8.dp))
        NowPlayingFormatInfo(diagnostics = status.diagnostics)

        Spacer(Modifier.height(10.dp))
        NowPlayingScrubber(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = onSeek,
        )

        Spacer(Modifier.height(10.dp))
        NowPlayingControlDock(
            isPlaying = status.isPlaying,
            leadingIcon = Icons.Rounded.Lyrics,
            leadingDescription = "歌词",
            onLeadingAction = onOpenLyrics,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onOpenQueue = onOpenQueue,
        )
        Spacer(Modifier.height(14.dp))
    }
}
@Composable
private fun NowPlayingTopBar(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 38.dp, height = 5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.38f))
                .clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun NowPlayingLyricsPage(
    status: EchoPlaybackStatus,
    lyricsState: EchoLyricsLoadState,
    showLyricsControlDeck: Boolean,
    lyricsFontFamily: FontFamily?,
    lyricsFontMode: String,
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
    importedFontUri: String?,
    onlineLyricsEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    positionMs: Long,
    durationMs: Long,
    onCloseLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onLyricsColorModeChange: (String) -> Unit,
    onLyricsAlignmentChange: (String) -> Unit,
    onLyricsLineSpacingChange: (Float) -> Unit,
    onLyricsBackgroundDimChange: (Float) -> Unit,
    onLyricsWordHighlightEnabledChange: (Boolean) -> Unit,
    onLyricsWordHighlightIntensityChange: (Float) -> Unit,
    onLyricsImmersiveModeChange: (Boolean) -> Unit,
    onLyricsMotionModeChange: (String) -> Unit,
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onLyricsFocusGlowChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    onOpenLyricsSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readyLyrics = (lyricsState as? EchoLyricsLoadState.Ready)?.lyrics
    val lyricAccent = lyricsColorForMode(lyricsColorMode)
    val lyricsDimAlpha by animateFloatAsState(
        targetValue = lyricsBackgroundDim.coerceIn(0f, 0.78f),
        animationSpec = tween(durationMillis = 240, easing = LyricsSettingsMotionEasing),
        label = "lyrics-page-dim",
    )
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = lyricsDimAlpha)),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp, bottom = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (lyricsState) {
                    EchoLyricsLoadState.Idle -> LyricsEmptyState("选择一首歌后显示歌词", onImportLyrics)
                    EchoLyricsLoadState.Loading -> LyricsEmptyState("正在读取本地歌词…")
                    EchoLyricsLoadState.Missing -> LyricsEmptyState("未找到同名歌词", onImportLyrics)
                    is EchoLyricsLoadState.Error -> LyricsEmptyState(lyricsState.message, onImportLyrics)
                    is EchoLyricsLoadState.Ready -> LyricsLineList(
                        lyrics = lyricsState.lyrics,
                        positionMs = positionMs,
                        onSeek = onSeek,
                        lyricsFontFamily = lyricsFontFamily,
                        lyricsFontScale = lyricsFontScale,
                        lyricAccent = lyricAccent,
                        lyricsAlignment = lyricsAlignment,
                        lyricsLineSpacing = lyricsLineSpacing,
                        lyricsWordHighlightEnabled = lyricsWordHighlightEnabled,
                        lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
                        lyricsImmersiveModeEnabled = lyricsImmersiveModeEnabled,
                        lyricsMotionMode = lyricsMotionMode,
                        showTranslation = lyricsShowTranslation,
                        showRomanization = lyricsShowRomanization,
                        focusGlowEnabled = lyricsFocusGlowEnabled,
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
                    animationSpec = tween(durationMillis = 360, easing = LyricsSettingsMotionEasing),
                ) + fadeIn(tween(durationMillis = 220, delayMillis = 40, easing = LyricsSettingsMotionEasing)) +
                    slideInVertically(tween(durationMillis = 360, easing = LyricsSettingsMotionEasing)) { -it / 4 },
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(durationMillis = 240, easing = LyricsSettingsMotionEasing),
                ) + fadeOut(tween(durationMillis = 160, easing = LyricsSettingsMotionEasing)) +
                    slideOutVertically(tween(durationMillis = 240, easing = LyricsSettingsMotionEasing)) { -it / 5 },
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
            NowPlayingScrubber(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
            )
            Spacer(Modifier.height(10.dp))
            NowPlayingControlDock(
                isPlaying = status.isPlaying,
                leadingIcon = Icons.Rounded.Settings,
                leadingDescription = "歌词设置",
                onLeadingAction = onOpenLyricsSettings,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onOpenQueue = onOpenQueue,
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun LyricsSettingsDrawer(
    visible: Boolean,
    lyricsFontMode: String,
    importedFontUri: String?,
    lyricsFontScale: Float,
    lyricsColorMode: String,
    lyricsAlignment: String,
    lyricsLineSpacing: Float,
    lyricsBackgroundDim: Float,
    lyricsWordHighlightEnabled: Boolean,
    lyricsWordHighlightIntensity: Float,
    lyricsImmersiveModeEnabled: Boolean,
    lyricsMotionMode: String,
    lyricAccent: Color,
    showTranslation: Boolean,
    showRomanization: Boolean,
    focusGlowEnabled: Boolean,
    hasTranslation: Boolean,
    hasRomanization: Boolean,
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    onDismiss: () -> Unit,
    onCloseLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onLyricsColorModeChange: (String) -> Unit,
    onLyricsAlignmentChange: (String) -> Unit,
    onLyricsLineSpacingChange: (Float) -> Unit,
    onLyricsBackgroundDimChange: (Float) -> Unit,
    onLyricsWordHighlightEnabledChange: (Boolean) -> Unit,
    onLyricsWordHighlightIntensityChange: (Float) -> Unit,
    onLyricsImmersiveModeChange: (Boolean) -> Unit,
    onLyricsMotionModeChange: (String) -> Unit,
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onLyricsFocusGlowChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = remember { MutableTransitionState(false) }
    drawerState.targetState = visible
    AnimatedVisibility(
        visibleState = drawerState,
        enter = fadeIn(tween(durationMillis = 90, easing = LyricsSettingsMotionEasing)),
        exit = fadeOut(tween(durationMillis = 180, easing = LyricsSettingsMotionEasing)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            AnimatedVisibility(
                visibleState = drawerState,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { it } +
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 360, easing = LyricsSettingsMotionEasing),
                    ) +
                    fadeIn(tween(durationMillis = 260, delayMillis = 35, easing = LyricsSettingsMotionEasing)) +
                    scaleIn(
                        initialScale = 0.965f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                exit = slideOutVertically(tween(durationMillis = 260, easing = LyricsSettingsMotionEasing)) { it } +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 260, easing = LyricsSettingsMotionEasing),
                    ) +
                    fadeOut(tween(durationMillis = 160, easing = LyricsSettingsMotionEasing)) +
                    scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(durationMillis = 260, easing = LyricsSettingsMotionEasing),
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LyricsSettingsPanel(
                    lyricsFontMode = lyricsFontMode,
                    importedFontUri = importedFontUri,
                    lyricsFontScale = lyricsFontScale,
                    lyricsColorMode = lyricsColorMode,
                    lyricsAlignment = lyricsAlignment,
                    lyricsLineSpacing = lyricsLineSpacing,
                    lyricsBackgroundDim = lyricsBackgroundDim,
                    lyricsWordHighlightEnabled = lyricsWordHighlightEnabled,
                    lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
                    lyricsImmersiveModeEnabled = lyricsImmersiveModeEnabled,
                    lyricsMotionMode = lyricsMotionMode,
                    lyricAccent = lyricAccent,
                    showTranslation = showTranslation,
                    showRomanization = showRomanization,
                    focusGlowEnabled = focusGlowEnabled,
                    hasTranslation = hasTranslation,
                    hasRomanization = hasRomanization,
                    showLyricsControlDeck = showLyricsControlDeck,
                    onlineLyricsEnabled = onlineLyricsEnabled,
                    onDismiss = onDismiss,
                    onCloseLyrics = onCloseLyrics,
                    onImportLyrics = onImportLyrics,
                    onImportLyricsFont = onImportLyricsFont,
                    onLyricsFontFamilyChange = onLyricsFontFamilyChange,
                    onLyricsFontScaleChange = onLyricsFontScaleChange,
                    onLyricsColorModeChange = onLyricsColorModeChange,
                    onLyricsAlignmentChange = onLyricsAlignmentChange,
                    onLyricsLineSpacingChange = onLyricsLineSpacingChange,
                    onLyricsBackgroundDimChange = onLyricsBackgroundDimChange,
                    onLyricsWordHighlightEnabledChange = onLyricsWordHighlightEnabledChange,
                    onLyricsWordHighlightIntensityChange = onLyricsWordHighlightIntensityChange,
                    onLyricsImmersiveModeChange = onLyricsImmersiveModeChange,
                    onLyricsMotionModeChange = onLyricsMotionModeChange,
                    onLyricsShowTranslationChange = onLyricsShowTranslationChange,
                    onLyricsShowRomanizationChange = onLyricsShowRomanizationChange,
                    onLyricsFocusGlowChange = onLyricsFocusGlowChange,
                    onShowLyricsControlDeckChange = onShowLyricsControlDeckChange,
                    onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
                )
            }
        }
    }
}

@Composable
private fun LyricsSettingsPanel(
    lyricsFontMode: String,
    importedFontUri: String?,
    lyricsFontScale: Float,
    lyricsColorMode: String,
    lyricsAlignment: String,
    lyricsLineSpacing: Float,
    lyricsBackgroundDim: Float,
    lyricsWordHighlightEnabled: Boolean,
    lyricsWordHighlightIntensity: Float,
    lyricsImmersiveModeEnabled: Boolean,
    lyricsMotionMode: String,
    lyricAccent: Color,
    showTranslation: Boolean,
    showRomanization: Boolean,
    focusGlowEnabled: Boolean,
    hasTranslation: Boolean,
    hasRomanization: Boolean,
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    onDismiss: () -> Unit,
    onCloseLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onLyricsColorModeChange: (String) -> Unit,
    onLyricsAlignmentChange: (String) -> Unit,
    onLyricsLineSpacingChange: (Float) -> Unit,
    onLyricsBackgroundDimChange: (Float) -> Unit,
    onLyricsWordHighlightEnabledChange: (Boolean) -> Unit,
    onLyricsWordHighlightIntensityChange: (Float) -> Unit,
    onLyricsImmersiveModeChange: (Boolean) -> Unit,
    onLyricsMotionModeChange: (String) -> Unit,
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onLyricsFocusGlowChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
) {
    val scale = lyricsFontScale.coerceIn(0.82f, 1.28f)
    val fontFraction = ((scale - 0.82f) / (1.28f - 0.82f)).coerceIn(0f, 1f)
    val spacing = lyricsLineSpacing.coerceIn(0.82f, 1.38f)
    val spacingFraction = ((spacing - 0.82f) / (1.38f - 0.82f)).coerceIn(0f, 1f)
    val dim = lyricsBackgroundDim.coerceIn(0f, 0.78f)
    val dimFraction = (dim / 0.78f).coerceIn(0f, 1f)
    val highlight = lyricsWordHighlightIntensity.coerceIn(0.45f, 1.35f)
    val highlightFraction = ((highlight - 0.45f) / (1.35f - 0.45f)).coerceIn(0f, 1f)
    val dark = LocalEchoDarkTheme.current
    val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val titleColor = if (dark) Color.White else Color(0xFF101722)
    val mutedColor = if (dark) Color.White.copy(alpha = 0.78f) else Color(0xFF4F5C70)
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.58f)
            .navigationBarsPadding()
            .clip(panelShape)
            .background(
                if (dark) {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF292A30).copy(alpha = 0.96f),
                            Color(0xFF202127).copy(alpha = 0.96f),
                            Color(0xFF191A20).copy(alpha = 0.96f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFF7F8FC).copy(alpha = 0.97f),
                            Color(0xFFEDEFF5).copy(alpha = 0.96f),
                        ),
                    )
                },
            )
            .border(
                BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f)),
                panelShape,
            )
            .verticalScroll(scrollState)
            .animateContentSize(tween(durationMillis = 300, easing = LyricsSettingsMotionEasing))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 48.dp, height = 5.dp)
                .clip(CircleShape)
                .background(if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFF253142).copy(alpha = 0.22f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(lyricAccent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = if (dark) lyricAccent else Color(0xFF17202D), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("歌词设置", color = titleColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("字体、颜色和显示方式", color = mutedColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            GlyphButton(
                icon = Icons.Rounded.Close,
                description = "关闭歌词设置",
                touchSize = 40.dp,
                iconSize = 22.dp,
                tint = titleColor,
                background = if (dark) EchoGlassPanel.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.62f),
                onClick = onDismiss,
            )
        }

        LyricsSettingsSection(icon = Icons.Rounded.TextFields, title = "字体", detail = lyricsFontDetail(lyricsFontMode, importedFontUri), enterDelayMillis = 45) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lyricsFontOptions(importedFontUri).forEach { (value, label) ->
                    LyricsChoiceChip(
                        text = label,
                        selected = lyricsFontMode == value,
                        accent = lyricAccent,
                        onClick = {
                            if (value == "imported" && importedFontUri.isNullOrBlank()) {
                                onImportLyricsFont()
                            } else {
                                onLyricsFontFamilyChange(value)
                            }
                        },
                    )
                }
            }
        }

        LyricsSettingsSection(icon = Icons.Rounded.TextFields, title = "对齐", detail = lyricsAlignmentLabel(lyricsAlignment), enterDelayMillis = 90) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LyricsAlignmentOptions.forEach { option ->
                    LyricsChoiceChip(
                        text = option.label,
                        selected = lyricsAlignment == option.value,
                        accent = lyricAccent,
                        onClick = { onLyricsAlignmentChange(option.value) },
                    )
                }
            }
        }

        LyricsSettingsSection(
            icon = Icons.Rounded.FormatSize,
            title = "字号",
            detail = "${(scale * 100f).roundToInt()}%",
            enterDelayMillis = 135,
        ) {
            ThinSlider(
                fraction = fontFraction,
                onValueChange = { fraction ->
                    onLyricsFontScaleChange(0.82f + fraction.coerceIn(0f, 1f) * (1.28f - 0.82f))
                },
                onValueChangeFinished = { fraction ->
                    onLyricsFontScaleChange(0.82f + fraction.coerceIn(0f, 1f) * (1.28f - 0.82f))
                },
                activeColor = lyricAccent,
                inactiveColor = if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFFB8C2D6).copy(alpha = 0.55f),
                thumbColor = Color.White,
            )
        }

        LyricsSettingsSection(icon = Icons.Rounded.ColorLens, title = "颜色", detail = lyricsColorLabel(lyricsColorMode), enterDelayMillis = 180) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                LyricsColorOptions.forEach { option ->
                    LyricsColorSwatch(
                        option = option,
                        selected = option.value == lyricsColorMode,
                        onClick = { onLyricsColorModeChange(option.value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        LyricsSettingsSection(icon = Icons.Rounded.FormatSize, title = "细节", detail = lyricsMotionLabel(lyricsMotionMode), enterDelayMillis = 225) {
            LyricsMiniSliderRow(
                label = "行距",
                valueLabel = "${(spacing * 100f).roundToInt()}%",
                fraction = spacingFraction,
                accent = lyricAccent,
                onValueChange = { fraction ->
                    onLyricsLineSpacingChange(0.82f + fraction.coerceIn(0f, 1f) * (1.38f - 0.82f))
                },
            )
            LyricsMiniSliderRow(
                label = "遮罩浓度",
                valueLabel = "${(dim * 100f).roundToInt()}%",
                fraction = dimFraction,
                accent = lyricAccent,
                onValueChange = { fraction ->
                    onLyricsBackgroundDimChange(fraction.coerceIn(0f, 1f) * 0.78f)
                },
            )
            LyricsMiniSliderRow(
                label = if (lyricsWordHighlightEnabled) "逐字强度" else "逐字强度 关",
                valueLabel = if (lyricsWordHighlightEnabled) "${(highlight * 100f).roundToInt()}%" else "关闭",
                fraction = highlightFraction,
                accent = lyricAccent,
                onValueChange = { fraction ->
                    onLyricsWordHighlightIntensityChange(0.45f + fraction.coerceIn(0f, 1f) * (1.35f - 0.45f))
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LyricsMotionOptions.forEach { option ->
                    LyricsChoiceChip(
                        text = option.label,
                        selected = lyricsMotionMode == option.value,
                        accent = lyricAccent,
                        onClick = { onLyricsMotionModeChange(option.value) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LyricsToggleTile("翻译", showTranslation, lyricAccent, Modifier.weight(1f), available = hasTranslation) {
                onLyricsShowTranslationChange(!showTranslation)
            }
            LyricsToggleTile("罗马音", showRomanization, lyricAccent, Modifier.weight(1f), available = hasRomanization) {
                onLyricsShowRomanizationChange(!showRomanization)
            }
            LyricsToggleTile("强调", focusGlowEnabled, lyricAccent, Modifier.weight(1f)) {
                onLyricsFocusGlowChange(!focusGlowEnabled)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LyricsToggleTile("逐字", lyricsWordHighlightEnabled, lyricAccent, Modifier.weight(1f)) {
                onLyricsWordHighlightEnabledChange(!lyricsWordHighlightEnabled)
            }
            LyricsToggleTile("沉浸", lyricsImmersiveModeEnabled, lyricAccent, Modifier.weight(1f)) {
                onLyricsImmersiveModeChange(!lyricsImmersiveModeEnabled)
            }
            LyricsToggleTile("舞台", lyricsMotionMode == "stage", lyricAccent, Modifier.weight(1f)) {
                onLyricsMotionModeChange(if (lyricsMotionMode == "stage") "smooth" else "stage")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LyricsToolButton(Icons.Rounded.UploadFile, "导入歌词", onImportLyrics, Modifier.weight(1f))
            LyricsToolButton(Icons.Rounded.Settings, "同步工具", { onShowLyricsControlDeckChange(!showLyricsControlDeck) }, Modifier.weight(1f), showLyricsControlDeck)
            LyricsToolButton(Icons.Rounded.Translate, "网络歌词", { onOnlineLyricsEnabledChange(!onlineLyricsEnabled) }, Modifier.weight(1f), onlineLyricsEnabled)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (dark) EchoGlassPanel.copy(alpha = 0.54f) else Color(0xFF101722).copy(alpha = 0.06f))
                .border(if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.Transparent), RoundedCornerShape(18.dp))
                .clickable(onClick = onCloseLyrics)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = titleColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("返回封面页", color = titleColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LyricsSettingsSection(
    icon: ImageVector,
    title: String,
    detail: String,
    enterDelayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val titleColor = if (dark) Color.White else Color(0xFF101722)
    val mutedColor = if (dark) Color.White.copy(alpha = 0.78f) else Color(0xFF4F5C70)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(enterDelayMillis) {
        appeared = false
        delay(enterDelayMillis.toLong())
        appeared = true
    }
    val sectionAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = LyricsSettingsMotionEasing),
        label = "lyrics-section-alpha",
    )
    val sectionOffset by animateDpAsState(
        targetValue = if (appeared) 0.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "lyrics-section-offset",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = sectionOffset)
            .graphicsLayer { alpha = sectionAlpha }
            .animateContentSize(tween(durationMillis = 280, easing = LyricsSettingsMotionEasing))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Text(title, color = titleColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(detail, color = mutedColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        content()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (dark) Color.White.copy(alpha = 0.06f) else Color(0xFF101722).copy(alpha = 0.06f)),
        )
    }
}

@Composable
private fun LyricsPreviewCard(
    lyricAccent: Color,
    lyricsFontScale: Float,
    lyricsAlignment: String,
    lyricsLineSpacing: Float,
    lyricsBackgroundDim: Float,
    lyricsWordHighlightIntensity: Float,
    lyricsMotionMode: String,
) {
    val dark = LocalEchoDarkTheme.current
    val backgroundAlpha by animateFloatAsState(
        targetValue = (0.18f + lyricsBackgroundDim.coerceIn(0f, 0.78f) * 0.62f).coerceIn(0.18f, 0.66f),
        animationSpec = tween(durationMillis = 260, easing = LyricsSettingsMotionEasing),
        label = "lyrics-preview-dim",
    )
    val activeScale by animateFloatAsState(
        targetValue = when (lyricsMotionMode) {
            "stage" -> 1.035f
            "calm" -> 1.0f
            else -> 1.018f
        },
        animationSpec = tween(durationMillis = 360, easing = LyricsSettingsMotionEasing),
        label = "lyrics-preview-scale",
    )
    val lineGap by animateDpAsState(
        targetValue = (7f * lyricsLineSpacing.coerceIn(0.82f, 1.38f)).dp,
        animationSpec = tween(durationMillis = 260, easing = LyricsSettingsMotionEasing),
        label = "lyrics-preview-gap",
    )
    val highlightAlpha = (0.54f + lyricsWordHighlightIntensity.coerceIn(0.45f, 1.35f) * 0.30f).coerceIn(0.58f, 0.94f)
    val textAlign = lyricsTextAlign(lyricsAlignment)
    val horizontalAlignment = lyricsHorizontalAlignment(lyricsAlignment)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        lyricAccent.copy(alpha = if (dark) backgroundAlpha * 0.34f else backgroundAlpha * 0.22f),
                        if (dark) EchoGlassInk.copy(alpha = backgroundAlpha) else Color.White.copy(alpha = 0.62f),
                    ),
                ),
            )
            .border(if (dark) echoDarkGlassBorder(true) else BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(lineGap),
    ) {
        AnimatedContent(
            targetState = lyricsMotionMode,
            transitionSpec = {
                (fadeIn(tween(180, easing = LyricsSettingsMotionEasing)) +
                    slideInVertically(tween(280, easing = LyricsSettingsMotionEasing)) { it / 5 }) togetherWith
                    fadeOut(tween(120, easing = LyricsSettingsMotionEasing))
            },
            label = "lyrics-preview-motion",
        ) { mode ->
            Text(
                text = when (mode) {
                    "stage" -> "每一句都贴着节拍浮起来"
                    "calm" -> "歌词安静地停在画面中央"
                    else -> "歌词随着播放自然呼吸"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = activeScale
                        scaleY = activeScale
                    },
                color = lyricAccent.copy(alpha = highlightAlpha),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (20f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                    lineHeight = (27f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                    shadow = Shadow(
                        color = Color.Transparent,
                    ),
                ),
                fontWeight = FontWeight.ExtraBold,
                textAlign = textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "翻译 / 罗马音会在当前歌词包含对应数据时显示",
            modifier = Modifier.fillMaxWidth(),
            color = if (dark) Color.White.copy(alpha = 0.68f) else Color(0xFF4F5C70),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LyricsMiniSliderRow(
    label: String,
    valueLabel: String,
    fraction: Float,
    accent: Color,
    onValueChange: (Float) -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (dark) Color.White.copy(alpha = 0.92f) else Color(0xFF253142), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, color = if (dark) Color.White.copy(alpha = 0.70f) else Color(0xFF4F5C70), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        ThinSlider(
            fraction = fraction,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChange,
            activeColor = accent,
            inactiveColor = if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFFB8C2D6).copy(alpha = 0.55f),
            thumbColor = Color.White,
        )
    }
}

@Composable
private fun LyricsChoiceChip(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            accent.copy(alpha = if (dark) 0.20f else 0.18f)
        } else {
            if (dark) EchoGlassPanel.copy(alpha = 0.46f) else Color.White.copy(alpha = 0.56f)
        },
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "lyrics-choice-container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            accent.copy(alpha = 0.78f)
        } else {
            if (dark) EchoDarkGlassBorder else Color.White.copy(alpha = 0.68f)
        },
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "lyrics-choice-border",
    )
    val chipScale by animateFloatAsState(
        targetValue = if (selected) 1.025f else 1f,
        animationSpec = tween(durationMillis = 220, easing = LyricsSettingsMotionEasing),
        label = "lyrics-choice-scale",
    )
    val dotSize by animateDpAsState(
        targetValue = if (selected) 15.dp else 13.dp,
        animationSpec = tween(durationMillis = 220, easing = LyricsSettingsMotionEasing),
        label = "lyrics-choice-dot",
    )
    Row(
        modifier = Modifier
            .height(46.dp)
            .widthIn(min = 76.dp)
            .graphicsLayer {
                scaleX = chipScale
                scaleY = chipScale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(
                BorderStroke(1.dp, borderColor),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(if (selected) accent else Color.Transparent)
                .border(
                    BorderStroke(1.5.dp, if (selected) accent else if (dark) Color.White.copy(alpha = 0.52f) else Color(0xFF253142).copy(alpha = 0.42f)),
                    CircleShape,
                ),
        )
        Text(
            text = text,
            color = if (dark) Color.White else Color(0xFF101722),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LyricsColorSwatch(
    option: LyricsColorOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEchoDarkTheme.current
    val ringColor by animateColorAsState(
        targetValue = if (selected) option.color else if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFF101722).copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "lyrics-palette-ring",
    )
    val swatchScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(durationMillis = 240, easing = LyricsSettingsMotionEasing),
        label = "lyrics-palette-scale",
    )
    Column(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(top = 5.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = swatchScale
                    scaleY = swatchScale
                }
                .clip(CircleShape)
                .border(BorderStroke(if (selected) 2.5.dp else 1.dp, ringColor), CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(option.color)
                    .border(BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f)), CircleShape),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            option.label,
            color = if (selected) option.color else if (dark) Color.White.copy(alpha = 0.74f) else Color(0xFF253142),
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun LyricsToggleTile(
    title: String,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    available: Boolean = true,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val active = enabled && available
    val tileColor by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = 0.28f) else if (dark) EchoGlassPanel.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.48f),
        animationSpec = tween(durationMillis = 220, easing = LyricsSettingsMotionEasing),
        label = "lyrics-toggle-color",
    )
    val tileScale by animateFloatAsState(
        targetValue = if (active) 1.02f else 1f,
        animationSpec = tween(durationMillis = 220, easing = LyricsSettingsMotionEasing),
        label = "lyrics-toggle-scale",
    )
    Column(
        modifier = modifier
            .height(66.dp)
            .graphicsLayer {
                scaleX = tileScale
                scaleY = tileScale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(tileColor)
            .border(
                BorderStroke(1.dp, if (active) accent.copy(alpha = 0.38f) else if (dark) EchoDarkGlassBorder else Color.White.copy(alpha = 0.66f)),
                RoundedCornerShape(18.dp),
            )
            .then(if (available) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (available) 1f else 0.56f)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = if (dark) Color.White else Color(0xFF253142), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        Text(
            when {
                !available -> "当前无数据"
                enabled -> "开启"
                else -> "关闭"
            },
            color = if (dark) Color.White.copy(alpha = 0.78f) else Color(0xFF4F5C70),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LyricsToolButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val dark = LocalEchoDarkTheme.current
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            Color.White.copy(alpha = if (dark) 0.22f else 0.12f)
        } else {
            if (dark) EchoGlassPanel.copy(alpha = 0.56f) else Color.White.copy(alpha = 0.54f)
        },
        animationSpec = tween(durationMillis = 220, easing = LyricsSettingsMotionEasing),
        label = "lyrics-tool-container",
    )
    val toolScale by animateFloatAsState(
        targetValue = if (selected) 1.025f else 1f,
        animationSpec = tween(durationMillis = 220, easing = LyricsSettingsMotionEasing),
        label = "lyrics-tool-scale",
    )
    Row(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = toolScale
                scaleY = toolScale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .border(if (dark) echoDarkGlassBorder(selected) else BorderStroke(1.dp, Color.White.copy(alpha = 0.68f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) Color.White.copy(alpha = 0.92f) else Color(0xFF253142), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(title, color = if (dark) Color.White.copy(alpha = 0.96f) else Color(0xFF253142), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun lyricsColorForMode(mode: String): Color =
    LyricsColorOptions.firstOrNull { it.value == mode }?.color ?: Color.White

private fun lyricsColorLabel(mode: String): String =
    LyricsColorOptions.firstOrNull { it.value == mode }?.label ?: "白"

private fun lyricsAlignmentLabel(mode: String): String =
    LyricsAlignmentOptions.firstOrNull { it.value == mode }?.label ?: "居中"

private fun lyricsMotionLabel(mode: String): String =
    LyricsMotionOptions.firstOrNull { it.value == mode }?.label ?: "顺滑"

private fun lyricsLayoutDetail(alignment: String, spacing: Float): String =
    "${lyricsAlignmentLabel(alignment)} / ${(spacing.coerceIn(0.82f, 1.38f) * 100f).roundToInt()}%"

private fun lyricsTextAlign(alignment: String): TextAlign =
    when (alignment) {
        "start" -> TextAlign.Start
        else -> TextAlign.Center
    }

private fun lyricsHorizontalAlignment(alignment: String): Alignment.Horizontal =
    when (alignment) {
        "start" -> Alignment.Start
        else -> Alignment.CenterHorizontally
    }

private fun lyricsMotionIntensity(mode: String): Float =
    when (mode) {
        "calm" -> 0.35f
        "stage" -> 1.0f
        else -> 0.68f
    }

private fun lyricsFontOptions(importedFontUri: String?): List<Pair<String, String>> = buildList {
    add("system" to "系统")
    add("serif" to "衬线")
    add("monospace" to "等宽")
    add("imported" to if (importedFontUri.isNullOrBlank()) "导入" else "导入")
}

private fun lyricsFontDetail(mode: String, importedFontUri: String?): String =
    when (mode) {
        "outfit" -> "系统字体"
        "system" -> "系统字体"
        "serif" -> "系统衬线"
        "monospace" -> "系统等宽"
        "imported" -> importedFontUri?.substringAfterLast('/')?.takeLast(18)?.let { "导入 $it" } ?: "选择字体文件"
        else -> "系统字体"
    }

@Composable
private fun LyricsLineList(
    lyrics: EchoLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    lyricsFontFamily: FontFamily?,
    lyricsFontScale: Float,
    lyricAccent: Color,
    lyricsAlignment: String,
    lyricsLineSpacing: Float,
    lyricsWordHighlightEnabled: Boolean,
    lyricsWordHighlightIntensity: Float,
    lyricsImmersiveModeEnabled: Boolean,
    lyricsMotionMode: String,
    showTranslation: Boolean,
    showRomanization: Boolean,
    focusGlowEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val synced = lyrics.isSynced
        val activeIndex = remember(lyrics, positionMs, synced) {
            if (synced) {
                lyrics.lines.indexOfLast { line -> line.startMs <= positionMs + 80L }
                    .coerceAtLeast(0)
            } else {
                -1
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(activeIndex, lyrics.lines.size, synced) {
            if (synced && lyrics.lines.isNotEmpty()) {
                listState.animateScrollToItem(activeIndex.coerceIn(0, lyrics.lines.lastIndex))
            }
        }
        val scale = lyricsFontScale.coerceIn(0.82f, 1.28f)
        val spacing = lyricsLineSpacing.coerceIn(0.82f, 1.38f)
        val textAlign = lyricsTextAlign(lyricsAlignment)
        val horizontalAlignment = lyricsHorizontalAlignment(lyricsAlignment)
        val motionIntensity = lyricsMotionIntensity(lyricsMotionMode)
        val immersive = lyricsImmersiveModeEnabled && synced
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = maxHeight * 0.54f,
                bottom = maxHeight * 0.46f,
            ),
            verticalArrangement = Arrangement.spacedBy((18f * spacing).dp),
        ) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "${line.startMs}-$index-${line.text}" },
            ) { index, line ->
                val active = synced && index == activeIndex
                val focusDistance = if (activeIndex >= 0) abs(index - activeIndex).coerceAtMost(4) else 1
                val seekable = synced && line.startMs >= 0L
                val primaryAlpha = when (focusDistance) {
                    0 -> 1f
                    else -> if (immersive) 0.08f else when (focusDistance) {
                        1 -> 0.78f
                        2 -> 0.58f
                        3 -> 0.40f
                        else -> 0.28f
                    }
                }
                val secondaryAlpha = when (focusDistance) {
                    0 -> 0.84f
                    else -> if (immersive) 0f else when (focusDistance) {
                        1 -> 0.64f
                        2 -> 0.48f
                        3 -> 0.34f
                        else -> 0.24f
                    }
                }
                val backgroundAlpha = when (focusDistance) {
                    0 -> if (immersive) 0.08f else 0f
                    else -> 0f
                }
                val lineBlur by animateDpAsState(
                    targetValue = if (immersive && !active) 10.dp else 0.dp,
                    animationSpec = tween(durationMillis = 260, easing = LyricsSettingsMotionEasing),
                    label = "lyrics-line-blur",
                )
                val animatedPrimaryAlpha by animateFloatAsState(
                    targetValue = primaryAlpha,
                    animationSpec = tween(durationMillis = 220, easing = LyricsSettingsMotionEasing),
                    label = "lyrics-line-alpha",
                )
                val lineScale by animateFloatAsState(
                    targetValue = if (active) 1f + 0.036f * motionIntensity else 1f,
                    animationSpec = tween(durationMillis = 300, easing = LyricsSettingsMotionEasing),
                    label = "lyrics-line-scale",
                )
                val lineOffset by animateDpAsState(
                    targetValue = if (active) (-5f * motionIntensity).dp else 0.dp,
                    animationSpec = tween(durationMillis = 300, easing = LyricsSettingsMotionEasing),
                    label = "lyrics-line-offset",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = lineOffset)
                        .blur(lineBlur)
                        .graphicsLayer {
                            scaleX = lineScale
                            scaleY = lineScale
                        }
                        .background(
                            lyricAccent.copy(alpha = backgroundAlpha),
                            RoundedCornerShape(18.dp),
                        )
                        .then(
                            if (seekable) {
                                Modifier.clickable { onSeek(line.startMs) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalAlignment = horizontalAlignment,
                    verticalArrangement = Arrangement.spacedBy((5f * spacing).dp),
                ) {
                    val activeShadow = if (active && focusGlowEnabled) {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.22f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f,
                        )
                    } else {
                        Shadow(
                            color = Color.Transparent,
                        )
                    }
                    Text(
                        text = line.displayText(
                            active = active,
                            positionMs = positionMs,
                            activeColor = lyricAccent,
                            highlightEnabled = lyricsWordHighlightEnabled,
                            highlightIntensity = lyricsWordHighlightIntensity,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        color = lyricAccent.copy(alpha = animatedPrimaryAlpha),
                        style = if (active) {
                            MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = lyricsFontFamily,
                                fontSize = (31f * scale).sp,
                                lineHeight = (40f * scale * spacing).sp,
                                shadow = activeShadow,
                            )
                        } else {
                            MaterialTheme.typography.titleLarge.copy(
                                fontFamily = lyricsFontFamily,
                                fontSize = (22f * scale).sp,
                                lineHeight = (30f * scale * spacing).sp,
                            )
                        },
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                        textAlign = textAlign,
                        maxLines = if (active) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    line.translation?.takeIf { showTranslation && it.isNotBlank() }?.let { translation ->
                        Text(
                            text = translation,
                            modifier = Modifier.fillMaxWidth(),
                            color = lyricAccent.copy(alpha = secondaryAlpha),
                            style = if (active) {
                                MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = lyricsFontFamily,
                                    fontSize = (15f * scale).sp,
                                    lineHeight = (22f * scale * spacing).sp,
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = lyricsFontFamily,
                                    fontSize = (13f * scale).sp,
                                    lineHeight = (20f * scale * spacing).sp,
                                )
                            },
                            fontWeight = FontWeight.SemiBold,
                            textAlign = textAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    line.romanization?.takeIf { showRomanization && it.isNotBlank() }?.let { romanization ->
                        Text(
                            text = romanization,
                            modifier = Modifier.fillMaxWidth(),
                            color = lyricAccent.copy(alpha = secondaryAlpha * 0.92f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = lyricsFontFamily,
                                fontSize = (12f * scale).sp,
                                lineHeight = (18f * scale * spacing).sp,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = textAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun EchoLyricLine.displayText(
    active: Boolean,
    positionMs: Long,
    activeColor: Color,
    highlightEnabled: Boolean,
    highlightIntensity: Float,
) =
    if (!active || !highlightEnabled || words.isEmpty()) {
        buildAnnotatedString { append(text) }
    } else {
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                val nextStartMs = words.getOrNull(index + 1)?.startMs
                val endMs = word.endMs ?: nextStartMs ?: this@displayText.endMs ?: Long.MAX_VALUE
                val isCurrentWord = positionMs in word.startMs until endMs
                val mutedAlpha = (0.56f + 0.18f * highlightIntensity.coerceIn(0.45f, 1.35f)).coerceIn(0.62f, 0.82f)
                val color = if (isCurrentWord) activeColor else activeColor.copy(alpha = mutedAlpha)
                pushStyle(
                    SpanStyle(
                        color = color,
                        fontWeight = if (isCurrentWord) FontWeight.ExtraBold else FontWeight.Bold,
                    ),
                )
                append(word.text)
                pop()
            }
        }
    }

@Composable
private fun LyricsEmptyState(
    message: String,
    onImportLyrics: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.Lyrics,
            contentDescription = null,
            tint = OnArtMuted,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = message,
            color = OnArtMuted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        onImportLyrics?.let { onClick ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(OnArtChip)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.UploadFile,
                    contentDescription = null,
                    tint = OnArt,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "导入歌词",
                    color = OnArt,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LyricsControlDeck(
    lyrics: EchoLyrics,
    onImportLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
) {
    val userOffsetMs = lyrics.metadata["user_offset_ms"]?.toLongOrNull() ?: 0L
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        EchoGlassPanel.copy(alpha = 0.54f),
                        EchoGlassInk.copy(alpha = 0.66f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoDarkGlassBorder), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormatChip(text = "Lyrics", highlight = true)
                FormatChip(text = lyrics.format.label(), highlight = false)
                lyrics.sourceLabel?.takeIf { it.isNotBlank() }?.let { source ->
                    Text(
                        text = source,
                        color = OnArtMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            GlyphButton(
                icon = Icons.Rounded.UploadFile,
                description = "更换歌词",
                touchSize = 34.dp,
                iconSize = 20.dp,
                tint = OnArtMuted,
                background = Color.Transparent,
                onClick = onImportLyrics,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormatChip(text = "Sync", highlight = true)
                Text(
                    text = formatLyricsOffset(userOffsetMs),
                    color = OnArtMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(
                    icon = Icons.Rounded.KeyboardDoubleArrowLeft,
                    description = "歌词提前 0.25 秒",
                    touchSize = 34.dp,
                    iconSize = 21.dp,
                    tint = OnArtMuted,
                    background = Color.Transparent,
                    onClick = { onAdjustLyricsOffset(-250L) },
                )
                GlyphButton(
                    icon = Icons.Rounded.RestartAlt,
                    description = "重置歌词偏移",
                    touchSize = 34.dp,
                    iconSize = 20.dp,
                    tint = if (userOffsetMs == 0L) OnArtFaint else OnArtMuted,
                    background = Color.Transparent,
                    onClick = onResetLyricsOffset,
                )
                GlyphButton(
                    icon = Icons.Rounded.KeyboardDoubleArrowRight,
                    description = "歌词延后 0.25 秒",
                    touchSize = 34.dp,
                    iconSize = 21.dp,
                    tint = OnArtMuted,
                    background = Color.Transparent,
                    onClick = { onAdjustLyricsOffset(250L) },
                )
            }
        }
    }
}

private fun EchoLyricsFormat.label(): String = when (this) {
    EchoLyricsFormat.Lrc -> "LRC"
    EchoLyricsFormat.EnhancedLrc -> "Enhanced LRC"
    EchoLyricsFormat.Ttml -> "TTML"
    EchoLyricsFormat.Srt -> "SRT"
    EchoLyricsFormat.Vtt -> "WebVTT"
    EchoLyricsFormat.Ass -> "ASS/SSA"
    EchoLyricsFormat.Yrc -> "YRC"
    EchoLyricsFormat.Qrc -> "QRC"
    EchoLyricsFormat.Krc -> "KRC"
    EchoLyricsFormat.PlainText -> "Plain"
}

private fun formatLyricsOffset(offsetMs: Long): String {
    val sign = when {
        offsetMs > 0L -> "+"
        offsetMs < 0L -> "-"
        else -> ""
    }
    val seconds = kotlin.math.abs(offsetMs) / 1000f
    return "$sign${"%.2f".format(seconds)}s"
}

@Composable
private fun NowPlayingTrackInfo(
    title: String,
    artist: String,
    album: String?,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    playbackSettingsExpanded: Boolean,
    onOpenPlaybackSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = OnArt,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    artist,
                    modifier = Modifier.clickable(onClick = onOpenArtist),
                    color = OnArtMuted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                album?.takeIf { it.isNotBlank() }?.let { value ->
                    Text(
                        value,
                        modifier = Modifier.clickable(onClick = onOpenAlbum),
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(
                    icon = Icons.Rounded.StarBorder,
                    description = "收藏",
                    touchSize = 40.dp,
                    iconSize = 21.dp,
                    tint = Color.White.copy(alpha = 0.88f),
                    background = Color.White.copy(alpha = 0.11f),
                    onClick = {},
                )
                GlyphButton(
                    icon = Icons.Rounded.MoreHoriz,
                    description = if (playbackSettingsExpanded) "收起播放设置" else "展开播放设置",
                    touchSize = 40.dp,
                    iconSize = 21.dp,
                    tint = if (playbackSettingsExpanded) Color.White else Color.White.copy(alpha = 0.88f),
                    background = if (playbackSettingsExpanded) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.11f),
                    border = if (playbackSettingsExpanded) Color.White.copy(alpha = 0.38f) else Color.Transparent,
                    onClick = onOpenPlaybackSettings,
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsDrawer(
    visible: Boolean,
    status: EchoPlaybackStatus,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetPlaybackSpeed: (Float, Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onAdjustReplayGainPreamp: (Float) -> Unit,
    onSetSkipSilenceEnabled: (Boolean) -> Unit,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = remember { MutableTransitionState(false) }
    drawerState.targetState = visible
    AnimatedVisibility(
        visibleState = drawerState,
        enter = fadeIn(tween(durationMillis = 90, easing = LyricsSettingsMotionEasing)),
        exit = fadeOut(tween(durationMillis = 180, easing = LyricsSettingsMotionEasing)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            AnimatedVisibility(
                visibleState = drawerState,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { it } +
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 360, easing = LyricsSettingsMotionEasing),
                    ) +
                    fadeIn(tween(durationMillis = 260, delayMillis = 35, easing = LyricsSettingsMotionEasing)) +
                    scaleIn(
                        initialScale = 0.965f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                exit = slideOutVertically(tween(durationMillis = 260, easing = LyricsSettingsMotionEasing)) { it } +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 260, easing = LyricsSettingsMotionEasing),
                    ) +
                    fadeOut(tween(durationMillis = 160, easing = LyricsSettingsMotionEasing)) +
                    scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(durationMillis = 260, easing = LyricsSettingsMotionEasing),
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                PlaybackSettingsPanel(
                    status = status,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    onSetSleepTimer = onSetSleepTimer,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onSetReplayGain = onSetReplayGain,
                    onAdjustReplayGainPreamp = onAdjustReplayGainPreamp,
                    onSetSkipSilenceEnabled = onSetSkipSilenceEnabled,
                    lyricsOffsetMs = lyricsOffsetMs,
                    onAdjustLyricsOffset = onAdjustLyricsOffset,
                    onResetLyricsOffset = onResetLyricsOffset,
                    onOpenQueue = onOpenQueue,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsPanel(
    status: EchoPlaybackStatus,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetPlaybackSpeed: (Float, Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onAdjustReplayGainPreamp: (Float) -> Unit,
    onSetSkipSilenceEnabled: (Boolean) -> Unit,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nightcore = isNightcorePlayback(status)
    val dark = LocalEchoDarkTheme.current
    val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val titleColor = if (dark) Color.White else Color(0xFF101722)
    val mutedColor = if (dark) Color.White.copy(alpha = 0.76f) else Color(0xFF4F5C70)
    val accentColor = Color(0xFF9ED8FF)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .navigationBarsPadding()
            .clip(panelShape)
            .background(
                if (dark) {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF292A30).copy(alpha = 0.98f),
                            Color(0xFF202127).copy(alpha = 0.98f),
                            Color(0xFF191A20).copy(alpha = 0.98f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFF7F8FC).copy(alpha = 0.98f),
                            Color(0xFFEDEFF5).copy(alpha = 0.98f),
                        ),
                    )
                },
            )
            .border(
                BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f)),
                panelShape,
            )
            .verticalScroll(rememberScrollState())
            .animateContentSize(tween(durationMillis = 300, easing = LyricsSettingsMotionEasing))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 48.dp, height = 5.dp)
                .clip(CircleShape)
                .background(if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFF253142).copy(alpha = 0.22f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "播放设置",
                    color = titleColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    playbackSettingsSummary(status),
                    color = mutedColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlyphButton(
                icon = Icons.Rounded.Close,
                description = "关闭播放设置",
                touchSize = 42.dp,
                iconSize = 22.dp,
                tint = titleColor,
                background = Color.Transparent,
                onClick = onDismiss,
            )
        }
        PlaybackSettingsLabel(
            text = if (status.sleepTimerRemainingMs > 0L) {
                "睡眠定时 · ${formatSleepTimerRemaining(status.sleepTimerRemainingMs)}"
            } else {
                "睡眠定时"
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PlaybackSpeedChip(
                text = "关闭",
                selected = status.sleepTimerRemainingMs <= 0L,
                onClick = onCancelSleepTimer,
            )
            SleepTimerOptions.forEach { minutes ->
                PlaybackSpeedChip(
                    text = "${minutes}m",
                    selected = false,
                    onClick = { onSetSleepTimer(minutes) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackSettingButton(
                icon = if (status.repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                title = repeatModeLabel(status.repeatMode),
                selected = status.repeatMode != EchoRepeatMode.Off,
                onClick = onCycleRepeatMode,
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.Shuffle,
                title = if (status.shuffleEnabled) "随机开启" else "顺序播放",
                selected = status.shuffleEnabled,
                onClick = onToggleShuffle,
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "队列",
                selected = false,
                onClick = onOpenQueue,
                modifier = Modifier.weight(1f),
            )
        }
        PlaybackSettingsLabel(text = "变速")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackSettingButton(
                icon = Icons.Rounded.PlayArrow,
                title = "普通变速",
                selected = !nightcore,
                onClick = { onSetPlaybackSpeed(status.playbackSpeed, false) },
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.StarBorder,
                title = "Nightcore",
                selected = nightcore,
                onClick = { onSetPlaybackSpeed(status.playbackSpeed.coerceAtLeast(1.25f), true) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PlaybackSpeedOptions.forEach { speed ->
                PlaybackSpeedChip(
                    text = playbackSpeedLabel(speed),
                    selected = abs(status.playbackSpeed - speed) < 0.01f,
                    onClick = { onSetPlaybackSpeed(speed, nightcore) },
                )
            }
        }
        PlaybackSettingsLabel(text = "ReplayGain")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackSettingButton(
                icon = Icons.Rounded.GraphicEq,
                title = if (status.replayGainEnabled) "已启用" else "未启用",
                selected = status.replayGainEnabled,
                onClick = { onSetReplayGain(!status.replayGainEnabled, status.replayGainPreampDb) },
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.KeyboardDoubleArrowLeft,
                title = "-3dB",
                selected = false,
                onClick = { onAdjustReplayGainPreamp(-3f) },
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.KeyboardDoubleArrowRight,
                title = "+3dB",
                selected = false,
                onClick = { onAdjustReplayGainPreamp(3f) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "标签 ${status.replayGainTrackGainDb?.let(::formatReplayGainDb) ?: "未读取"} · 预增益 ${formatReplayGainDb(status.replayGainPreampDb)}",
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackSettingButton(
                icon = Icons.Rounded.MoreHoriz,
                title = if (status.skipSilenceEnabled) "跳过静音开" else "跳过静音关",
                selected = status.skipSilenceEnabled,
                onClick = { onSetSkipSilenceEnabled(!status.skipSilenceEnabled) },
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.RestartAlt,
                title = "歌词 ${formatLyricsOffset(lyricsOffsetMs)}",
                selected = lyricsOffsetMs != 0L,
                onClick = onResetLyricsOffset,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackSettingButton(
                icon = Icons.Rounded.KeyboardDoubleArrowLeft,
                title = "-0.5s",
                selected = false,
                onClick = { onAdjustLyricsOffset(-500L) },
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.KeyboardDoubleArrowRight,
                title = "+0.5s",
                selected = false,
                onClick = { onAdjustLyricsOffset(500L) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaybackSettingsLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.70f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun PlaybackSettingButton(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "playback-setting-container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "playback-setting-border",
    )
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = Color.White.copy(alpha = if (selected) 0.96f else 0.76f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            title,
            color = Color.White.copy(alpha = if (selected) 0.98f else 0.78f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackSpeedChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "playback-speed-chip-container",
    )
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(
                BorderStroke(1.dp, if (selected) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.10f)),
                RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (selected) 0.96f else 0.72f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun playbackSettingsSummary(status: EchoPlaybackStatus): String {
    val diagnostics = status.diagnostics
    val format = buildList {
        diagnostics.codec?.let { add(it) }
        diagnostics.sampleRateHz?.takeIf { it > 0 }?.let { add(formatSampleRate(it)) }
        diagnostics.bitDepth?.takeIf { it > 0 }?.let { add("${it}bit") }
    }.joinToString(" · ").ifBlank { "等待音频信息" }
    val output = if (diagnostics.usbDeviceName != null) "USB 输出" else "系统输出"
    val mode = if (isNightcorePlayback(status)) "Nightcore 变调" else "普通变速"
    val silence = if (status.skipSilenceEnabled) "跳过静音" else null
    return listOfNotNull(format, output, mode, silence).joinToString(" · ")
}

private fun isNightcorePlayback(status: EchoPlaybackStatus): Boolean =
    status.playbackSpeed > 1.01f && abs(status.playbackPitch - status.playbackSpeed) < 0.01f

private fun playbackSpeedLabel(speed: Float): String {
    val rounded = (speed * 100f).roundToInt() / 100f
    return if (abs(rounded - rounded.toInt()) < 0.01f) {
        "${rounded.toInt()}x"
    } else {
        "${"%.2f".format(rounded).trimEnd('0').trimEnd('.')}x"
    }
}

private fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

private fun formatReplayGainDb(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    val sign = if (rounded > 0f) "+" else ""
    return if (abs(rounded - rounded.toInt()) < 0.01f) {
        "$sign${rounded.toInt()}dB"
    } else {
        "$sign${"%.1f".format(rounded)}dB"
    }
}

private fun repeatModeLabel(mode: EchoRepeatMode): String = when (mode) {
    EchoRepeatMode.Off -> "循环关闭"
    EchoRepeatMode.All -> "全部循环"
    EchoRepeatMode.One -> "单曲循环"
}

@Composable
private fun NowPlayingFormatInfo(diagnostics: EchoPlaybackDiagnostics) {
    val chips = buildList {
        diagnostics.codec?.let { add(it) }
        diagnostics.sampleRateHz?.takeIf { it > 0 }?.let { add(formatSampleRate(it)) }
        diagnostics.bitDepth?.takeIf { it > 0 }?.let { add("${it}bit") }
        diagnostics.channelCount?.takeIf { it > 0 }?.let { add(channelLabel(it)) }
    }
    val bitrateKbps = diagnostics.bitrate?.takeIf { it > 0 }?.let { it / 1000 }
    if (chips.isEmpty() && bitrateKbps == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEachIndexed { index, label ->
            FormatChip(text = label, highlight = index == 0)
        }
        bitrateKbps?.let { kbps ->
            Text(
                "$kbps kbps",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun FormatChip(text: String, highlight: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlight) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (highlight) Color.White.copy(alpha = 0.98f) else Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatSampleRate(hz: Int): String {
    val khzTimes10 = (hz + 50) / 100
    val whole = khzTimes10 / 10
    val frac = khzTimes10 % 10
    return if (frac == 0) "${whole}kHz" else "$whole.${frac}kHz"
}

private fun channelLabel(channels: Int): String = when (channels) {
    1 -> "Mono"
    2 -> "2CH"
    else -> "${channels}CH"
}

@Composable
private fun NowPlayingScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val liveFraction = progressFraction(positionMs, durationMs)
    val shown = scrubFraction ?: liveFraction
    val currentMs = if (durationMs > 0L) (shown * durationMs).toLong() else positionMs
    val remainingMs = (durationMs - currentMs).coerceAtLeast(0L)

    Column(Modifier.fillMaxWidth()) {
        ThinSlider(
            fraction = shown,
            onValueChange = { scrubFraction = it },
            onValueChangeFinished = { fraction ->
                if (durationMs > 0L) {
                    onSeek((fraction * durationMs).toLong())
                }
                scrubFraction = null
            },
            trackHeight = 4.dp,
            thumbSize = 10.dp,
            inactiveColor = Color.White.copy(alpha = 0.18f),
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(currentMs),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "-" + formatDuration(remainingMs),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NowPlayingControlDock(
    isPlaying: Boolean,
    leadingIcon: ImageVector,
    leadingDescription: String,
    onLeadingAction: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            icon = leadingIcon,
            description = leadingDescription,
            touchSize = 44.dp,
            iconSize = 24.dp,
            tint = Color.White.copy(alpha = 0.78f),
            background = Color.Transparent,
            onClick = onLeadingAction,
        )
        GlyphButton(
            icon = Icons.Rounded.KeyboardDoubleArrowLeft,
            description = "上一首",
            touchSize = 54.dp,
            iconSize = 38.dp,
            tint = OnArt,
            background = Color.Transparent,
            onClick = onPrevious,
        )
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayPause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "播放或暂停",
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
        GlyphButton(
            icon = Icons.Rounded.KeyboardDoubleArrowRight,
            description = "下一首",
            touchSize = 54.dp,
            iconSize = 38.dp,
            tint = OnArt,
            background = Color.Transparent,
            onClick = onNext,
        )
        GlyphButton(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            description = "播放队列",
            touchSize = 44.dp,
            iconSize = 24.dp,
            tint = Color.White.copy(alpha = 0.78f),
            background = Color.Transparent,
            onClick = onOpenQueue,
        )
    }
}

/**
 * 纤细圆角滑条（Apple Music 风）：细轨道 + 小圆点，支持拖动与点按定位。
 */
@Composable
private fun ThinSlider(
    fraction: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 6.dp,
    thumbSize: Dp = 13.dp,
    activeColor: Color = Color.White,
    inactiveColor: Color = OnArtFaint,
    thumbColor: Color = Color.White,
) {
    val f = fraction.coerceIn(0f, 1f)
    fun fractionAt(x: Float, width: Int): Float =
        if (width > 0) (x / width).coerceIn(0f, 1f) else f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val target = fractionAt(offset.x, size.width)
                    onValueChange(target)
                    onValueChangeFinished(target)
                }
            }
            .pointerInput(Unit) {
                var latestFraction = f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latestFraction = fractionAt(offset.x, size.width)
                        onValueChange(latestFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latestFraction = fractionAt(change.position.x, size.width)
                        onValueChange(latestFraction)
                    },
                    onDragEnd = { onValueChangeFinished(latestFraction) },
                    onDragCancel = { onValueChangeFinished(latestFraction) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(inactiveColor),
        )
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(trackHeight)
                .clip(CircleShape)
                .background(activeColor),
        )
        Box(
            Modifier
                .offset { IntOffset(((maxWidth.toPx() - thumbSize.toPx()) * f).roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
private fun GlyphButton(
    icon: ImageVector,
    description: String,
    touchSize: Dp,
    iconSize: Dp,
    tint: Color,
    background: Color,
    border: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(touchSize)
            .clip(CircleShape)
            .background(background)
            .then(if (border.alpha > 0f) Modifier.border(BorderStroke(1.dp, border), CircleShape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

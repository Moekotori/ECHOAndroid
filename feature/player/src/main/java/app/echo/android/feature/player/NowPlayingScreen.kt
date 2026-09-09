package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource

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
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import app.echo.android.model.i18n.echoText
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
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
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.BlurredArtworkBackground
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoLiquidGlass
import app.echo.android.design.echoAccentColor
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassNight
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoWidthSizeClass
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackError
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoSleepTimerMode
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
    val color: Color,
)

private data class LyricsTextOption(
    val value: String,
)

private val LyricsColorOptions = listOf(
    LyricsColorOption("white", Color.White),
    LyricsColorOption("warm", Color(0xFFFFD6A0)),
    LyricsColorOption("blue", Color(0xFF9ED8FF)),
    LyricsColorOption("violet", Color(0xFFD9C2FF)),
    LyricsColorOption("mint", Color(0xFFA9F3D0)),
)

private val LyricsAlignmentOptions = listOf(
    LyricsTextOption("center"),
    LyricsTextOption("start"),
    LyricsTextOption("dynamic"),
)

private val LyricsMotionOptions = listOf(
    LyricsTextOption("calm"),
    LyricsTextOption("smooth"),
    LyricsTextOption("stage"),
)

private val PlaybackSpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SleepTimerOptions = listOf(15, 30, 60)
private val NowPlayingDismissSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

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
    onSetSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onSetReplayGainMode: (app.echo.android.model.playback.EchoReplayGainMode) -> Unit = {},
    onAdjustReplayGainPreamp: (Float) -> Unit,
    onSetSkipSilenceEnabled: (Boolean) -> Unit,
    onImportLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    positionState: State<PlaybackPositionState>? = null,
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
    isCurrentTrackFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    openLyricsRequestId: Int = 0,
    predictiveBackProgress: () -> Float = { 0f },
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
    LaunchedEffect(openLyricsRequestId) {
        if (openLyricsRequestId > 0) {
            pagerState.scrollToPage(NowPlayingPage.Lyrics.ordinal)
        }
    }
    // 进度以 State 引用下发,根页不读取具体值:进度 tick 只重组真正显示进度的叶子
    // (scrubber/当前歌词行),封面、玻璃、背景等子树保持可跳过。
    val statusState = rememberUpdatedState(status)
    val positionMsState = remember(positionState) {
        derivedStateOf { positionState?.value?.positionMs ?: statusState.value.positionMs }
    }
    val durationMsState = remember(positionState) {
        derivedStateOf {
            positionState?.value?.durationMs?.takeIf { it > 0L } ?: statusState.value.durationMs
        }
    }
    // 延迟读取 pager 偏移:横滑封面/歌词时只重组背景层,不重组整页
    val lyricsReveal = remember(pagerState) {
        {
            val pageOffset = (pagerState.currentPage - NowPlayingPage.Lyrics.ordinal) +
                pagerState.currentPageOffsetFraction
            (1f - abs(pageOffset)).coerceIn(0f, 1f)
        }
    }
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
    val density = LocalDensity.current
    val dismissScope = rememberCoroutineScope()
    val dismissHaptics = rememberEchoHapticPerformer()
    val dismissDrag = remember { NowPlayingDismissDragState() }
    val dismissThresholdPx = remember(density) { with(density) { 108.dp.toPx() } }
    val dismissFlingPx = remember(density) { with(density) { 1080.dp.toPx() } }
    val overlayBlocking = lyricsSettingsVisible || playbackSettingsVisible
    val dismissEnabledState = rememberUpdatedState(!overlayBlocking)
    val onDismissState = rememberUpdatedState(onDismiss)
    val nestedScrollConnection = rememberNowPlayingDismissConnection(
        dragState = dismissDrag,
        enabled = dismissEnabledState,
        thresholdPx = dismissThresholdPx,
        onCrossedThreshold = { crossed ->
            if (crossed) dismissHaptics.tick()
        },
        onSettle = { velocityY ->
            dismissScope.launch {
                settleNowPlayingDismiss(
                    dragState = dismissDrag,
                    velocityY = velocityY,
                    thresholdPx = dismissThresholdPx,
                    flingVelocityPx = dismissFlingPx,
                    onDismiss = onDismissState.value,
                )
            }
        },
    )
    // 下滑/返回手势的位移、缩放全部在 graphicsLayer 内读取状态:
    // 只走绘制通道,拖拽时不触发整页重组
    fun currentDismissOffsetPx(): Float =
        dismissDrag.offsetPx + predictiveBackProgress().coerceIn(0f, 1f) * dismissThresholdPx * 1.35f
    val pagerScrollEnabled by remember(dismissThresholdPx) {
        derivedStateOf { currentDismissOffsetPx() < 12f }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .graphicsLayer {
                val dismissOffsetPx = currentDismissOffsetPx()
                val settledProgress = (dismissOffsetPx / dismissThresholdPx).coerceIn(0f, 1f)
                translationY = dismissOffsetPx
                val scale = 1f - 0.045f * settledProgress
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.12f * settledProgress
                transformOrigin = TransformOrigin(0.5f, 0.06f)
            },
    ) {
        NowPlayingBackdrop(
            artworkUri = track?.artworkUri,
            palette = palette.asNowPlayingWash(),
            reveal = lyricsReveal,
            modifier = Modifier.fillMaxSize(),
        )

        val splitNowPlaying = LocalEchoWidthSizeClass.current.prefersNowPlayingSplit
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = if (splitNowPlaying) LocalEchoContentMaxWidth.current else 560.dp)
                .padding(horizontal = if (splitNowPlaying) 20.dp else 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NowPlayingTopBar(
                onDismiss = onDismiss,
                onHandleDrag = { delta ->
                    if (dismissEnabledState.value) {
                        dismissDrag.applyDelta(delta, dismissThresholdPx) { crossed ->
                            if (crossed) dismissHaptics.tick()
                        }
                    }
                },
                onHandleDragEnd = { velocityY ->
                    if (dismissEnabledState.value) {
                        dismissScope.launch {
                            settleNowPlayingDismiss(
                                dragState = dismissDrag,
                                velocityY = velocityY,
                                thresholdPx = dismissThresholdPx,
                                flingVelocityPx = dismissFlingPx,
                                onDismiss = onDismissState.value,
                            )
                        }
                    }
                },
                currentPage = pagerState.currentPage,
                pageCount = NowPlayingPage.entries.size,
                showPageIndicator = !splitNowPlaying,
            )
            status.diagnostics.lastError?.let { playbackError ->
                NowPlayingErrorBanner(
                    error = playbackError,
                    autoSkipped = status.diagnostics.lastCommand == "skip_error",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                )
            }

            if (splitNowPlaying) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    NowPlayingCoverPage(
                        status = status,
                        positionMsState = positionMsState,
                        durationMsState = durationMsState,
                        lyrics = readyLyrics,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onOpenQueue = onOpenQueue,
                        playbackSettingsExpanded = playbackSettingsVisible,
                        onOpenPlaybackSettings = { playbackSettingsVisible = true },
                        isCurrentTrackFavorite = isCurrentTrackFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onOpenLyrics = {},
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    NowPlayingLyricsPage(
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
                        positionMsState = positionMsState,
                        durationMsState = durationMsState,
                        onCloseLyrics = {},
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
                        showTransportDock = false,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 0,
                userScrollEnabled = pagerScrollEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (NowPlayingPage.entries[page]) {
                    NowPlayingPage.Cover -> NowPlayingCoverPage(
                        status = status,
                        positionMsState = positionMsState,
                        durationMsState = durationMsState,
                        lyrics = readyLyrics,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onOpenQueue = onOpenQueue,
                        playbackSettingsExpanded = playbackSettingsVisible,
                        onOpenPlaybackSettings = { playbackSettingsVisible = true },
                        isCurrentTrackFavorite = isCurrentTrackFavorite,
                        onToggleFavorite = onToggleFavorite,
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
                        animationsVisible = pagerState.currentPage == NowPlayingPage.Lyrics.ordinal,
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
                        positionMsState = positionMsState,
                        durationMsState = durationMsState,
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
            onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
            onCancelSleepTimer = onCancelSleepTimer,
            onSetReplayGain = onSetReplayGain,
            onSetReplayGainMode = onSetReplayGainMode,
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
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    lyrics: EchoLyrics?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    playbackSettingsExpanded: Boolean,
    onOpenPlaybackSettings: () -> Unit,
    isCurrentTrackFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val playingScale by animateFloatAsState(
        targetValue = if (status.isPlaying) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "now-playing-cover-scale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val tileSize = minOf(maxWidth, maxHeight)
            val artworkShape = RoundedCornerShape(24.dp)
            EchoArtworkImage(
                artworkUri = track?.artworkUri,
                contentDescription = track?.title,
                modifier = Modifier
                    .size(tileSize)
                    .graphicsLayer {
                        scaleX = playingScale
                        scaleY = playingScale
                    }
                    .shadow(elevation = 28.dp, shape = artworkShape, clip = false),
                shape = artworkShape,
                sizeClass = EchoArtworkSize.Hero,
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 行文本用 derivedStateOf:进度 tick 不重组 TrackInfo,只在歌词行切换时更新
                val currentLyricLine by remember(lyrics) {
                    derivedStateOf { currentSyncedLyricText(lyrics, positionMsState.value) }
                }
                NowPlayingTrackInfo(
                    title = track?.title ?: stringResource(L10nR.string.feature_player_not_playing_d72324),
                    artist = track?.artist ?: stringResource(L10nR.string.feature_player_pick_a_song_to_start_68b6af),
                    album = track?.album,
                    currentLyricLine = currentLyricLine,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onOpenLyrics = onOpenLyrics,
                    playbackSettingsExpanded = playbackSettingsExpanded,
                    onOpenPlaybackSettings = onOpenPlaybackSettings,
                    isFavorite = isCurrentTrackFavorite,
                    favoriteEnabled = track != null,
                    onToggleFavorite = onToggleFavorite,
                )
                Spacer(Modifier.height(10.dp))
                NowPlayingFormatInfo(diagnostics = status.diagnostics)
                Spacer(Modifier.height(12.dp))
                NowPlayingScrubber(
                    positionMsState = positionMsState,
                    durationMsState = durationMsState,
                    onSeek = onSeek,
                )
                Spacer(Modifier.height(6.dp))
                NowPlayingControlDock(
                    isPlaying = status.isPlaying,
                    leadingIcon = PlayerControlIcons.Lyrics,
                    leadingDescription = stringResource(L10nR.string.feature_player_lyrics_b90c97),
                    onLeadingAction = onOpenLyrics,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onOpenQueue = onOpenQueue,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NowPlayingTopBar(
    onDismiss: () -> Unit,
    onHandleDrag: (Float) -> Unit,
    onHandleDragEnd: (Float) -> Unit,
    currentPage: Int,
    pageCount: Int,
    showPageIndicator: Boolean,
) {
    val onHandleDragLatest = rememberUpdatedState(onHandleDrag)
    val handleDragState = rememberDraggableState { delta -> onHandleDragLatest.value(delta) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = handleDragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> onHandleDragEnd(velocity) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            GlyphButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                description = stringResource(L10nR.string.feature_player_close_player_d23966),
                touchSize = 44.dp,
                iconSize = 30.dp,
                tint = Color.White.copy(alpha = 0.88f),
                background = Color.Transparent,
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.42f)),
                )
            }
        }
        if (showPageIndicator && pageCount > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { index ->
                    val selected = index == currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 16.dp else 6.dp,
                        animationSpec = EchoMotion.silkDp(260),
                        label = "now-playing-page-dot",
                    )
                    Box(
                        modifier = Modifier
                            .width(dotWidth)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) 0.90f else 0.26f)),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
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
    positionMsState: State<Long>,
    durationMsState: State<Long>,
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
    showTransportDock: Boolean = true,
    modifier: Modifier = Modifier,
    animationsVisible: Boolean = true,
) {
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
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        NowPlayingScrubber(
                            positionMsState = positionMsState,
                            durationMsState = durationMsState,
                            onSeek = onSeek,
                        )
                        Spacer(Modifier.height(6.dp))
                        NowPlayingControlDock(
                            isPlaying = status.isPlaying,
                            leadingIcon = PlayerControlIcons.Settings,
                            leadingDescription = stringResource(L10nR.string.feature_player_lyrics_settings_843bc9),
                            onLeadingAction = onOpenLyricsSettings,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onOpenQueue = onOpenQueue,
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
    BackHandler(enabled = visible, onBack = onDismiss)
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
                        animationSpec = EchoMotion.silkSize(360),
                    ) +
                    fadeIn(tween(durationMillis = 260, delayMillis = 35, easing = LyricsSettingsMotionEasing)) +
                    scaleIn(
                        initialScale = 0.965f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                exit = slideOutVertically(EchoMotion.silkOffset(260)) { it } +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = EchoMotion.silkSize(260),
                    ) +
                    fadeOut(tween(durationMillis = 160, easing = LyricsSettingsMotionEasing)) +
                    scaleOut(
                        targetScale = 0.98f,
                        animationSpec = EchoMotion.silkFloat(260),
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
    val titleColor = if (dark) Color.White else RoonInk
    val mutedColor = if (dark) Color.White.copy(alpha = 0.78f) else RoonMuted
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
                            EchoGlassPanel.copy(alpha = 0.96f),
                            EchoGlassInk.copy(alpha = 0.96f),
                            EchoGlassNight.copy(alpha = 0.96f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFF7F5F6).copy(alpha = 0.97f),
                            Color(0xFFEFECEE).copy(alpha = 0.96f),
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
                .background(if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFF2A282E).copy(alpha = 0.22f)),
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
                Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = if (dark) lyricAccent else Color(0xFF1A191C), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(L10nR.string.feature_player_lyrics_settings_843bc9),
                    color = titleColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(L10nR.string.feature_player_font_color_and_display_b51a7b),
                    color = mutedColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            GlyphButton(
                icon = Icons.Rounded.Close,
                description = stringResource(L10nR.string.feature_player_close_lyrics_settings_752454),
                touchSize = 40.dp,
                iconSize = 22.dp,
                tint = titleColor,
                background = if (dark) EchoGlassPanel.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.62f),
                onClick = onDismiss,
            )
        }

        LyricsSettingsSection(
            icon = Icons.Rounded.TextFields,
            title = stringResource(L10nR.string.feature_player_font_c0bdeb),
            detail = lyricsFontDetail(lyricsFontMode, importedFontUri),
            enterDelayMillis = 45,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lyricsFontOptions().forEach { (value, label) ->
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

        LyricsSettingsSection(
            icon = Icons.Rounded.TextFields,
            title = stringResource(L10nR.string.feature_player_alignment_66dbdb),
            detail = lyricsAlignmentLabel(lyricsAlignment),
            enterDelayMillis = 90,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LyricsAlignmentOptions.forEach { option ->
                    LyricsChoiceChip(
                        text = lyricsAlignmentLabel(option.value),
                        selected = lyricsAlignment == option.value,
                        accent = lyricAccent,
                        onClick = { onLyricsAlignmentChange(option.value) },
                    )
                }
            }
        }

        LyricsSettingsSection(
            icon = Icons.Rounded.FormatSize,
            title = stringResource(L10nR.string.feature_player_type_size_8ff7ee),
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
                inactiveColor = if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFFC5C1C6).copy(alpha = 0.55f),
                thumbColor = Color.White,
            )
        }

        LyricsSettingsSection(
            icon = Icons.Rounded.ColorLens,
            title = stringResource(L10nR.string.feature_player_color_b67caf),
            detail = lyricsColorLabel(lyricsColorMode),
            enterDelayMillis = 180,
        ) {
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

        LyricsSettingsSection(
            icon = Icons.Rounded.FormatSize,
            title = stringResource(L10nR.string.feature_player_details_aec98d),
            detail = lyricsMotionLabel(lyricsMotionMode),
            enterDelayMillis = 225,
        ) {
            LyricsMiniSliderRow(
                label = stringResource(L10nR.string.feature_player_line_spacing_ecbb6f),
                valueLabel = "${(spacing * 100f).roundToInt()}%",
                fraction = spacingFraction,
                accent = lyricAccent,
                onValueChange = { fraction ->
                    onLyricsLineSpacingChange(0.82f + fraction.coerceIn(0f, 1f) * (1.38f - 0.82f))
                },
            )
            LyricsMiniSliderRow(
                label = stringResource(L10nR.string.feature_player_dim_be8c03),
                valueLabel = "${(dim * 100f).roundToInt()}%",
                fraction = dimFraction,
                accent = lyricAccent,
                onValueChange = { fraction ->
                    onLyricsBackgroundDimChange(fraction.coerceIn(0f, 1f) * 0.78f)
                },
            )
            LyricsMiniSliderRow(
                label = if (lyricsWordHighlightEnabled) {
                    stringResource(L10nR.string.feature_player_word_highlight_f4dec8)
                } else {
                    stringResource(L10nR.string.feature_player_word_highlight_off_b0e40a)
                },
                valueLabel = if (lyricsWordHighlightEnabled) {
                    "${(highlight * 100f).roundToInt()}%"
                } else {
                    stringResource(L10nR.string.feature_player_off_12ac24)
                },
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
                        text = lyricsMotionLabel(option.value),
                        selected = lyricsMotionMode == option.value,
                        accent = lyricAccent,
                        onClick = { onLyricsMotionModeChange(option.value) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LyricsToggleTile(
                stringResource(L10nR.string.feature_player_translation_53c7ce),
                showTranslation,
                lyricAccent,
                Modifier.weight(1f),
                available = hasTranslation,
            ) {
                onLyricsShowTranslationChange(!showTranslation)
            }
            LyricsToggleTile(
                stringResource(L10nR.string.feature_player_romaji_6ad0ab),
                showRomanization,
                lyricAccent,
                Modifier.weight(1f),
                available = hasRomanization,
            ) {
                onLyricsShowRomanizationChange(!showRomanization)
            }
            LyricsToggleTile(
                stringResource(L10nR.string.feature_player_emphasis_6517be),
                focusGlowEnabled,
                lyricAccent,
                Modifier.weight(1f),
            ) {
                onLyricsFocusGlowChange(!focusGlowEnabled)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LyricsToggleTile(
                stringResource(L10nR.string.feature_player_word_d653ce),
                lyricsWordHighlightEnabled,
                lyricAccent,
                Modifier.weight(1f),
            ) {
                onLyricsWordHighlightEnabledChange(!lyricsWordHighlightEnabled)
            }
            LyricsToggleTile(
                stringResource(L10nR.string.feature_player_immersive_d86793),
                lyricsImmersiveModeEnabled,
                lyricAccent,
                Modifier.weight(1f),
            ) {
                onLyricsImmersiveModeChange(!lyricsImmersiveModeEnabled)
            }
            LyricsToggleTile(
                stringResource(L10nR.string.feature_player_stage_fffe7d),
                lyricsMotionMode == "stage",
                lyricAccent,
                Modifier.weight(1f),
            ) {
                onLyricsMotionModeChange(if (lyricsMotionMode == "stage") "smooth" else "stage")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LyricsToolButton(
                Icons.Rounded.UploadFile,
                stringResource(L10nR.string.feature_player_import_lyrics_e7494e),
                onImportLyrics,
                Modifier.weight(1f),
            )
            LyricsToolButton(
                Icons.Rounded.Settings,
                stringResource(L10nR.string.feature_player_sync_tools_ef1217),
                { onShowLyricsControlDeckChange(!showLyricsControlDeck) },
                Modifier.weight(1f),
                showLyricsControlDeck,
            )
            LyricsToolButton(
                Icons.Rounded.Translate,
                stringResource(L10nR.string.feature_player_online_lyrics_21c928),
                { onOnlineLyricsEnabledChange(!onlineLyricsEnabled) },
                Modifier.weight(1f),
                onlineLyricsEnabled,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (dark) EchoGlassPanel.copy(alpha = 0.54f) else RoonInk.copy(alpha = 0.06f))
                .border(if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.Transparent), RoundedCornerShape(18.dp))
                .clickable(onClick = onCloseLyrics)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = titleColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(L10nR.string.feature_player_back_to_cover_815543),
                color = titleColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
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
    val titleColor = if (dark) Color.White else RoonInk
    val mutedColor = if (dark) Color.White.copy(alpha = 0.78f) else RoonMuted
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
                .background(if (dark) Color.White.copy(alpha = 0.06f) else RoonInk.copy(alpha = 0.06f)),
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
                    "stage" -> stringResource(L10nR.string.feature_player_each_line_lifts_with_the_beat_ddb972)
                    "calm" -> stringResource(L10nR.string.feature_player_lyrics_rest_quietly_in_the_center_810bd7)
                    else -> stringResource(L10nR.string.feature_player_lyrics_breathe_naturally_with_playback_c6b1df)
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
            text = stringResource(L10nR.string.feature_player_translation_romaji_appear_when_the_current_lyrics_include_3b41d9),
            modifier = Modifier.fillMaxWidth(),
            color = if (dark) Color.White.copy(alpha = 0.68f) else RoonMuted,
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
            Text(label, color = if (dark) Color.White.copy(alpha = 0.92f) else Color(0xFF2A282E), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, color = if (dark) Color.White.copy(alpha = 0.70f) else RoonMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        ThinSlider(
            fraction = fraction,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChange,
            activeColor = accent,
            inactiveColor = if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFFC5C1C6).copy(alpha = 0.55f),
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
                    BorderStroke(1.5.dp, if (selected) accent else if (dark) Color.White.copy(alpha = 0.52f) else Color(0xFF2A282E).copy(alpha = 0.42f)),
                    CircleShape,
                ),
        )
        Text(
            text = text,
            color = if (dark) Color.White else RoonInk,
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
        targetValue = if (selected) option.color else if (dark) Color.White.copy(alpha = 0.18f) else RoonInk.copy(alpha = 0.12f),
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
            lyricsColorLabel(option.value),
            color = if (selected) option.color else if (dark) Color.White.copy(alpha = 0.74f) else Color(0xFF2A282E),
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
    val active = enabled
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
            .heightIn(min = 74.dp)
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
            .toggleable(value = enabled, role = Role.Switch, onValueChange = { onClick() })
            .alpha(if (available) 1f else 0.80f)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = if (dark) Color.White else Color(0xFF2A282E), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        Text(
            listOfNotNull(
                stringResource(if (enabled) L10nR.string.feature_player_on_3062f9 else L10nR.string.feature_player_off_12ac24),
                if (!available) stringResource(L10nR.string.feature_player_no_data_5a8239) else null,
            ).joinToString(" · "),
            color = if (dark) Color.White.copy(alpha = 0.78f) else RoonMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
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
        Icon(icon, contentDescription = null, tint = if (dark) Color.White.copy(alpha = 0.92f) else Color(0xFF2A282E), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(title, color = if (dark) Color.White.copy(alpha = 0.96f) else Color(0xFF2A282E), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun lyricsColorForMode(mode: String): Color =
    LyricsColorOptions.firstOrNull { it.value == mode }?.color ?: Color.White

@Composable
private fun lyricsColorLabel(mode: String): String = when (mode) {
    "warm" -> stringResource(L10nR.string.feature_player_warm_060f63)
    "blue" -> stringResource(L10nR.string.feature_player_blue_4a9e32)
    "violet" -> stringResource(L10nR.string.feature_player_violet_a03e22)
    "mint" -> stringResource(L10nR.string.feature_player_green_d7b519)
    else -> stringResource(L10nR.string.feature_player_white_cc6c04)
}

@Composable
private fun lyricsAlignmentLabel(mode: String): String = when (mode) {
    "start" -> stringResource(L10nR.string.feature_player_left_f9d864)
    "dynamic" -> stringResource(L10nR.string.feature_player_stage_fffe7d)
    else -> stringResource(L10nR.string.feature_player_center_3e4c92)
}

@Composable
private fun lyricsMotionLabel(mode: String): String = when (mode) {
    "calm" -> stringResource(L10nR.string.feature_player_calm_207bcb)
    "stage" -> stringResource(L10nR.string.feature_player_stage_fffe7d)
    else -> stringResource(L10nR.string.feature_player_smooth_4989bb)
}

@Composable
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

@Composable
private fun lyricsFontOptions(): List<Pair<String, String>> = buildList {
    add("system" to stringResource(L10nR.string.feature_player_system_90f402))
    add("serif" to stringResource(L10nR.string.feature_player_serif_fb7b05))
    add("monospace" to stringResource(L10nR.string.feature_player_mono_ee96ee))
    add("imported" to stringResource(L10nR.string.feature_player_import_688061))
}

@Composable
private fun lyricsFontDetail(mode: String, importedFontUri: String?): String =
    when (mode) {
        "serif" -> stringResource(L10nR.string.feature_player_system_serif_ee6148)
        "monospace" -> stringResource(L10nR.string.feature_player_system_mono_6ca8fa)
        "imported" -> importedFontUri?.substringAfterLast('/')?.takeLast(18)?.let { name ->
            stringResource(L10nR.string.feature_player_import_name_a3094e, (name).toString())
        } ?: stringResource(L10nR.string.feature_player_choose_a_font_file_a60802)
        else -> stringResource(L10nR.string.feature_player_system_font_8ddbe6)
    }

@Composable
private fun LyricsLineList(
    lyrics: EchoLyrics,
    onAdjustOffset: (Long) -> Unit,
    positionMsState: State<Long>,
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
        // 进度经 State 引用传入 item,让行 lambda 捕获保持稳定:
        // 进度 tick 只重组"当前行"(逐词高亮),行切换才重组可见行。
        val timeline = remember(lyrics) { LyricsTimeline(lyrics.lines) }
        val activeIndices by remember(timeline) { derivedStateOf {
            if (synced) timeline.activeAt(positionMsState.value) else emptySet()
        } }
        val activeIndex = activeIndices.minOrNull() ?: -1
        val listState = rememberLazyListState()
        val dragging by listState.interactionSource.collectIsDraggedAsState()
        var following by remember(lyrics) { mutableStateOf(true) }
        var calibrationIndex by remember(lyrics) { mutableStateOf<Int?>(null) }
        LaunchedEffect(dragging) { if (dragging) following = false }
        LaunchedEffect(activeIndex, lyrics, following) {
            if (synced && activeIndex >= 0 && following) listState.animateScrollToItem(activeIndex)
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
                val active = synced && index in activeIndices
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
                                Modifier.combinedClickable(onClick = { onSeek(line.startMs) }, onLongClick = { following = false; calibrationIndex = index })
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
                    val wordHighlightEnabled = lyricsWordHighlightEnabled &&
                        !LocalEchoEffectivePerformanceMode.current.isLightweight
                    if (line.speaker != null || line.isBackground) {
                        Text(text = if (line.isBackground) echoText("Backing vocals", "和声", "コーラス") else line.speaker.orEmpty(),
                            color = lyricAccent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                    KaraokeLyricText(
                        line = line,
                        active = active,
                        enabled = wordHighlightEnabled,
                        position = positionMsState,
                        color = lyricAccent.copy(alpha = animatedPrimaryAlpha),
                        intensity = lyricsWordHighlightIntensity,
                        modifier = Modifier.fillMaxWidth(),
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
                        weight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                        align = textAlign,
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
                        )
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (synced && activeIndex < 0) {
                val seconds by remember(timeline) { derivedStateOf {
                    timeline.nextStart(positionMsState.value)?.let { ((it - positionMsState.value + 999) / 1000).coerceAtLeast(0) }
                } }
                seconds?.let { Text(echoText("Vocals in ${it}s", "距下一句 ${it} 秒", "次の歌詞まで ${it} 秒"), color = lyricAccent) }
            }
            if (!following && synced) {
                TextButton(
                    onClick = { following = true; calibrationIndex = null },
                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Black),
                ) {
                    Text(echoText("Back to current line", "回到当前句", "現在の歌詞に戻る"), color = lyricAccent)
                }
            }
            calibrationIndex?.let { index ->
                TextButton(onClick = {
                    onAdjustOffset(positionMsState.value - lyrics.lines[index].startMs)
                    calibrationIndex = null; following = true
                }) { Text(echoText("This line starts now", "这句现在开始", "この行を今に合わせる"), color = lyricAccent) }
            }
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
                    text = stringResource(L10nR.string.feature_player_import_lyrics_e7494e),
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
                description = stringResource(L10nR.string.feature_player_change_lyrics_170696),
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
                    icon = Icons.Rounded.FastRewind,
                    description = stringResource(L10nR.string.feature_player_lyrics_earlier_by_0_25s_0605b3),
                    touchSize = 34.dp,
                    iconSize = 21.dp,
                    tint = OnArtMuted,
                    background = Color.Transparent,
                    onClick = { onAdjustLyricsOffset(-250L) },
                )
                GlyphButton(
                    icon = Icons.Rounded.RestartAlt,
                    description = stringResource(L10nR.string.feature_player_reset_lyrics_offset_df9dcc),
                    touchSize = 34.dp,
                    iconSize = 20.dp,
                    tint = if (userOffsetMs == 0L) OnArtFaint else OnArtMuted,
                    background = Color.Transparent,
                    onClick = onResetLyricsOffset,
                )
                GlyphButton(
                    icon = Icons.Rounded.FastForward,
                    description = stringResource(L10nR.string.feature_player_lyrics_later_by_0_25s_f31341),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingTrackInfo(
    title: String,
    artist: String,
    album: String?,
    currentLyricLine: String?,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenLyrics: () -> Unit,
    playbackSettingsExpanded: Boolean,
    onOpenPlaybackSettings: () -> Unit,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                title,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                color = OnArt,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(
                targetState = currentLyricLine?.takeIf { it.isNotBlank() },
                transitionSpec = {
                    (fadeIn(tween(180, easing = LyricsSettingsMotionEasing)) +
                        slideInVertically(tween(220, easing = LyricsSettingsMotionEasing)) { it / 3 }) togetherWith
                        (fadeOut(tween(120, easing = LyricsSettingsMotionEasing)) +
                            slideOutVertically(tween(160, easing = LyricsSettingsMotionEasing)) { -it / 4 })
                },
                label = "now-playing-current-lyric",
            ) { line ->
                if (line == null) {
                    Spacer(Modifier.height(0.dp))
                } else {
                    Text(
                        line,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenLyrics)
                            .basicMarquee(iterations = Int.MAX_VALUE),
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White.copy(alpha = 0.82f),
                        ),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
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
                    icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    description = if (isFavorite) {
                        stringResource(L10nR.string.feature_player_unfavorite_3a27e4)
                    } else {
                        stringResource(L10nR.string.feature_player_favorite_b5d1f5)
                    },
                    touchSize = 40.dp,
                    iconSize = 21.dp,
                    tint = if (isFavorite) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.90f),
                    background = Color.White.copy(alpha = 0.12f),
                    onClick = { if (favoriteEnabled) onToggleFavorite() },
                )
                GlyphButton(
                    icon = Icons.Rounded.MoreHoriz,
                    description = if (playbackSettingsExpanded) {
                        stringResource(L10nR.string.feature_player_collapse_playback_settings_79e2cd)
                    } else {
                        stringResource(L10nR.string.feature_player_expand_playback_settings_cf64a0)
                    },
                    touchSize = 40.dp,
                    iconSize = 21.dp,
                    tint = Color.White.copy(alpha = 0.92f),
                    background = Color.White.copy(alpha = 0.12f),
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
    onSetSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onSetReplayGainMode: (app.echo.android.model.playback.EchoReplayGainMode) -> Unit = {},
    onAdjustReplayGainPreamp: (Float) -> Unit,
    onSetSkipSilenceEnabled: (Boolean) -> Unit,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
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
                        animationSpec = EchoMotion.silkSize(360),
                    ) +
                    fadeIn(tween(durationMillis = 260, delayMillis = 35, easing = LyricsSettingsMotionEasing)) +
                    scaleIn(
                        initialScale = 0.965f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                exit = slideOutVertically(EchoMotion.silkOffset(260)) { it } +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = EchoMotion.silkSize(260),
                    ) +
                    fadeOut(tween(durationMillis = 160, easing = LyricsSettingsMotionEasing)) +
                    scaleOut(
                        targetScale = 0.98f,
                        animationSpec = EchoMotion.silkFloat(260),
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                PlaybackSettingsPanel(
                    status = status,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    onSetSleepTimer = onSetSleepTimer,
                    onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onSetReplayGain = onSetReplayGain,
                    onSetReplayGainMode = onSetReplayGainMode,
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
    onSetSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onSetReplayGainMode: (app.echo.android.model.playback.EchoReplayGainMode) -> Unit = {},
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
    var showCustomSleepTimer by remember { mutableStateOf(false) }
    val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val titleColor = if (dark) Color.White else RoonInk
    val mutedColor = if (dark) Color.White.copy(alpha = 0.76f) else RoonMuted
    val accentColor = echoAccentColor()
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
                            EchoGlassPanel.copy(alpha = 0.98f),
                            EchoGlassInk.copy(alpha = 0.98f),
                            EchoGlassNight.copy(alpha = 0.98f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFF7F5F6).copy(alpha = 0.98f),
                            Color(0xFFEFECEE).copy(alpha = 0.98f),
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
                .background(if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFF2A282E).copy(alpha = 0.22f)),
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
                    stringResource(L10nR.string.feature_player_playback_settings_651436),
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
                description = stringResource(L10nR.string.feature_player_close_playback_settings_289e1a),
                touchSize = 42.dp,
                iconSize = 22.dp,
                tint = titleColor,
                background = Color.Transparent,
                onClick = onDismiss,
            )
        }
        PlaybackSettingsLabel(
            text = sleepTimerLabel(status),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PlaybackSpeedChip(
                text = stringResource(L10nR.string.feature_player_off_12ac24),
                selected = status.sleepTimerMode == EchoSleepTimerMode.Off,
                onClick = onCancelSleepTimer,
            )
            PlaybackSpeedChip(
                text = stringResource(L10nR.string.feature_player_this_track_210ea7),
                selected = status.sleepTimerMode == EchoSleepTimerMode.EndOfTrack,
                onClick = onSetSleepTimerEndOfTrack,
            )
            SleepTimerOptions.forEach { minutes ->
                PlaybackSpeedChip(
                    text = "${minutes}m",
                    selected = status.sleepTimerMode == EchoSleepTimerMode.Timed &&
                        status.sleepTimerMinutes == minutes,
                    onClick = { onSetSleepTimer(minutes) },
                )
            }
            PlaybackSpeedChip(
                text = if (
                    status.sleepTimerMode == EchoSleepTimerMode.Timed &&
                    status.sleepTimerMinutes != null &&
                    status.sleepTimerMinutes !in SleepTimerOptions
                ) {
                    "${status.sleepTimerMinutes}m"
                } else {
                    "…"
                },
                selected = status.sleepTimerMode == EchoSleepTimerMode.Timed &&
                    status.sleepTimerMinutes != null &&
                    status.sleepTimerMinutes !in SleepTimerOptions,
                onClick = { showCustomSleepTimer = true },
            )
        }
        if (showCustomSleepTimer) {
            var customMinutes by remember { mutableStateOf("45") }
            AlertDialog(
                onDismissRequest = { showCustomSleepTimer = false },
                title = { Text("Sleep timer") },
                text = {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter(Char::isDigit).take(3) },
                        label = { Text("Minutes (1–180)") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            customMinutes.toIntOrNull()?.coerceIn(1, 180)?.let(onSetSleepTimer)
                            showCustomSleepTimer = false
                        },
                    ) { Text("Set") }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomSleepTimer = false }) { Text("Cancel") }
                },
            )
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
                title = if (status.shuffleEnabled) {
                    stringResource(L10nR.string.feature_player_shuffle_on_c7c5c4)
                } else {
                    stringResource(L10nR.string.feature_player_in_order_47b60a)
                },
                selected = status.shuffleEnabled,
                onClick = onToggleShuffle,
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = stringResource(L10nR.string.feature_player_queue_84794b),
                selected = false,
                onClick = onOpenQueue,
                modifier = Modifier.weight(1f),
            )
        }
        PlaybackSettingsLabel(text = stringResource(L10nR.string.feature_player_speed_1d93fc))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackSettingButton(
                icon = Icons.Rounded.PlayArrow,
                title = stringResource(L10nR.string.feature_player_normal_speed_a8fc98),
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
                title = if (status.replayGainEnabled) {
                    stringResource(L10nR.string.feature_player_enabled_889420)
                } else {
                    stringResource(L10nR.string.feature_player_disabled_3bd0d0)
                },
                selected = status.replayGainEnabled,
                onClick = { onSetReplayGain(!status.replayGainEnabled, status.replayGainPreampDb) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackSpeedChip(
                text = "Auto",
                selected = status.replayGainMode == app.echo.android.model.playback.EchoReplayGainMode.Auto,
                onClick = { onSetReplayGainMode(app.echo.android.model.playback.EchoReplayGainMode.Auto) },
            )
            PlaybackSpeedChip(
                text = "Track",
                selected = status.replayGainMode == app.echo.android.model.playback.EchoReplayGainMode.Track,
                onClick = { onSetReplayGainMode(app.echo.android.model.playback.EchoReplayGainMode.Track) },
            )
            PlaybackSpeedChip(
                text = "Album",
                selected = status.replayGainMode == app.echo.android.model.playback.EchoReplayGainMode.Album,
                onClick = { onSetReplayGainMode(app.echo.android.model.playback.EchoReplayGainMode.Album) },
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.FastRewind,
                title = "-3dB",
                selected = false,
                onClick = { onAdjustReplayGainPreamp(-3f) },
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.FastForward,
                title = "+3dB",
                selected = false,
                onClick = { onAdjustReplayGainPreamp(3f) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(
                L10nR.string.feature_player_tag_status_replaygaintrackgaindb_let_formatreplaygaindb_unread_p_9c7a19,
                status.replayGainTrackGainDb?.let(::formatReplayGainDb)
                    ?: stringResource(
                        if (status.replayGainTagsLoaded) {
                            L10nR.string.feature_player_replay_gain_none
                        } else {
                            L10nR.string.feature_player_replay_gain_unread
                        },
                    ),
                formatReplayGainDb(status.replayGainPreampDb),
            ),
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
                title = if (status.skipSilenceEnabled) {
                    stringResource(L10nR.string.feature_player_skip_silence_on_159990)
                } else {
                    stringResource(L10nR.string.feature_player_skip_silence_off_8f8ae6)
                },
                selected = status.skipSilenceEnabled,
                onClick = { onSetSkipSilenceEnabled(!status.skipSilenceEnabled) },
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.RestartAlt,
                title = stringResource(L10nR.string.feature_player_lyrics_formatlyricsoffset_lyricsoffsetms_6c3953, (formatLyricsOffset(lyricsOffsetMs)).toString()),
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
                icon = Icons.Rounded.FastRewind,
                title = "-0.5s",
                selected = false,
                onClick = { onAdjustLyricsOffset(-500L) },
                modifier = Modifier.weight(1f),
            )
            PlaybackSettingButton(
                icon = Icons.Rounded.FastForward,
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

@Composable
private fun playbackSettingsSummary(status: EchoPlaybackStatus): String {
    val diagnostics = status.diagnostics
    val format = buildList {
        diagnostics.codec?.let { add(it) }
        diagnostics.sampleRateHz?.takeIf { it > 0 }?.let { add(formatSampleRate(it)) }
        diagnostics.bitDepth?.takeIf { it > 0 }?.let { add("${it}bit") }
    }.joinToString(" · ").ifBlank {
        stringResource(L10nR.string.feature_player_waiting_for_audio_info_a869fd)
    }
    val output = if (diagnostics.usbDeviceName != null) {
        stringResource(L10nR.string.feature_player_usb_output_c6800e)
    } else {
        stringResource(L10nR.string.feature_player_system_output_856789)
    }
    val mode = if (isNightcorePlayback(status)) {
        stringResource(L10nR.string.feature_player_nightcore_pitch_65449f)
    } else {
        stringResource(L10nR.string.feature_player_normal_speed_a8fc98)
    }
    val silence = if (status.skipSilenceEnabled) {
        stringResource(L10nR.string.feature_player_skip_silence_d27e03)
    } else {
        null
    }
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

@Composable
private fun sleepTimerLabel(status: EchoPlaybackStatus): String {
    if (status.sleepTimerMode == EchoSleepTimerMode.Off) {
        return stringResource(L10nR.string.feature_player_sleep_timer_108738)
    }
    if (status.sleepTimerMode == EchoSleepTimerMode.EndOfTrack) {
        val remaining = status.sleepTimerRemainingMs
        return if (remaining in 1 until 12 * 60 * 60 * 1000L) {
            val clock = formatSleepTimerRemaining(remaining)
            stringResource(L10nR.string.feature_player_sleep_timer_this_track_clock_e76a6a, (clock).toString())
        } else {
            stringResource(L10nR.string.feature_player_sleep_timer_this_track_1ae161)
        }
    }
    return if (status.sleepTimerRemainingMs > 0L) {
        val remaining = formatSleepTimerRemaining(status.sleepTimerRemainingMs)
        stringResource(L10nR.string.feature_player_sleep_timer_remaining_4b8c3f, (remaining).toString())
    } else {
        stringResource(L10nR.string.feature_player_sleep_timer_108738)
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

@Composable
private fun repeatModeLabel(mode: EchoRepeatMode): String = when (mode) {
    EchoRepeatMode.Off -> stringResource(L10nR.string.feature_player_repeat_off_e1d801)
    EchoRepeatMode.All -> stringResource(L10nR.string.feature_player_repeat_all_751078)
    EchoRepeatMode.One -> stringResource(L10nR.string.feature_player_repeat_one_3df94f)
}

@Composable
private fun NowPlayingErrorBanner(
    error: EchoPlaybackError,
    autoSkipped: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = if (autoSkipped) {
        stringResource(L10nR.string.feature_player_skipped_an_unplayable_track_dcd77d)
    } else {
        stringResource(L10nR.string.feature_player_unable_to_play_bee8f9)
    }
    val detail = playbackErrorLabel(error)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC7A1F2B))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFFFC7CE),
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                detail,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun playbackErrorLabel(error: EchoPlaybackError): String = when (error.kind) {
    EchoAudioErrorKind.FileMissing -> stringResource(L10nR.string.feature_player_audio_file_is_missing_31d228)
    EchoAudioErrorKind.UnsupportedFormat -> stringResource(L10nR.string.feature_player_audio_format_is_unsupported_53c7bb)
    EchoAudioErrorKind.DecodeFailure -> stringResource(L10nR.string.feature_player_audio_decode_failed_8fe0c7)
    EchoAudioErrorKind.NetworkFailure -> stringResource(L10nR.string.feature_player_network_playback_failed_3b492e)
    EchoAudioErrorKind.AuthenticationFailed -> stringResource(L10nR.string.feature_player_remote_authentication_failed_426253)
    EchoAudioErrorKind.PermissionDenied -> stringResource(L10nR.string.feature_player_playback_permission_denied_2a3371)
    EchoAudioErrorKind.OutputRouteFailure -> stringResource(L10nR.string.feature_player_output_device_failed_5abf21)
    EchoAudioErrorKind.AudioFocusLost -> stringResource(L10nR.string.feature_player_audio_focus_lost_328a0b)
    EchoAudioErrorKind.SystemInterrupted -> stringResource(L10nR.string.feature_player_playback_was_interrupted_by_the_system_70c8d1)
    EchoAudioErrorKind.Unknown -> error.message.ifBlank {
        stringResource(L10nR.string.feature_player_playback_failed_059f1f)
    }
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
    val chipShape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(Color.White.copy(alpha = if (highlight) 0.20f else 0.10f))
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (highlight) Color.White.copy(alpha = 0.98f) else Color.White.copy(alpha = 0.78f),
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
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onSeek: (Long) -> Unit,
) {
    // 进度 State 只在此叶子读取,tick 只重组 scrubber 本身
    val positionMs = positionMsState.value
    val durationMs = durationMsState.value
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
    val haptics = rememberEchoHapticPerformer()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerControlButton(leadingIcon, leadingDescription, onLeadingAction)
        PlayerControlButton(
            PlayerControlIcons.Previous,
            stringResource(L10nR.string.feature_player_previous_af0264),
            onClick = { haptics.tick(); onPrevious() },
            iconSize = 32.dp,
        )
        PlayerControlButton(
            if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
            stringResource(L10nR.string.feature_player_play_or_pause_37a70f),
            onClick = { haptics.confirm(); onPlayPause() },
            touchSize = 72.dp,
            iconSize = 48.dp,
            tint = Color.White,
        )
        PlayerControlButton(
            PlayerControlIcons.Next,
            stringResource(L10nR.string.feature_player_next_d67904),
            onClick = { haptics.tick(); onNext() },
            iconSize = 32.dp,
        )
        PlayerControlButton(PlayerControlIcons.Queue, stringResource(L10nR.string.feature_player_queue_37fa6a), onOpenQueue)
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
    modifier: Modifier = Modifier,
    glass: Boolean = false,
) {
    val clickMod = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
    if (glass) {
        EchoLiquidGlass(
            modifier = modifier
                .size(touchSize)
                .then(clickMod),
            shape = CircleShape,
            strength = 0.92f,
            elevation = 8.dp,
            dark = true,
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
        }
    } else {
        Box(
            modifier = modifier
                .size(touchSize)
                .clip(CircleShape)
                .background(background)
                .then(if (border.alpha > 0f) Modifier.border(BorderStroke(1.dp, border), CircleShape) else Modifier)
                .then(clickMod),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun NowPlayingBackdrop(
    artworkUri: String?,
    palette: ArtworkPalette,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    // 每帧变化的 reveal 只在这里读取,横滑时重组范围被限制在背景层
    val lyricsReveal = reveal()
    // 模糊半径量化为 5 档,避免每帧重建 RenderEffect
    val blurStep = (lyricsReveal * 4f).roundToInt()
    Box(modifier = modifier) {
        BlurredArtworkBackground(
            artworkUri = artworkUri,
            palette = palette,
            modifier = Modifier.fillMaxSize(),
            artworkScale = 1.16f + 0.10f * lyricsReveal,
            artworkBlur = 24.dp + 2.dp * blurStep,
            artworkAlpha = 0.58f - 0.08f * lyricsReveal,
            overlayStartAlpha = 0.46f + 0.12f * lyricsReveal,
            overlayMidAlpha = 0.58f + 0.10f * lyricsReveal,
            overlayEndAlpha = 0.90f,
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
    }
}

private class NowPlayingDismissDragState {
    var offsetPx by mutableFloatStateOf(0f)
    var crossedThreshold by mutableStateOf(false)

    fun applyDelta(delta: Float, thresholdPx: Float, onCrossedThreshold: (Boolean) -> Unit) {
        val resisted = if (offsetPx > thresholdPx && delta > 0f) delta * 0.38f else delta
        offsetPx = (offsetPx + resisted).coerceAtLeast(0f)
        val crossed = offsetPx >= thresholdPx
        if (crossed != crossedThreshold) {
            crossedThreshold = crossed
            onCrossedThreshold(crossed)
        }
    }

    fun reset() {
        offsetPx = 0f
        crossedThreshold = false
    }
}

@Composable
private fun rememberNowPlayingDismissConnection(
    dragState: NowPlayingDismissDragState,
    enabled: State<Boolean>,
    thresholdPx: Float,
    onCrossedThreshold: (Boolean) -> Unit,
    onSettle: (Float) -> Unit,
): NestedScrollConnection {
    val thresholdState = rememberUpdatedState(thresholdPx)
    val crossedState = rememberUpdatedState(onCrossedThreshold)
    val settleState = rememberUpdatedState(onSettle)
    return remember(dragState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled.value || available.y == 0f) return Offset.Zero
                if (dragState.offsetPx <= 0f && available.y <= 0f) return Offset.Zero
                if (dragState.offsetPx <= 0f) return Offset.Zero
                val consumed = if (available.y < 0f) {
                    available.y.coerceAtLeast(-dragState.offsetPx)
                } else {
                    available.y
                }
                dragState.applyDelta(consumed, thresholdState.value, crossedState.value)
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!enabled.value || available.y <= 0f) return Offset.Zero
                if (dragState.offsetPx <= 0f && available.y < 10f) return Offset.Zero
                dragState.applyDelta(available.y, thresholdState.value, crossedState.value)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!enabled.value) return Velocity.Zero
                if (dragState.offsetPx > 0f || available.y > 0f) {
                    settleState.value(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
}

private fun ArtworkPalette.asNowPlayingWash(): ArtworkPalette {
    val night = EchoGlassNight
    return copy(
        vibrant = lerp(deep, night, 0.48f),
        deep = lerp(deep, night, 0.22f),
        soft = lerp(soft, night, 0.62f),
    )
}

/** 歌词行按 startMs 升序(解析器已排序),二分找最后一个 startMs <= positionMs+80 的行;无则 -1。 */
private fun syncedLyricIndexAt(lines: List<EchoLyricLine>, positionMs: Long): Int {
    val target = positionMs
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].startMs <= target) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun currentSyncedLyricText(lyrics: EchoLyrics?, positionMs: Long): String? {
    if (lyrics == null || !lyrics.isSynced || lyrics.lines.isEmpty()) return null
    val index = syncedLyricIndexAt(lyrics.lines, positionMs)
    if (index < 0) return null
    val line = lyrics.lines[index]
    if (line.endMs?.let { positionMs >= it } == true) return null
    return line.text.takeIf { it.isNotBlank() }
}

private suspend fun settleNowPlayingDismiss(
    dragState: NowPlayingDismissDragState,
    velocityY: Float,
    thresholdPx: Float,
    flingVelocityPx: Float,
    onDismiss: () -> Unit,
) {
    val shouldDismiss = dragState.offsetPx >= thresholdPx ||
        (velocityY >= flingVelocityPx && dragState.offsetPx > thresholdPx * 0.28f)
    if (shouldDismiss) {
        onDismiss()
        // 退场动画期间组合仍存活:把偏移缓释回 0,避免退场中快速重开时带着
        // 残留偏移渲染(整页下移、封面翻页被锁)。全屏下滑退场会掩盖这点回移。
        animate(
            initialValue = dragState.offsetPx,
            targetValue = 0f,
            animationSpec = NowPlayingDismissSpring,
        ) { value, _ ->
            dragState.offsetPx = value
        }
        dragState.reset()
        return
    }
    val start = dragState.offsetPx
    if (start <= 0f) {
        dragState.reset()
        return
    }
    animate(
        initialValue = start,
        targetValue = 0f,
        initialVelocity = velocityY,
        animationSpec = NowPlayingDismissSpring,
    ) { value, _ ->
        dragState.offsetPx = value
    }
    dragState.reset()
}

package app.echo.android.feature.player

import androidx.compose.runtime.DisposableEffect

import androidx.compose.runtime.snapshotFlow

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoLiquidGlass
import app.echo.android.design.echoAccentColor
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoWidthSizeClass
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.rememberSilkPagerFlingBehavior
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.design.echoTheme
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackError
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 封面毛玻璃背景上的前景色：白色为主，半透明分级
internal val LyricsSettingsMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

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
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
    onSetRepeatMode: (app.echo.android.model.playback.EchoRepeatMode) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetPlaybackSpeed: (Float, Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onSetReplayGainMode: (app.echo.android.model.playback.EchoReplayGainMode) -> Unit = {},
    onAdjustReplayGainPreamp: (Float) -> Unit,
    replayGainScanState: app.echo.android.model.playback.EchoReplayGainScanState =
        app.echo.android.model.playback.EchoReplayGainScanState.Idle,
    onScanReplayGain: () -> Unit = {},
    onImportLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
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
    presentationExpanded: Boolean = true,
    onDragProgress: (Float) -> Unit = {},
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
    val dragProgressCallback = rememberUpdatedState(onDragProgress)
    LaunchedEffect(presentationExpanded) {
        if (presentationExpanded && dismissDrag.offsetPx > 0f) {
            dismissDrag.settleJob?.cancel()
            dismissDrag.settleJob = dismissScope.launch { restoreNowPlayingDismiss(dismissDrag) }
        }
    }
    val dismissThresholdPx = remember(density) { with(density) { 108.dp.toPx() } }
    LaunchedEffect(dismissDrag, dismissThresholdPx) {
        snapshotFlow { (dismissDrag.offsetPx / dismissThresholdPx).coerceIn(0f, 1f) }
            .collect { dragProgressCallback.value(it) }
    }
    DisposableEffect(Unit) { onDispose { dragProgressCallback.value(0f) } }
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
            dismissDrag.settleJob?.cancel()
            dismissDrag.settleJob = dismissScope.launch {
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
            palette = palette,
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
                        dismissDrag.settleJob?.cancel()
                        dismissDrag.settleJob = dismissScope.launch {
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
                        palette = palette,
                        presentationExpanded = presentationExpanded,
                        lightStrength = { 1f - 0.55f * (currentDismissOffsetPx() / dismissThresholdPx).coerceIn(0f, 1f) },
                        status = status,
                        positionMsState = positionMsState,
                        durationMsState = durationMsState,
                        lyrics = readyLyrics,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onOpenQueue = onOpenQueue,
                        onCast = onCast,
                        castActive = castActive,
                        playbackSettingsExpanded = playbackSettingsVisible,
                        onOpenPlaybackSettings = { playbackSettingsVisible = true },
                        isCurrentTrackFavorite = isCurrentTrackFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onOpenLyrics = {},
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
                        onCast = onCast,
                        castActive = castActive,
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
                flingBehavior = rememberSilkPagerFlingBehavior(pagerState),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (NowPlayingPage.entries[page]) {
                    NowPlayingPage.Cover -> NowPlayingCoverPage(
                        palette = palette,
                        presentationExpanded = presentationExpanded,
                        lightStrength = { 1f - 0.55f * (currentDismissOffsetPx() / dismissThresholdPx).coerceIn(0f, 1f) },
                        status = status,
                        positionMsState = positionMsState,
                        durationMsState = durationMsState,
                        lyrics = readyLyrics,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onOpenQueue = onOpenQueue,
                        onCast = onCast,
                        castActive = castActive,
                        playbackSettingsExpanded = playbackSettingsVisible,
                        onOpenPlaybackSettings = { playbackSettingsVisible = true },
                        isCurrentTrackFavorite = isCurrentTrackFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onOpenLyrics = {
                            pageScope.launch {
                                pagerState.animateScrollToPage(NowPlayingPage.Lyrics.ordinal)
                            }
                        },
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
                        onCast = onCast,
                        castActive = castActive,
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
            onSetRepeatMode = onSetRepeatMode,
            onToggleShuffle = onToggleShuffle,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSetSleepTimer = onSetSleepTimer,
            onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
            onCancelSleepTimer = onCancelSleepTimer,
            onSetReplayGain = onSetReplayGain,
            onSetReplayGainMode = onSetReplayGainMode,
            onAdjustReplayGainPreamp = onAdjustReplayGainPreamp,
            replayGainScanState = replayGainScanState,
            onScanReplayGain = onScanReplayGain,
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
    palette: ArtworkPalette,
    presentationExpanded: Boolean,
    lightStrength: () -> Float,
    status: EchoPlaybackStatus,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    lyrics: EchoLyrics?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
    playbackSettingsExpanded: Boolean,
    onOpenPlaybackSettings: () -> Unit,
    isCurrentTrackFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    var previousRequestedFrom by remember { mutableStateOf<String?>(null) }
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
            NowPlayingArtworkLight(
                palette = palette,
                expanded = presentationExpanded,
                gestureStrength = lightStrength,
                enabled = !track?.artworkUri.isNullOrBlank(),
                modifier = Modifier.size(tileSize).graphicsLayer {
                    scaleX = playingScale
                    scaleY = playingScale
                },
            ) {
                NowPlayingTrackTransition(
                    track = track,
                    previousRequestedFrom = previousRequestedFrom,
                    artwork = true,
                    modifier = Modifier.fillMaxSize(),
                ) { displayedTrack ->
                    EchoArtworkImage(
                        artworkUri = displayedTrack?.artworkUri,
                        contentDescription = displayedTrack?.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!LocalEchoEffectivePerformanceMode.current.isLightweight && LocalEchoDarkTheme.current) {
                                    Modifier.shadow(18.dp, artworkShape, clip = false)
                                } else Modifier
                            ),
                        shape = artworkShape,
                        sizeClass = EchoArtworkSize.Hero,
                    )
                }
            }
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
                NowPlayingTrackTransition(
                    track = track,
                    previousRequestedFrom = previousRequestedFrom,
                    modifier = Modifier.fillMaxWidth(),
                ) { displayedTrack ->
                    NowPlayingTrackInfo(
                        title = displayedTrack?.title ?: stringResource(L10nR.string.feature_player_not_playing_d72324),
                        artist = displayedTrack?.artist ?: stringResource(L10nR.string.feature_player_pick_a_song_to_start_68b6af),
                        album = displayedTrack?.album,
                        currentLyricLine = currentLyricLine.takeIf { displayedTrack?.id == track?.id },
                        onOpenLyrics = { if (displayedTrack?.id == track?.id) onOpenLyrics() },
                        playbackSettingsExpanded = playbackSettingsExpanded,
                        onOpenPlaybackSettings = { if (displayedTrack?.id == track?.id) onOpenPlaybackSettings() },
                        isFavorite = isCurrentTrackFavorite,
                        favoriteEnabled = track != null,
                        onToggleFavorite = { if (displayedTrack?.id == track?.id) onToggleFavorite() },
                    )
                }
                Spacer(Modifier.height(10.dp))
                NowPlayingFormatInfo(diagnostics = status.diagnostics)
                Spacer(Modifier.height(12.dp))
                NowPlayingScrubber(
                    trackKey = status.track?.id,
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
                    onNext = { previousRequestedFrom = null; onNext() },
                    onPrevious = { previousRequestedFrom = track?.id; onPrevious() },
                    onOpenQueue = onOpenQueue,
                    onCast = onCast,
                    castActive = castActive,
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
                onDragStarted = { onHandleDragLatest.value(0f) },
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
                tint = OnArt.copy(alpha = 0.88f),
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
                        .background(OnArt.copy(alpha = 0.42f)),
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
                            .background(OnArt.copy(alpha = if (selected) 0.90f else 0.26f)),
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
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
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
                .background((if (LocalEchoDarkTheme.current) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = lyricsDimAlpha)),
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
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        NowPlayingScrubber(
                            trackKey = status.track?.id,
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
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    AnimatedVisibility(
        visible = visible,
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
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).animateEnterExit(
                    enter = if (lightweight) fadeIn(tween(90)) else slideInVertically(EchoMotion.silkOffset(340)) { it },
                    exit = if (lightweight) fadeOut(tween(90)) else slideOutVertically(EchoMotion.silkOffset(260)) { it },
                ),
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val dark = LocalEchoDarkTheme.current
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.65f) else echoTheme().muted
    var typeExpanded by remember { mutableStateOf(true) }
    var colorExpanded by remember { mutableStateOf(false) }
    var motionExpanded by remember { mutableStateOf(false) }
    var contentExpanded by remember { mutableStateOf(false) }
    val heading = stringResource(L10nR.string.feature_player_lyrics_settings_843bc9)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().height(maxHeight * 0.88f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(if (dark) echoTheme().panel else Color(0xFFF4F1F3))
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LyricsSettingsHandle(onDismiss)
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Lyrics, null, tint = app.echo.android.design.echoAccentColor(), modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = titleColor)
                        Text(stringResource(L10nR.string.feature_player_font_color_and_display_b51a7b),
                            style = MaterialTheme.typography.bodySmall, color = mutedColor)
                    }
                    GlyphButton(Icons.Rounded.Close, stringResource(L10nR.string.feature_player_close_lyrics_settings_752454),
                        touchSize = 48.dp, iconSize = 22.dp, tint = titleColor, background = Color.Transparent, onClick = onDismiss)
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides titleColor) {
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlaybackSettingsSection(Icons.Rounded.TextFields,
                        stringResource(L10nR.string.lyrics_setting_typography),
                        "${lyricsFontDetail(lyricsFontMode, importedFontUri)} · ${(lyricsFontScale * 100).roundToInt()}%",
                        expanded = typeExpanded, onToggleExpanded = { typeExpanded = !typeExpanded }) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth().selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            lyricsFontOptions().forEach { (value, label) ->
                                PlaybackChoiceChip(text = label, selected = lyricsFontMode == value, fillWidth = false, onClick = {
                                    if (value == "imported" && importedFontUri.isNullOrBlank()) {
                                        onDismiss(); onImportLyricsFont()
                                    } else onLyricsFontFamilyChange(value)
                                })
                            }
                        }
                        if (!importedFontUri.isNullOrBlank()) TextButton(onClick = { onDismiss(); onImportLyricsFont() }) {
                            Text(stringResource(L10nR.string.lyrics_setting_replace_font))
                        }
                        Text(stringResource(L10nR.string.feature_player_alignment_66dbdb), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LyricsAlignmentOptions.forEach { option ->
                                PlaybackChoiceChip(text = lyricsAlignmentLabel(option.value), selected = lyricsAlignment == option.value,
                                    onClick = { onLyricsAlignmentChange(option.value) }, modifier = Modifier.weight(1f))
                            }
                        }
                        LyricsSettingSlider(stringResource(L10nR.string.feature_player_type_size_8ff7ee), lyricsFontScale, 0.82f..1.28f, 1f, onLyricsFontScaleChange)
                        LyricsSettingSlider(stringResource(L10nR.string.feature_player_line_spacing_ecbb6f), lyricsLineSpacing, 0.82f..1.38f, 1f, onLyricsLineSpacingChange)
                    }
                    PlaybackSettingsSection(Icons.Rounded.ColorLens,
                        stringResource(L10nR.string.lyrics_setting_color_background), lyricsColorLabel(lyricsColorMode),
                        expanded = colorExpanded, onToggleExpanded = { colorExpanded = !colorExpanded }) {
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LyricsColorOptions.forEach { option ->
                                LyricsColorSwatch(option, selected = option.value == lyricsColorMode,
                                    onClick = { onLyricsColorModeChange(option.value) }, modifier = Modifier.weight(1f))
                            }
                        }
                        LyricsSettingSlider(stringResource(L10nR.string.feature_player_dim_be8c03), lyricsBackgroundDim, 0f..0.78f, 0f, onLyricsBackgroundDimChange)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_emphasis_6517be), focusGlowEnabled, onLyricsFocusGlowChange)
                    }
                    PlaybackSettingsSection(Icons.Rounded.Lyrics,
                        stringResource(L10nR.string.lyrics_setting_motion), lyricsMotionLabel(lyricsMotionMode),
                        expanded = motionExpanded, onToggleExpanded = { motionExpanded = !motionExpanded }) {
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LyricsMotionOptions.forEach { option ->
                                PlaybackChoiceChip(text = lyricsMotionLabel(option.value), selected = lyricsMotionMode == option.value,
                                    onClick = { onLyricsMotionModeChange(option.value) }, modifier = Modifier.weight(1f))
                            }
                        }
                        LyricsSettingToggle(stringResource(L10nR.string.lyrics_setting_word_highlight), lyricsWordHighlightEnabled, onLyricsWordHighlightEnabledChange)
                        app.echo.android.design.EchoExpand(lyricsWordHighlightEnabled) {
                            LyricsSettingSlider(stringResource(L10nR.string.lyrics_setting_highlight_strength), lyricsWordHighlightIntensity,
                                0.45f..1.35f, 1f, onLyricsWordHighlightIntensityChange)
                        }
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_immersive_d86793), lyricsImmersiveModeEnabled, onLyricsImmersiveModeChange)
                    }
                    PlaybackSettingsSection(Icons.Rounded.Translate,
                        stringResource(L10nR.string.lyrics_setting_content),
                        stringResource(L10nR.string.lyrics_setting_content_summary),
                        expanded = contentExpanded, onToggleExpanded = { contentExpanded = !contentExpanded }) {
                        val noContent = stringResource(L10nR.string.lyrics_setting_no_content)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_translation_53c7ce), showTranslation,
                            onLyricsShowTranslationChange, hint = if (!hasTranslation) noContent else null)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_romaji_6ad0ab), showRomanization,
                            onLyricsShowRomanizationChange, hint = if (!hasRomanization) noContent else null)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_sync_tools_ef1217), showLyricsControlDeck, onShowLyricsControlDeckChange)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_online_lyrics_21c928), onlineLyricsEnabled, onOnlineLyricsEnabledChange)
                    }
                    PlaybackActionRow(Icons.Rounded.UploadFile, stringResource(L10nR.string.feature_player_import_lyrics_e7494e),
                        onClick = { onDismiss(); onImportLyrics() })
                    PlaybackActionRow(Icons.Rounded.Album, stringResource(L10nR.string.feature_player_back_to_cover_815543),
                        onClick = { onDismiss(); onCloseLyrics() })
                }
            }
        }
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
                        if (dark) echoTheme().ink.copy(alpha = backgroundAlpha) else Color.White.copy(alpha = 0.62f),
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
            color = if (dark) Color.White.copy(alpha = 0.68f) else echoTheme().muted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            maxLines = 2,
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
        targetValue = if (selected) app.echo.android.design.echoAccentColor() else if (dark) Color.White.copy(alpha = 0.18f) else echoTheme().heading.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = if (LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 180, easing = LyricsSettingsMotionEasing),
        label = "lyrics-palette-ring",
    )
    Column(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(top = 5.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
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
            color = if (dark) Color.White.copy(alpha = 0.9f) else echoTheme().heading,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun lyricsColorForMode(mode: String): Color {
    val color = LyricsColorOptions.firstOrNull { it.value == mode }?.color ?: Color.White
    return if (LocalEchoDarkTheme.current) color else if (mode == "white") MaterialTheme.colorScheme.onSurface
    else androidx.compose.ui.graphics.lerp(color, Color(0xFF29252A), 0.62f)
}

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

internal fun lyricsTextAlign(alignment: String): TextAlign =
    when (alignment) {
        "start" -> TextAlign.Start
        else -> TextAlign.Center
    }

internal fun lyricsHorizontalAlignment(alignment: String): Alignment.Horizontal =
    when (alignment) {
        "start" -> Alignment.Start
        else -> Alignment.CenterHorizontally
    }

internal fun lyricsMotionIntensity(mode: String): Float =
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
                        echoTheme().panel.copy(alpha = 0.54f),
                        echoTheme().ink.copy(alpha = 0.66f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, echoTheme().glassBorder), RoundedCornerShape(16.dp))
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

internal fun formatLyricsOffset(offsetMs: Long): String {
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
                        color = OnArt.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = OnArt.copy(alpha = 0.82f),
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
                    color = OnArtMuted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                album?.takeIf { it.isNotBlank() }?.let { value ->
                    Text(
                        value,
                        color = OnArt.copy(alpha = 0.78f),
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
                    tint = if (isFavorite) Color(0xFFFFD54F) else OnArt.copy(alpha = 0.90f),
                    background = OnArtChip,
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
                    tint = OnArt.copy(alpha = 0.92f),
                    background = OnArtChip,
                    onClick = onOpenPlaybackSettings,
                )
            }
        }
    }
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
    val chips = playbackFormatChips(
        diagnostics = diagnostics,
        pcmRateLabel = ::formatSampleRate,
        channelLabel = ::channelLabel,
    )
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
                color = OnArt.copy(alpha = 0.78f),
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
            .background(OnArt.copy(alpha = if (highlight) 0.20f else 0.10f))
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (highlight) OnArt.copy(alpha = 0.98f) else OnArt.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

internal fun formatSampleRate(hz: Int): String {
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
    trackKey: String?,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onSeek: (Long) -> Unit,
) {
    if (app.echo.android.model.radio.EchoRadioStation.isRadio(trackKey)) {
        RadioPlaybackProgress()
        return
    }
    // 进度 State 只在此叶子读取,tick 只重组 scrubber 本身
    val positionMs = positionMsState.value
    val durationMs = durationMsState.value
    var scrubFraction by remember(trackKey, durationMs) { mutableStateOf<Float?>(null) }
    val liveFraction = progressFraction(positionMs, durationMs)
    val shown = scrubFraction ?: liveFraction
    val currentMs = if (durationMs > 0L) (shown * durationMs).toLong() else positionMs
    val remainingMs = (durationMs - currentMs).coerceAtLeast(0L)

    Column(Modifier.fillMaxWidth()) {
        WaveformSeekBar(
            trackKey = trackKey,
            fraction = shown,
            enabled = durationMs > 0L,
            onPreview = { scrubFraction = it },
            onCommit = { fraction ->
                if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                scrubFraction = null
            },
            onCancel = { scrubFraction = null },
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(currentMs),
                color = OnArt.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "-" + formatDuration(remainingMs),
                color = OnArt.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 纤细圆角滑条（Apple Music 风）：细轨道 + 小圆点，支持拖动与点按定位。
 */
@Composable
internal fun GlyphButton(
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
            dark = LocalEchoDarkTheme.current,
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

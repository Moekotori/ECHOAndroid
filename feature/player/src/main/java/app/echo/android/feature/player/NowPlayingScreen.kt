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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import app.echo.android.design.animateSilkToPage
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import app.echo.android.design.EchoIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import app.echo.android.design.echoSheetUnderlay
import app.echo.android.design.EchoSheetOverlay
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoLiquidGlass
import app.echo.android.design.echoAccentColor
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoSharedPlayerArtwork
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.rememberSilkPagerFlingBehavior
import app.echo.android.design.rememberContentPagerNestedScroll
import app.echo.android.design.echoDrag
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberArtworkColorGrid
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.design.echoTheme
import app.echo.android.model.settings.EchoLyricsBackdrop
import app.echo.android.model.settings.EchoLyricsPageStyle
import app.echo.android.model.settings.EchoLyricsWordHighlightStyle
import app.echo.android.model.settings.EchoPlayerPageStyle
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
    onOpenTrackTools: () -> Unit = {},
    songActions: NowPlayingSongActions = NowPlayingSongActions(),
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
    smartTransitionState: State<app.echo.android.model.playback.EchoSmartTransitionVisual?>? = null,
    lyricsFontFamily: FontFamily? = null,
    lyricsPageStyle: String = EchoLyricsPageStyle.Mist.id,
    lyricsBackdrop: String = EchoLyricsBackdrop.Fluid.id,
    lyricsFontMode: String = "system",
    lyricsFontScale: Float = 1f,
    lyricsColorMode: String = "white",
    lyricsAlignment: String = EchoLyricsPageStyle.Mist.defaultAlignment,
    lyricsLineSpacing: Float = 1f,
    lyricsBackgroundDim: Float = 0f,
    lyricsWordHighlightEnabled: Boolean = true,
    lyricsEstimatedWordHighlightEnabled: Boolean = false,
    lyricsWordHighlightIntensity: Float = 1f,
    lyricsWordHighlightStyle: String = EchoLyricsWordHighlightStyle.Sweep.id,
    lyricsImmersiveModeEnabled: Boolean = false,
    lyricsShowTranslation: Boolean = true,
    lyricsShowRomanization: Boolean = true,
    lyricsFocusGlowEnabled: Boolean = false,
    importedFontUri: String? = null,
    onlineLyricsEnabled: Boolean = false,
    onLyricsPageStyleChange: (String) -> Unit = {},
    onImportLyricsFont: () -> Unit = {},
    onLyricsFontFamilyChange: (String) -> Unit = {},
    onLyricsFontScaleChange: (Float) -> Unit = {},
    onLyricsColorModeChange: (String) -> Unit = {},
    onLyricsBackdropChange: (String) -> Unit = {},
    onLyricsAlignmentChange: (String) -> Unit = {},
    onLyricsLineSpacingChange: (Float) -> Unit = {},
    onLyricsBackgroundDimChange: (Float) -> Unit = {},
    onLyricsWordHighlightEnabledChange: (Boolean) -> Unit = {},
    onLyricsEstimatedWordHighlightEnabledChange: (Boolean) -> Unit = {},
    onLyricsWordHighlightIntensityChange: (Float) -> Unit = {},
    onLyricsWordHighlightStyleChange: (String) -> Unit = {},
    onLyricsImmersiveModeChange: (Boolean) -> Unit = {},
    onLyricsShowTranslationChange: (Boolean) -> Unit = {},
    onLyricsShowRomanizationChange: (Boolean) -> Unit = {},
    onLyricsFocusGlowChange: (Boolean) -> Unit = {},
    onShowLyricsControlDeckChange: (Boolean) -> Unit = {},
    onOnlineLyricsEnabledChange: (Boolean) -> Unit = {},
    isCurrentTrackFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onOpenArtist: ((trackId: String, artistName: String) -> Unit)? = null,
    openLyricsRequestId: Int = 0,
    predictiveBackProgress: () -> Float = { 0f },
    presentationExpanded: Boolean = true,
    onDragProgress: (Float) -> Unit = {},
    onSuppressArtworkFlight: (Boolean) -> Unit = {},
    playerPageStyle: String = DefaultPlayerStyle,
    playerTextScale: Float = 1f,
    playerArtworkScale: Float = 1f,
    onPlayerAppearanceChange: (String, Float, Float) -> Unit = { _, _, _ -> },
) {
    val lyricsMotionMode = DefaultLyricsMotionMode
    val persistedAppearance = remember(playerPageStyle, playerTextScale, playerArtworkScale) {
        PlayerAppearance(
            style = normalizedPlayerStyle(playerPageStyle),
            textScale = playerTextScale.takeIf { it.isFinite() }?.coerceIn(0.8f, 1.2f) ?: 1f,
            artworkScale = playerArtworkScale.takeIf { it.isFinite() }?.coerceIn(0.7f, 1f) ?: 1f,
        )
    }
    var appearance by remember(persistedAppearance) { mutableStateOf(persistedAppearance) }
    // Style chips preview and save in the same click. Keep the latest draft here so
    // the save does not persist the appearance from the previous composition.
    val appearanceDraft = remember(persistedAppearance) { AppearanceDraft(persistedAppearance) }
    appearanceDraft.current = appearance
    val track = status.track
    val isRadio = app.echo.android.model.radio.EchoRadioStation.isRadio(track?.id)
    val radioColors = if (isRadio) radioPlayerColors() else null
    val effectivePerformanceMode = LocalEchoEffectivePerformanceMode.current
    val effectiveLyricsFocusGlowEnabled = lyricsFocusGlowEnabled && !effectivePerformanceMode.isLightweight
    val palette = rememberArtworkPalette(track?.artworkUri, seedKey = track?.id)
    val backdropStyle = EchoLyricsBackdrop.fromId(lyricsBackdrop)
    val fullBleedPage = appearance.style.isFullBleedStyle()
    // Full-bleed page styles always paint their own field, whatever the lyrics backdrop setting says.
    val shownBackdrop = if (fullBleedPage) EchoLyricsBackdrop.Halftone else backdropStyle
    val detailedGrid = fullBleedPage && appearance.style != EchoPlayerPageStyle.Halftone.id
    val colorGrid = rememberArtworkColorGrid(track?.artworkUri, enabled = shownBackdrop.needsColorGrid, detailed = detailedGrid)
    var splitNowPlaying by remember { mutableStateOf(false) }
    // The chat thread reads the lyrics in place; it has no lyrics page to swipe to.
    val inlineLyrics = !isRadio && appearance.style == EchoPlayerPageStyle.ChatThread.id
    val pagerState = rememberPagerState(
        // A direct lyrics opening has its own outer entrance; do not add a simultaneous page slide.
        initialPage = if (openLyricsRequestId > 0 && !isRadio && !inlineLyrics) NowPlayingPage.Lyrics.ordinal else NowPlayingPage.Cover.ordinal,
        pageCount = { if (isRadio || inlineLyrics) 1 else NowPlayingPage.entries.size },
    )
    val pageScope = rememberCoroutineScope()
    val pageStates = rememberSaveableStateHolder()
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
    // The backdrop reads these lazily; only a line change or play/pause reaches it, never a position tick.
    val backdropLink = if (backdropStyle.reactsToLyrics) {
        val timeline = remember(readyLyrics) { readyLyrics?.lines?.let(::LyricsTimeline) }
        val playing = rememberUpdatedState(status.isPlaying)
        val lineIndex = remember(timeline, positionMsState) {
            derivedStateOf { timeline?.lastStarted(positionMsState.value) ?: -1 }
        }
        remember(playing, lineIndex) { BackdropLink(playing, lineIndex) }
    } else {
        val playing = rememberUpdatedState(status.isPlaying)
        remember(playing) { BackdropLink(playing, mutableIntStateOf(-1)) }
    }
    val hasTranslation = remember(readyLyrics) {
        readyLyrics?.lines?.any { !it.translation.isNullOrBlank() } == true
    }
    val hasRomanization = remember(readyLyrics) {
        readyLyrics?.lines?.any { !it.romanization.isNullOrBlank() } == true
    }
    var lyricsSettingsVisible by rememberSaveable { mutableStateOf(false) }
    var playbackSettingsVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(presentationExpanded) {
        if (!presentationExpanded) {
            playbackSettingsVisible = false
            lyricsSettingsVisible = false
        }
    }
    var handledLyricsRequestId by rememberSaveable { mutableIntStateOf(openLyricsRequestId) }
    // Opening the drawer can interrupt a horizontal fling. Settle the cover before
    // a style changes its page width, so controls cannot remain partly off-screen.
    LaunchedEffect(playbackSettingsVisible, appearance.style, splitNowPlaying, isRadio) {
        if (playbackSettingsVisible) {
            if (splitNowPlaying || isRadio) pagerState.requestScrollToPage(NowPlayingPage.Cover.ordinal)
            else pagerState.scrollToPage(NowPlayingPage.Cover.ordinal)
        }
    }
    LaunchedEffect(openLyricsRequestId, isRadio, splitNowPlaying, inlineLyrics) {
        if (isRadio) {
            pagerState.requestScrollToPage(NowPlayingPage.Cover.ordinal)
            lyricsSettingsVisible = false
        } else if (inlineLyrics) {
            handledLyricsRequestId = openLyricsRequestId
        } else if (openLyricsRequestId > handledLyricsRequestId) {
            if (splitNowPlaying || EchoPlayerPageStyle.fromId(appearance.style).lyricsPreset.isAfterglow) pagerState.requestScrollToPage(NowPlayingPage.Lyrics.ordinal)
            else pagerState.animateSilkToPage(NowPlayingPage.Lyrics.ordinal, effectivePerformanceMode.isLightweight)
            handledLyricsRequestId = openLyricsRequestId
        }
    }
    val lyricAccent = lyricsColorForMode(lyricsColorMode)
    val density = LocalDensity.current
    val dismissScope = rememberCoroutineScope()
    val dismissHaptics = rememberEchoHapticPerformer()
    val dismissDrag = remember { NowPlayingDismissDragState() }
    val dismissGesture = remember { NowPlayingGestureCompletion() }
    val dragProgressCallback = rememberUpdatedState(onDragProgress)
    LaunchedEffect(presentationExpanded) {
        if (presentationExpanded && dismissDrag.offsetPx > 0f) {
            dismissDrag.settleJob?.cancel()
            dismissDrag.settleJob = dismissScope.launch { restoreNowPlayingDismiss(dismissDrag, lightweight = effectivePerformanceMode.isLightweight) }
        }
    }
    val dismissThresholdPx = remember(density) { with(density) { 108.dp.toPx() } }
    LaunchedEffect(dismissDrag, dismissThresholdPx) {
        snapshotFlow { (dismissDrag.offsetPx / dismissThresholdPx).coerceIn(0f, 1f) }
            .collect { dragProgressCallback.value(it) }
    }
    DisposableEffect(Unit) { onDispose { dragProgressCallback.value(0f) } }
    val dismissFlingPx = remember(density) { with(density) { 1080.dp.toPx() } }
    var playerHeightPx by remember { mutableFloatStateOf(0f) }
    val suppressArtworkFlight = rememberUpdatedState(onSuppressArtworkFlight)
    val overlayBlocking = lyricsSettingsVisible || playbackSettingsVisible
    val dismissEnabledState = rememberUpdatedState(presentationExpanded && !overlayBlocking)
    LaunchedEffect(overlayBlocking) {
        if (overlayBlocking && dismissDrag.offsetPx > 0f) {
            dismissDrag.settleJob?.cancel()
            dismissDrag.settleJob = dismissScope.launch {
                restoreNowPlayingDismiss(dismissDrag, lightweight = effectivePerformanceMode.isLightweight)
            }
        }
    }
    val onDismissState = rememberUpdatedState(onDismiss)
    val lightweightDismiss = effectivePerformanceMode.isLightweight
    fun launchDismiss(velocityY: Float, force: Boolean = false, awaitGesture: Boolean = false) {
        val gestureResult = if (awaitGesture) dismissGesture.result else null
        val exitDistancePx = if (playerHeightPx > 0f) playerHeightPx + 8f else 0f
        dismissDrag.settleJob?.cancel()
        dismissDrag.settleJob = dismissScope.launch {
            if (gestureResult != null && gestureResult.await() != true) {
                restoreNowPlayingDismiss(dismissDrag, lightweight = lightweightDismiss)
                return@launch
            }
            var suppressed = false
            try {
                settleNowPlayingDismiss(
                    dragState = dismissDrag,
                    velocityY = velocityY,
                    thresholdPx = dismissThresholdPx,
                    flingVelocityPx = dismissFlingPx,
                    onDismiss = onDismissState.value,
                    lightweight = lightweightDismiss,
                    exitDistancePx = exitDistancePx,
                    forceDismiss = force,
                    onSuppressArtworkFlight = {
                        suppressed = true
                        suppressArtworkFlight.value(true)
                    },
                )
            } finally {
                if (suppressed && dismissDrag.isDragging) suppressArtworkFlight.value(false)
            }
        }
    }
    val nestedScrollConnection = rememberNowPlayingDismissConnection(
        dragState = dismissDrag,
        enabled = dismissEnabledState,
        thresholdPx = dismissThresholdPx,
        onCrossedThreshold = { crossed ->
            if (crossed) dismissHaptics.tick()
        },
        onSettle = { velocityY -> launchDismiss(velocityY, awaitGesture = true) },
    )
    // 位移和圆角都在 graphicsLayer 里读状态,拖拽时不重组整页。
    fun currentDismissOffsetPx(): Float =
        dismissDrag.offsetPx + predictiveBackProgress().coerceIn(0f, 1f) * dismissThresholdPx * 1.35f
    val pagerScrollEnabled by remember(dismissThresholdPx) {
        derivedStateOf { currentDismissOffsetPx() < 12f }
    }

    val sharedStyle = EchoPlayerPageStyle.fromId(appearance.style)
    val lyricStyle = sharedStyle.lyricsPreset
    val styledLyricsFont = if (lyricsFontMode == "system")
        sharedStyle.displayFont ?: lyricsFontFamily else lyricsFontFamily
    val windowLayout = app.echo.android.design.rememberEchoWindowLayout()
    val shortLandscape = windowLayout.shortLandscape
    val fold by rememberPlayerFold(presentationExpanded)
    val preferSplit = windowLayout.supportsTwoPanes() && (!lyricStyle.isAfterglow || lyricsAlignment == "vertical")
    val wideSinglePage = windowLayout.widthDp >= 840f
    val lyricsSurfaceVisible = !isRadio && pagerState.currentPage == NowPlayingPage.Lyrics.ordinal
    val immersiveAfterglow = lyricsAlignment != "vertical" && !isRadio && fold == null && !splitNowPlaying && lyricStyle.isAfterglow && lyricsSurfaceVisible && !pagerState.isScrollInProgress
    val sleeveTopBar = !isRadio && appearance.usesFlatSurface && !splitNowPlaying && pagerState.currentPage == NowPlayingPage.Cover.ordinal
    val drawLyricsBackdrop = !isRadio && (!appearance.usesFlatSurface || fullBleedPage)
    RecordSleeveSystemBars(
        enabled = presentationExpanded && (!immersiveAfterglow || playbackSettingsVisible),
        darkIcons = if (isRadio) !LocalEchoDarkTheme.current else sharedStyle.isLight,
        navigationDarkIcons = if (playbackSettingsVisible || isRadio) {
            !LocalEchoDarkTheme.current
        } else {
            sharedStyle.isLight
        },
    )

    // Settings follow the app theme even when the player uses its own immersive palette.
    val settingsDarkTheme = LocalEchoDarkTheme.current
    val settingsTokens = LocalEchoTheme.current
    val settingsColorScheme = MaterialTheme.colorScheme
    val settingsTypography = MaterialTheme.typography
    CompositionLocalProvider(
        LocalPlayerPageStyle provides sharedStyle,
        LocalLyricsWordHighlightStyle provides EchoLyricsWordHighlightStyle.fromId(lyricsWordHighlightStyle),
    ) {
    LyricsPageTheme(lyricStyle, enabled = !isRadio) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { playerHeightPx = it.height.toFloat() }
                .observeNowPlayingGesture(dismissGesture)
                .nowPlayingSurfaceDismiss(
                    enabled = { dismissEnabledState.value },
                    onDelta = { delta ->
                        if (dismissEnabledState.value) {
                            dismissDrag.applyDelta(delta, dismissThresholdPx) { crossed ->
                                if (crossed) dismissHaptics.tick()
                            }
                        }
                    },
                    onStop = { velocityY ->
                        if (dismissEnabledState.value) launchDismiss(velocityY)
                    },
                    onCancel = {
                        dismissDrag.settleJob?.cancel()
                        dismissDrag.settleJob = dismissScope.launch {
                            restoreNowPlayingDismiss(dismissDrag, lightweight = effectivePerformanceMode.isLightweight)
                        }
                    },
                )
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer {
                    val dismissOffsetPx = currentDismissOffsetPx()
                    translationY = dismissOffsetPx
                    // 窗口保持不透明,只把露出来的两个顶角收成圆角,避免生硬的矩形透底。
                    val corner = nowPlayingSheetCornerFraction(dismissOffsetPx, 72.dp.toPx())
                    if (corner <= 0f) {
                        shape = RectangleShape
                        clip = false
                        shadowElevation = 0f
                    } else {
                        shape = RoundedCornerShape(topStart = 28.dp * corner, topEnd = 28.dp * corner)
                        clip = true
                        shadowElevation = if (effectivePerformanceMode.isLightweight) 0f else 18.dp.toPx() * corner
                    }
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .background(radioColors?.background ?: appearance.background),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().echoSheetUnderlay(overlayBlocking), contentAlignment = Alignment.TopCenter) {
            if (immersiveAfterglow) {
                app.echo.android.feature.player.afterglow.AfterglowImmersivePage(
                    status = status, lyricsState = lyricsState, style = lyricStyle,
                    position = positionMsState, duration = durationMsState,
                    fontFamily = styledLyricsFont, fontScale = lyricsFontScale, lineSpacing = lyricsLineSpacing,
                    motionMode = lyricsMotionMode, showTranslation = lyricsShowTranslation,
                    showRomanization = lyricsShowRomanization, wordHighlight = lyricsWordHighlightEnabled,
                    estimatedHighlight = lyricsEstimatedWordHighlightEnabled, highlightIntensity = lyricsWordHighlightIntensity,
                    visible = presentationExpanded, overlayBlocking = overlayBlocking,
                    onDismiss = onDismiss, onSettings = { lyricsSettingsVisible = true },
                    onImport = onImportLyrics, onAdjustOffset = onAdjustLyricsOffset,
                    onPlayPause = onPlayPause, onNext = onNext, onPrevious = onPrevious,
                    onSeek = onSeek, onQueue = onOpenQueue, onCast = onCast, castActive = castActive,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
            if (drawLyricsBackdrop) {
                val backdropModifier = Modifier.fillMaxSize()
                LyricsPageBackdrop(palette, lyricsReveal,
                    presentationExpanded && !overlayBlocking && track != null, backdropModifier, shownBackdrop, colorGrid, backdropLink)
            }
            if (!isRadio) PageStyleDecor(sharedStyle, lyricsReveal, Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .widthIn(max = if (fold != null) windowLayout.widthDp.dp else if (preferSplit) 1280.dp else if (shortLandscape || wideSinglePage) 960.dp else 560.dp)
                    .fillMaxSize()
                    .padding(horizontal = if (isRadio) 26.dp else if (splitNowPlaying) 20.dp else when (appearance.style) { "record_sleeve" -> 30.dp; "pixel_handheld" -> 18.dp; "type_poster" -> 24.dp; else -> 26.dp }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HingeSafePlayerHeader(fold) { Column {
                NowPlayingTopBar(
                    onDismiss = { launchDismiss(0f, force = true) },
                    onHandleDrag = { delta ->
                        if (dismissEnabledState.value) {
                            dismissDrag.applyDelta(delta, dismissThresholdPx) { crossed ->
                                if (crossed) dismissHaptics.tick()
                            }
                        }
                    },
                    onHandleDragEnd = { velocityY ->
                        if (dismissEnabledState.value) launchDismiss(velocityY)
                    },
                    onHandleDragCancel = {
                        dismissDrag.settleJob?.cancel()
                        dismissDrag.settleJob = dismissScope.launch {
                            restoreNowPlayingDismiss(dismissDrag, lightweight = effectivePerformanceMode.isLightweight)
                        }
                    },
                    currentPage = pagerState.currentPage,
                    pageCount = NowPlayingPage.entries.size,
                    showPageIndicator = !isRadio && !splitNowPlaying,
                    editorial = sleeveTopBar,
                    appearance = appearance,
                    radioColors = radioColors,
                    isFavorite = isCurrentTrackFavorite,
                    onToggleFavorite = { if (track != null) onToggleFavorite() },
                    lyricsPage = lyricsSurfaceVisible && !splitNowPlaying,
                    lyricsReveal = lyricsReveal,
                    onCast = onCast,
                    castActive = castActive,
                    onOpenPlaybackSettings = {
                        if (lyricsSurfaceVisible && !splitNowPlaying) lyricsSettingsVisible = true
                        else playbackSettingsVisible = true
                    },
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

                } }

                val lyricsPageBody: @Composable (Boolean, Boolean, Modifier) -> Unit = { split, embedded, pageModifier ->
                        NowPlayingLyricsPage(
                            animationsVisible = presentationExpanded && !overlayBlocking &&
                                (split || embedded || pagerState.currentPage == NowPlayingPage.Lyrics.ordinal),
                            embedded = embedded,
                            pageReveal = if (split || embedded) ({ 1f }) else lyricsReveal,
                            status = status,
                            lyricsState = lyricsState,
                            lyricsPageStyle = lyricStyle,
                            palette = palette,
                            showBackdrop = split,
                            backdropStyle = backdropStyle,
                            colorGrid = colorGrid,
                            backdropLink = backdropLink,
                            showLyricsControlDeck = showLyricsControlDeck,
                            lyricsFontFamily = styledLyricsFont,
                            lyricsFontScale = lyricsFontScale,
                            lyricsColorMode = lyricsColorMode,
                            lyricsAlignment = lyricsAlignment,
                            lyricsLineSpacing = lyricsLineSpacing,
                            lyricsBackgroundDim = lyricsBackgroundDim,
                            lyricsWordHighlightEnabled = lyricsWordHighlightEnabled,
                            lyricsEstimatedWordHighlightEnabled = lyricsEstimatedWordHighlightEnabled,
                            lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
                            lyricsImmersiveModeEnabled = lyricsImmersiveModeEnabled,
                            lyricsMotionMode = lyricsMotionMode,
                            lyricsShowTranslation = lyricsShowTranslation,
                            lyricsShowRomanization = lyricsShowRomanization,
                            lyricsFocusGlowEnabled = effectiveLyricsFocusGlowEnabled,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onSeek = onSeek,
                            onOpenQueue = onOpenQueue,
                            positionMsState = positionMsState,
                            durationMsState = durationMsState,
                            onImportLyrics = onImportLyrics,
                            onAdjustLyricsOffset = onAdjustLyricsOffset,
                            onResetLyricsOffset = onResetLyricsOffset,
                            onOpenLyricsSettings = { lyricsSettingsVisible = true },
                            onOpenArtist = onOpenArtist,
                            showTransportDock = !split,
                            modifier = pageModifier,
                        )
                }
                val lyricsRenderer: @Composable (Boolean) -> Unit = { split ->
                    pageStates.SaveableStateProvider("lyrics") { lyricsPageBody(split, false, Modifier.fillMaxSize()) }
                }
                val coverRenderer: @Composable () -> Unit = {
                    pageStates.SaveableStateProvider("cover") {
                        PlayerCoverPage(
                            appearance = appearance,
                            palette = palette,
                            presentationExpanded = presentationExpanded &&
                                (splitNowPlaying || pagerState.currentPage == NowPlayingPage.Cover.ordinal),
                            status = status,
                            positionMsState = positionMsState,
                            durationMsState = durationMsState,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onSeek = onSeek,
                            onOpenQueue = onOpenQueue,
                            onCast = onCast,
                            castActive = castActive,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeatMode = onCycleRepeatMode,
                            isCurrentTrackFavorite = isCurrentTrackFavorite,
                            onToggleFavorite = onToggleFavorite,
                            onOpenLyrics = {
                                if (inlineLyrics) lyricsSettingsVisible = true
                                else if (splitNowPlaying) pagerState.requestScrollToPage(NowPlayingPage.Lyrics.ordinal)
                                else pageScope.launch {
                                    if (lyricStyle.isAfterglow) pagerState.requestScrollToPage(NowPlayingPage.Lyrics.ordinal)
                                    else pagerState.animateSilkToPage(NowPlayingPage.Lyrics.ordinal, effectivePerformanceMode.isLightweight)
                                }
                            },
                            onOpenArtist = onOpenArtist,
                            lyricsContent = if (inlineLyrics) ({ lyricsModifier -> lyricsPageBody(false, true, lyricsModifier) }) else null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                val latestCoverRenderer = rememberUpdatedState(coverRenderer)
                val latestLyricsRenderer = rememberUpdatedState(lyricsRenderer)
                val coverPage = remember { movableContentOf { latestCoverRenderer.value() } }
                val lyricsPage = remember { movableContentOf { split: Boolean -> latestLyricsRenderer.value(split) } }
                AdaptivePlayerPanes(
                    fold = fold, preferSplit = preferSplit, allowSplit = !isRadio && !inlineLyrics,
                    onSplitChanged = { if (splitNowPlaying != it) splitNowPlaying = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    cover = coverPage,
                    lyrics = { lyricsPage(true) },
                    singlePage = {
                        if (isRadio) {
                    RadioNowPlayingPage(
                        status = status,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onOpenQueue = onOpenQueue,
                        onCast = onCast,
                        castActive = castActive,
                        onSetSleepTimer = onSetSleepTimer,
                        onCancelSleepTimer = onCancelSleepTimer,
                        modifier = Modifier.fillMaxSize(),
                    )

                        } else if (inlineLyrics) {
                            coverPage()
                        } else {
                            val pageFling = rememberSilkPagerFlingBehavior(pagerState)
                            val pageNestedScroll = rememberContentPagerNestedScroll(pagerState, pageFling)
                            val adjacentPages = rememberNowPlayingAdjacentPages(
                                pagerState, presentationExpanded, effectivePerformanceMode.isLightweight,
                            )
                            HorizontalPager(
                            state = pagerState, beyondViewportPageCount = adjacentPages,
                            userScrollEnabled = pagerScrollEnabled,
                            flingBehavior = pageFling,
                            pageNestedScrollConnection = pageNestedScroll,
                            modifier = Modifier.fillMaxSize().lyricsFrameRate(
                                visible = presentationExpanded,
                                continuousMotion = false,
                                scrolling = pagerState.isScrollInProgress,
                                lightweight = effectivePerformanceMode.isLightweight,
                            ),
                        ) { page ->
                            Box(
                                Modifier.fillMaxSize().nowPlayingPageDepth(
                                    offset = { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction },
                                    cover = page == NowPlayingPage.Cover.ordinal,
                                    lightweight = effectivePerformanceMode.isLightweight,
                                ),
                            ) {
                                if (NowPlayingPage.entries[page] == NowPlayingPage.Cover) coverPage()
                                else lyricsPage(false)
                            }
                        }
                        }
                    },
                )
            }
            }
            }
            SmartTransitionOverlay(
                state = smartTransitionState, positionState = positionState,
                currentTrackId = track?.id, currentArtwork = track?.artworkUri,
                isPlaying = status.isPlaying,
                visible = presentationExpanded && !overlayBlocking && !isRadio && !castActive,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding()
                    .padding(top = 64.dp).widthIn(max = 420.dp).padding(horizontal = 18.dp),
            )
            LyricsSettingsDrawer(
                visible = lyricsSettingsVisible,
                lyricsFontFamily = styledLyricsFont,
                lyricsPageStyle = lyricStyle,
                playerPageStyle = appearance.style,
                lyricsBackdrop = backdropStyle.id,
                onLyricsBackdropChange = onLyricsBackdropChange,
                onLyricsPageStyleChange = { next ->
                    val updated = appearance.copy(style = next)
                    appearanceDraft.current = updated
                    appearance = updated
                    onPlayerAppearanceChange(next, updated.textScale, updated.artworkScale)
                    if (EchoPlayerPageStyle.fromId(next).lyricsPreset.isAfterglow) {
                        lyricsSettingsVisible = false
                        pagerState.requestScrollToPage(NowPlayingPage.Lyrics.ordinal)
                    }
                },
                lyricsFontMode = lyricsFontMode,
                importedFontUri = importedFontUri,
                lyricsFontScale = lyricsFontScale,
                lyricsColorMode = lyricsColorMode,
                lyricsAlignment = lyricsAlignment,
                lyricsLineSpacing = lyricsLineSpacing,
                lyricsBackgroundDim = lyricsBackgroundDim,
                lyricsWordHighlightEnabled = lyricsWordHighlightEnabled,
                lyricsEstimatedWordHighlightEnabled = lyricsEstimatedWordHighlightEnabled,
                lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
                lyricsWordHighlightStyle = lyricsWordHighlightStyle,
                lyricsImmersiveModeEnabled = lyricsImmersiveModeEnabled,
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
                        if (lyricStyle.isAfterglow) pagerState.requestScrollToPage(NowPlayingPage.Cover.ordinal)
                        else pagerState.animateSilkToPage(NowPlayingPage.Cover.ordinal, effectivePerformanceMode.isLightweight)
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
                onLyricsEstimatedWordHighlightEnabledChange = onLyricsEstimatedWordHighlightEnabledChange,
                onLyricsWordHighlightIntensityChange = onLyricsWordHighlightIntensityChange,
                onLyricsWordHighlightStyleChange = onLyricsWordHighlightStyleChange,
                onLyricsImmersiveModeChange = onLyricsImmersiveModeChange,
                onLyricsShowTranslationChange = onLyricsShowTranslationChange,
                onLyricsShowRomanizationChange = onLyricsShowRomanizationChange,
                onLyricsFocusGlowChange = onLyricsFocusGlowChange,
                onShowLyricsControlDeckChange = onShowLyricsControlDeckChange,
                onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
                modifier = Modifier.fillMaxSize(),
            )
            CompositionLocalProvider(
                LocalEchoDarkTheme provides settingsDarkTheme,
                LocalEchoTheme provides settingsTokens,
                LocalContentColor provides settingsColorScheme.onSurface,
            ) {
                MaterialTheme(colorScheme = settingsColorScheme, typography = settingsTypography) {
                    PlaybackSettingsDrawer(
                        songActions = songActions,
                        actionsEnabled = presentationExpanded,
                        onOpenTrackTools = onOpenTrackTools,
                        appearance = appearance,
                        onAppearancePreview = { next ->
                            appearanceDraft.current = next
                            appearance = next
                        },
                        onAppearanceCommit = {
                            val next = appearanceDraft.current
                            onPlayerAppearanceChange(next.style, next.textScale, next.artworkScale)
                        },
                        lyricsBackdrop = backdropStyle.id,
                        onLyricsBackdropChange = onLyricsBackdropChange,
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
        }
    }
    }
}

@Composable
private fun NowPlayingTopBar(
    onDismiss: () -> Unit,
    onHandleDrag: (Float) -> Unit,
    onHandleDragEnd: (Float) -> Unit,
    onHandleDragCancel: () -> Unit,
    currentPage: Int,
    pageCount: Int,
    showPageIndicator: Boolean,
    editorial: Boolean,
    appearance: PlayerAppearance,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    lyricsPage: Boolean = false,
    lyricsReveal: () -> Float = { 0f },
    radioColors: RadioPlayerColors? = null,
    onOpenPlaybackSettings: () -> Unit,
    onCast: (() -> Unit)?,
    castActive: Boolean,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val headerInk = radioColors?.ink ?: if (editorial) appearance.headerInk else OnArt
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .echoDrag(
                orientation = Orientation.Vertical,
                onStart = { onHandleDrag(0f) },
                onDelta = onHandleDrag,
                onStop = onHandleDragEnd,
                onCancel = onHandleDragCancel,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            GlyphButton(
                icon = if (editorial && radioColors == null && appearance.style == "pixel_handheld") PixelPlayerIcons.Collapse
                    else if (editorial || radioColors != null) PlayerControlIcons.Collapse else Icons.Rounded.KeyboardArrowDown,
                description = stringResource(L10nR.string.feature_player_close_player_d23966),
                touchSize = 44.dp,
                iconSize = if (lyricsPage) 26.dp else 30.dp,
                tint = headerInk.copy(alpha = 0.88f),
                background = Color.Transparent,
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            AnimatedContent(
                targetState = editorial to lyricsPage,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { EchoMotion.pageFade(lightweight) },
                label = "player-page-heading",
            ) { (displayedEditorial, displayedLyricsPage) ->
                Box(Modifier.fillMaxSize()) {
                    if (radioColors != null || displayedEditorial || displayedLyricsPage) {
                        Text(
                            if (radioColors != null) stringResource(L10nR.string.radio_player_title)
                            else if (displayedLyricsPage && LocalLyricsPageStyle.current == EchoLyricsPageStyle.Paper) stringResource(L10nR.string.lyrics_page_title) else "ECHO",
                            color = headerInk,
                            fontFamily = if (displayedEditorial && appearance.style == "pixel_handheld") ExpressivePlayerStyle.PixelDisplay else RecordSleeveStyle.BodyFont,
                            fontSize = if (displayedEditorial && appearance.style == "pixel_handheld") 32.sp else 14.sp,
                            letterSpacing = if (radioColors != null) 0.sp else if (displayedEditorial && appearance.style == "pixel_handheld") 1.sp else 3.5.sp,
                            modifier = if (displayedEditorial && appearance.style == "type_poster") Modifier.align(Alignment.CenterStart).padding(start = 46.dp)
                                else Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            if (editorial && appearance.style == "type_poster") {
                GlyphButton(
                    icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    description = stringResource(if (isFavorite) L10nR.string.feature_player_unfavorite_3a27e4 else L10nR.string.feature_player_favorite_b5d1f5),
                    touchSize = 48.dp, iconSize = 28.dp, tint = appearance.headerInk,
                    background = Color.Transparent, onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
                )
            }
            if (lyricsPage) LyricsPageActions(
                onOpenSettings = onOpenPlaybackSettings,
                onCast = onCast,
                castActive = castActive,
                tint = headerInk,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) else GlyphButton(
                icon = if (radioColors != null) Icons.Rounded.MoreVert
                    else if (editorial && appearance.style == "pixel_handheld") PixelPlayerIcons.More else Icons.Rounded.MoreHoriz,
                description = stringResource(L10nR.string.feature_player_expand_playback_settings_cf64a0),
                touchSize = 48.dp,
                iconSize = 26.dp,
                tint = headerInk,
                background = Color.Transparent,
                onClick = onOpenPlaybackSettings,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        // Reserve the indicator strip on both classic pages. Removing it at the
        // pager midpoint used to resize the viewport while it was still sliding.
        if (!appearance.usesFlatSurface && showPageIndicator && pageCount > 1) {
            Row(
                modifier = Modifier.graphicsLayer { alpha = 1f - lyricsReveal().coerceIn(0f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { index ->
                    val selected = index == currentPage
                    val dot = animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = EchoMotion.silkFloat(260),
                        label = "now-playing-page-dot",
                    )
                    val dotColor = OnArt
                    // Width is resolved in layout and tint in draw, so the dots never recompose mid-swipe.
                    Box(
                        modifier = Modifier
                            .layout { measurable, _ ->
                                val width = (6.dp + 10.dp * dot.value.coerceIn(0f, 1f)).roundToPx()
                                val height = 6.dp.roundToPx()
                                val placeable = measurable.measure(Constraints.fixed(width, height))
                                layout(width, height) { placeable.place(0, 0) }
                            }
                            .clip(CircleShape)
                            .drawBehind {
                                val amount = dot.value.coerceIn(0f, 1f)
                                drawRect(dotColor.copy(alpha = 0.26f + 0.64f * amount))
                            },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LyricsSettingsDrawer(
    visible: Boolean,
    lyricsFontFamily: FontFamily?,
    lyricsPageStyle: EchoLyricsPageStyle,
    playerPageStyle: String,
    onLyricsPageStyleChange: (String) -> Unit,
    lyricsBackdrop: String,
    onLyricsBackdropChange: (String) -> Unit,
    lyricsFontMode: String,
    importedFontUri: String?,
    lyricsFontScale: Float,
    lyricsColorMode: String,
    lyricsAlignment: String,
    lyricsLineSpacing: Float,
    lyricsBackgroundDim: Float,
    lyricsWordHighlightEnabled: Boolean,
    lyricsEstimatedWordHighlightEnabled: Boolean,
    lyricsWordHighlightIntensity: Float,
    lyricsWordHighlightStyle: String,
    lyricsImmersiveModeEnabled: Boolean,
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
    onLyricsEstimatedWordHighlightEnabledChange: (Boolean) -> Unit,
    onLyricsWordHighlightIntensityChange: (Float) -> Unit,
    onLyricsWordHighlightStyleChange: (String) -> Unit,
    onLyricsImmersiveModeChange: (Boolean) -> Unit,
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onLyricsFocusGlowChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    EchoSheetOverlay(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        LyricsSettingsPanel(
            lyricsFontFamily = lyricsFontFamily,
            lyricsPageStyle = lyricsPageStyle,
            playerPageStyle = playerPageStyle,
            onLyricsPageStyleChange = onLyricsPageStyleChange,
            lyricsBackdrop = lyricsBackdrop,
            onLyricsBackdropChange = onLyricsBackdropChange,
            lyricsFontMode = lyricsFontMode,
            importedFontUri = importedFontUri,
            lyricsFontScale = lyricsFontScale,
            lyricsColorMode = lyricsColorMode,
            lyricsAlignment = lyricsAlignment,
            lyricsLineSpacing = lyricsLineSpacing,
            lyricsBackgroundDim = lyricsBackgroundDim,
            lyricsWordHighlightEnabled = lyricsWordHighlightEnabled,
            lyricsEstimatedWordHighlightEnabled = lyricsEstimatedWordHighlightEnabled,
            lyricsWordHighlightIntensity = lyricsWordHighlightIntensity,
            lyricsWordHighlightStyle = lyricsWordHighlightStyle,
            lyricsImmersiveModeEnabled = lyricsImmersiveModeEnabled,
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
            onLyricsEstimatedWordHighlightEnabledChange = onLyricsEstimatedWordHighlightEnabledChange,
            onLyricsWordHighlightIntensityChange = onLyricsWordHighlightIntensityChange,
            onLyricsWordHighlightStyleChange = onLyricsWordHighlightStyleChange,
            onLyricsImmersiveModeChange = onLyricsImmersiveModeChange,
            onLyricsShowTranslationChange = onLyricsShowTranslationChange,
            onLyricsShowRomanizationChange = onLyricsShowRomanizationChange,
            onLyricsFocusGlowChange = onLyricsFocusGlowChange,
            onShowLyricsControlDeckChange = onShowLyricsControlDeckChange,
            onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
        )
    }
}

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

@Composable
internal fun LyricsEmptyState(
    message: String,
    onImportLyrics: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EchoIcon(
            Icons.Rounded.Lyrics,
            contentDescription = null,
            tint = OnArtMuted,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = message,
            color = OnArtMuted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
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
                EchoIcon(
                    Icons.Rounded.UploadFile,
                    contentDescription = null,
                    tint = OnArt,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(L10nR.string.feature_player_import_lyrics_e7494e),
                    color = OnArt,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun LyricsControlDeck(
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
                lyrics.metadata["text_encoding"]?.takeIf { it.length <= 40 }?.let { encoding ->
                    FormatChip(text = stringResource(L10nR.string.lyrics_decoding_encoding, encoding), highlight = false)
                    if (lyrics.metadata["text_decoding_lossy"] == "true")
                        FormatChip(text = stringResource(L10nR.string.lyrics_decoding_lossy), highlight = false)
                }
                lyrics.sourceLabel?.takeIf { it.isNotBlank() }?.let { source ->
                    Text(
                        text = source,
                        color = OnArtMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
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
                    fontWeight = FontWeight.Medium,
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
    EchoLyricsFormat.Spl -> "SPL"
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
    EchoLyricsFormat.Sbv -> "SBV"
    EchoLyricsFormat.Sami -> "SAMI"
    EchoLyricsFormat.Json -> "JSON"
}

internal fun formatLyricsOffset(offsetMs: Long): String {
    val sign = when {
        offsetMs > 0L -> "+"
        offsetMs < 0L -> "-"
        else -> ""
    }
    val seconds = abs(offsetMs) / 1000f
    return "$sign${"%.2f".format(seconds)}s"
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
        EchoIcon(
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
                fontWeight = FontWeight.Medium,
            )
            Text(
                detail,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
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
            fontWeight = FontWeight.Medium,
        )
    }
}

internal fun formatSampleRate(hz: Int): String {
    val khzTimes10 = (hz + 50) / 100
    val whole = khzTimes10 / 10
    val frac = khzTimes10 % 10
    return if (frac == 0) "${whole}kHz" else "$whole.${frac}kHz"
}

/** Small icon action with an optional surface for non-transport controls. */
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
            EchoIcon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
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
            EchoIcon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

private class AppearanceDraft(var current: PlayerAppearance)

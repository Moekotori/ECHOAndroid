package app.echo.android.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoDarkGlassBrush
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// 封面毛玻璃背景上的前景色：白色为主，半透明分级
private val OnArt = Color.White
private val OnArtMuted = Color.White.copy(alpha = 0.74f)
private val OnArtFaint = Color.White.copy(alpha = 0.28f)
private val OnArtChip = Color.White.copy(alpha = 0.16f)
private val LyricsSettingsMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

private data class LyricsColorOption(
    val value: String,
    val label: String,
    val color: Color,
)

private val LyricsColorOptions = listOf(
    LyricsColorOption("white", "白", Color.White),
    LyricsColorOption("warm", "暖", Color(0xFFFFD6A0)),
    LyricsColorOption("blue", "蓝", Color(0xFF9ED8FF)),
    LyricsColorOption("violet", "紫", Color(0xFFD9C2FF)),
    LyricsColorOption("mint", "绿", Color(0xFFA9F3D0)),
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
    onImportLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    positionState: PlaybackPositionState? = null,
    lyricsFontFamily: FontFamily? = null,
    lyricsFontMode: String = "outfit",
    lyricsFontScale: Float = 1f,
    lyricsColorMode: String = "white",
    lyricsShowTranslation: Boolean = true,
    lyricsShowRomanization: Boolean = true,
    lyricsFocusGlowEnabled: Boolean = true,
    importedFontUri: String? = null,
    onlineLyricsEnabled: Boolean = false,
    onImportLyricsFont: () -> Unit = {},
    onLyricsFontFamilyChange: (String) -> Unit = {},
    onLyricsFontScaleChange: (Float) -> Unit = {},
    onLyricsColorModeChange: (String) -> Unit = {},
    onLyricsShowTranslationChange: (Boolean) -> Unit = {},
    onLyricsShowRomanizationChange: (Boolean) -> Unit = {},
    onLyricsFocusGlowChange: (Boolean) -> Unit = {},
    onShowLyricsControlDeckChange: (Boolean) -> Unit = {},
    onOnlineLyricsEnabledChange: (Boolean) -> Unit = {},
) {
    val track = status.track
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
                        lyricsShowTranslation = lyricsShowTranslation,
                        lyricsShowRomanization = lyricsShowRomanization,
                        lyricsFocusGlowEnabled = lyricsFocusGlowEnabled,
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
                        onLyricsShowTranslationChange = onLyricsShowTranslationChange,
                        onLyricsShowRomanizationChange = onLyricsShowRomanizationChange,
                        onLyricsFocusGlowChange = onLyricsFocusGlowChange,
                        onShowLyricsControlDeckChange = onShowLyricsControlDeckChange,
                        onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
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
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onLyricsFocusGlowChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readyLyrics = (lyricsState as? EchoLyricsLoadState.Ready)?.lyrics
    var settingsVisible by remember { mutableStateOf(false) }
    val lyricAccent = lyricsColorForMode(lyricsColorMode)
    Box(modifier = modifier.fillMaxWidth()) {
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
                        showTranslation = lyricsShowTranslation,
                        showRomanization = lyricsShowRomanization,
                        focusGlowEnabled = lyricsFocusGlowEnabled,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                    )
                }
            }

            if (showLyricsControlDeck) {
                readyLyrics?.let { lyrics ->
                    LyricsControlDeck(
                        lyrics = lyrics,
                        onImportLyrics = onImportLyrics,
                        onAdjustLyricsOffset = onAdjustLyricsOffset,
                        onResetLyricsOffset = onResetLyricsOffset,
                    )
                    Spacer(Modifier.height(10.dp))
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
                onLeadingAction = { settingsVisible = true },
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onOpenQueue = onOpenQueue,
            )
            Spacer(Modifier.height(14.dp))
        }
        LyricsSettingsDrawer(
            visible = settingsVisible,
            lyricsFontMode = lyricsFontMode,
            importedFontUri = importedFontUri,
            lyricsFontScale = lyricsFontScale,
            lyricsColorMode = lyricsColorMode,
            lyricAccent = lyricAccent,
            showTranslation = lyricsShowTranslation,
            showRomanization = lyricsShowRomanization,
            focusGlowEnabled = lyricsFocusGlowEnabled,
            showLyricsControlDeck = showLyricsControlDeck,
            onlineLyricsEnabled = onlineLyricsEnabled,
            onDismiss = { settingsVisible = false },
            onCloseLyrics = {
                settingsVisible = false
                onCloseLyrics()
            },
            onImportLyrics = onImportLyrics,
            onImportLyricsFont = onImportLyricsFont,
            onLyricsFontFamilyChange = onLyricsFontFamilyChange,
            onLyricsFontScaleChange = onLyricsFontScaleChange,
            onLyricsColorModeChange = onLyricsColorModeChange,
            onLyricsShowTranslationChange = onLyricsShowTranslationChange,
            onLyricsShowRomanizationChange = onLyricsShowRomanizationChange,
            onLyricsFocusGlowChange = onLyricsFocusGlowChange,
            onShowLyricsControlDeckChange = onShowLyricsControlDeckChange,
            onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LyricsSettingsDrawer(
    visible: Boolean,
    lyricsFontMode: String,
    importedFontUri: String?,
    lyricsFontScale: Float,
    lyricsColorMode: String,
    lyricAccent: Color,
    showTranslation: Boolean,
    showRomanization: Boolean,
    focusGlowEnabled: Boolean,
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    onDismiss: () -> Unit,
    onCloseLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onLyricsColorModeChange: (String) -> Unit,
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onLyricsFocusGlowChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 160, easing = LyricsSettingsMotionEasing)),
        exit = fadeOut(tween(durationMillis = 120, easing = LyricsSettingsMotionEasing)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                EchoGlassNight.copy(alpha = 0.22f),
                                EchoGlassInk.copy(alpha = 0.18f),
                                EchoGlassPanel.copy(alpha = 0.26f),
                            ),
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(durationMillis = 420, easing = LyricsSettingsMotionEasing)) { it } +
                    fadeIn(tween(durationMillis = 220, delayMillis = 40, easing = LyricsSettingsMotionEasing)) +
                    scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(durationMillis = 420, easing = LyricsSettingsMotionEasing),
                    ),
                exit = slideOutVertically(tween(durationMillis = 210, easing = LyricsSettingsMotionEasing)) { it / 2 } +
                    fadeOut(tween(durationMillis = 120, easing = LyricsSettingsMotionEasing)) +
                    scaleOut(
                        targetScale = 0.99f,
                        animationSpec = tween(durationMillis = 210, easing = LyricsSettingsMotionEasing),
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LyricsSettingsPanel(
                    lyricsFontMode = lyricsFontMode,
                    importedFontUri = importedFontUri,
                    lyricsFontScale = lyricsFontScale,
                    lyricsColorMode = lyricsColorMode,
                    lyricAccent = lyricAccent,
                    showTranslation = showTranslation,
                    showRomanization = showRomanization,
                    focusGlowEnabled = focusGlowEnabled,
                    showLyricsControlDeck = showLyricsControlDeck,
                    onlineLyricsEnabled = onlineLyricsEnabled,
                    onDismiss = onDismiss,
                    onCloseLyrics = onCloseLyrics,
                    onImportLyrics = onImportLyrics,
                    onImportLyricsFont = onImportLyricsFont,
                    onLyricsFontFamilyChange = onLyricsFontFamilyChange,
                    onLyricsFontScaleChange = onLyricsFontScaleChange,
                    onLyricsColorModeChange = onLyricsColorModeChange,
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
    lyricAccent: Color,
    showTranslation: Boolean,
    showRomanization: Boolean,
    focusGlowEnabled: Boolean,
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    onDismiss: () -> Unit,
    onCloseLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onLyricsColorModeChange: (String) -> Unit,
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onLyricsFocusGlowChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
) {
    val scale = lyricsFontScale.coerceIn(0.82f, 1.28f)
    val fontFraction = ((scale - 0.82f) / (1.28f - 0.82f)).coerceIn(0f, 1f)
    val dark = LocalEchoDarkTheme.current
    val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val titleColor = if (dark) Color.White else Color(0xFF101722)
    val mutedColor = if (dark) Color.White.copy(alpha = 0.62f) else Color(0xFF647084)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .clip(panelShape)
            .background(
                if (dark) {
                    echoDarkGlassBrush(1.18f)
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFCFCFF).copy(alpha = 0.96f),
                            Color(0xFFEFF5FF).copy(alpha = 0.94f),
                        ),
                    )
                },
            )
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.White.copy(alpha = 0.76f)),
                panelShape,
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                Text("字体、颜色和显示方式", color = mutedColor, style = MaterialTheme.typography.bodySmall)
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

        LyricsSettingsSection(icon = Icons.Rounded.TextFields, title = "字体", detail = lyricsFontDetail(lyricsFontMode, importedFontUri)) {
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

        LyricsSettingsSection(
            icon = Icons.Rounded.FormatSize,
            title = "字号",
            detail = "${(scale * 100f).roundToInt()}%",
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
                inactiveColor = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFB8C2D6).copy(alpha = 0.55f),
                thumbColor = Color.White,
            )
        }

        LyricsSettingsSection(icon = Icons.Rounded.ColorLens, title = "颜色", detail = lyricsColorLabel(lyricsColorMode)) {
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

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LyricsToggleTile("翻译", showTranslation, lyricAccent, Modifier.weight(1f)) {
                onLyricsShowTranslationChange(!showTranslation)
            }
            LyricsToggleTile("罗马音", showRomanization, lyricAccent, Modifier.weight(1f)) {
                onLyricsShowRomanizationChange(!showRomanization)
            }
            LyricsToggleTile("发光", focusGlowEnabled, lyricAccent, Modifier.weight(1f)) {
                onLyricsFocusGlowChange(!focusGlowEnabled)
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
                .background(if (dark) EchoGlassPanel.copy(alpha = 0.34f) else Color(0xFF101722).copy(alpha = 0.06f))
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
    content: @Composable () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val titleColor = if (dark) Color.White else Color(0xFF101722)
    val mutedColor = if (dark) Color.White.copy(alpha = 0.62f) else Color(0xFF647084)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (dark) EchoGlassPanel.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.58f))
            .border(if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)), RoundedCornerShape(20.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, contentDescription = null, tint = Color(0xFF4F62F4), modifier = Modifier.size(19.dp))
            Text(title, color = titleColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(detail, color = mutedColor, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        content()
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
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (selected) {
                    accent.copy(alpha = if (dark) 0.24f else 0.28f)
                } else {
                    if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFF101722).copy(alpha = 0.06f)
                },
            )
            .border(
                BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.46f) else if (dark) EchoDarkGlassBorder else Color.White.copy(alpha = 0.62f)),
                RoundedCornerShape(99.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        color = if (dark) Color.White else Color(0xFF101722),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun LyricsColorSwatch(
    option: LyricsColorOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) option.color.copy(alpha = 0.26f) else if (dark) EchoGlassPanel.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.48f))
            .border(
                BorderStroke(1.dp, if (selected) option.color.copy(alpha = 0.80f) else if (dark) EchoDarkGlassBorder else Color.White.copy(alpha = 0.68f)),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(option.color)
                .border(BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.08f)), CircleShape),
        )
        Text(option.label, color = if (dark) Color.White.copy(alpha = 0.86f) else Color(0xFF253142), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LyricsToggleTile(
    title: String,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = modifier
            .height(66.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) accent.copy(alpha = 0.22f) else if (dark) EchoGlassPanel.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.48f))
            .border(
                BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.38f) else if (dark) EchoDarkGlassBorder else Color.White.copy(alpha = 0.66f)),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = if (dark) Color.White else Color(0xFF253142), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        Text(if (enabled) "开启" else "关闭", color = if (dark) Color.White.copy(alpha = 0.58f) else Color(0xFF647084), style = MaterialTheme.typography.labelSmall)
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
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White.copy(alpha = if (dark) 0.16f else 0.12f) else if (dark) EchoGlassPanel.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.54f))
            .border(if (dark) echoDarkGlassBorder(selected) else BorderStroke(1.dp, Color.White.copy(alpha = 0.68f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) Color.White.copy(alpha = 0.84f) else Color(0xFF253142), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(title, color = if (dark) Color.White.copy(alpha = 0.88f) else Color(0xFF253142), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun lyricsColorForMode(mode: String): Color =
    LyricsColorOptions.firstOrNull { it.value == mode }?.color ?: Color.White

private fun lyricsColorLabel(mode: String): String =
    LyricsColorOptions.firstOrNull { it.value == mode }?.label ?: "白"

private fun lyricsFontOptions(importedFontUri: String?): List<Pair<String, String>> = buildList {
    add("outfit" to "Outfit")
    add("system" to "系统")
    add("serif" to "衬线")
    add("monospace" to "等宽")
    add("imported" to if (importedFontUri.isNullOrBlank()) "导入" else "导入")
}

private fun lyricsFontDetail(mode: String, importedFontUri: String?): String =
    when (mode) {
        "outfit" -> "内置 Outfit"
        "system" -> "系统字体"
        "serif" -> "系统衬线"
        "monospace" -> "系统等宽"
        "imported" -> importedFontUri?.substringAfterLast('/')?.takeLast(18)?.let { "导入 $it" } ?: "选择字体文件"
        else -> "内置 Outfit"
    }

@Composable
private fun LyricsLineList(
    lyrics: EchoLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    lyricsFontFamily: FontFamily?,
    lyricsFontScale: Float,
    lyricAccent: Color,
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

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = maxHeight * 0.54f,
                bottom = maxHeight * 0.46f,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
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
                    1 -> 0.62f
                    2 -> 0.36f
                    3 -> 0.20f
                    else -> 0.10f
                }
                val secondaryAlpha = when (focusDistance) {
                    0 -> 0.76f
                    1 -> 0.44f
                    2 -> 0.26f
                    3 -> 0.15f
                    else -> 0.08f
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (seekable) {
                                Modifier.clickable { onSeek(line.startMs) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val activeShadow = if (active && focusGlowEnabled) {
                        Shadow(
                            color = lyricAccent.copy(alpha = 0.34f),
                            offset = Offset.Zero,
                            blurRadius = 18f,
                        )
                    } else {
                        null
                    }
                    Text(
                        text = line.displayText(active = active, positionMs = positionMs, activeColor = lyricAccent),
                        modifier = Modifier.fillMaxWidth(),
                        color = lyricAccent.copy(alpha = primaryAlpha),
                        style = if (active) {
                            MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = lyricsFontFamily,
                                fontSize = (31f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                                lineHeight = (40f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                                shadow = activeShadow,
                            )
                        } else {
                            MaterialTheme.typography.titleLarge.copy(
                                fontFamily = lyricsFontFamily,
                                fontSize = (22f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                                lineHeight = (30f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                            )
                        },
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
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
                                    fontSize = (15f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                                    lineHeight = (22f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = lyricsFontFamily,
                                    fontSize = (13f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                                    lineHeight = (20f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                                )
                            },
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    line.romanization?.takeIf { showRomanization && it.isNotBlank() }?.let { romanization ->
                        Text(
                            text = romanization,
                            modifier = Modifier.fillMaxWidth(),
                            color = lyricAccent.copy(alpha = secondaryAlpha * 0.82f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = lyricsFontFamily,
                                fontSize = (12f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                                lineHeight = (18f * lyricsFontScale.coerceIn(0.82f, 1.28f)).sp,
                            ),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun EchoLyricLine.displayText(active: Boolean, positionMs: Long, activeColor: Color) =
    if (!active || words.isEmpty()) {
        buildAnnotatedString { append(text) }
    } else {
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                val nextStartMs = words.getOrNull(index + 1)?.startMs
                val endMs = word.endMs ?: nextStartMs ?: this@displayText.endMs ?: Long.MAX_VALUE
                val isCurrentWord = positionMs in word.startMs until endMs
                val color = if (isCurrentWord) activeColor else activeColor.copy(alpha = 0.62f)
                pushStyle(
                    SpanStyle(
                        color = color,
                        fontWeight = if (isCurrentWord) FontWeight.Bold else FontWeight.Medium,
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
            fontWeight = FontWeight.SemiBold,
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
                    fontWeight = FontWeight.SemiBold,
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
            .background(Color.White.copy(alpha = 0.12f))
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
                        fontWeight = FontWeight.Medium,
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
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = OnArt,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                album?.takeIf { it.isNotBlank() }?.let { value ->
                    Text(
                        value,
                        modifier = Modifier.clickable(onClick = onOpenAlbum),
                        color = Color.White.copy(alpha = 0.56f),
                        style = MaterialTheme.typography.bodySmall,
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
                    description = "更多",
                    touchSize = 40.dp,
                    iconSize = 21.dp,
                    tint = Color.White.copy(alpha = 0.88f),
                    background = Color.White.copy(alpha = 0.11f),
                    onClick = {},
                )
            }
        }
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
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
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
            .background(if (highlight) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (highlight) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
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
                color = Color.White.copy(alpha = 0.64f),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                "-" + formatDuration(remainingMs),
                color = Color.White.copy(alpha = 0.64f),
                style = MaterialTheme.typography.labelSmall,
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            icon = leadingIcon,
            description = leadingDescription,
            touchSize = 44.dp,
            iconSize = 24.dp,
            tint = Color.White.copy(alpha = 0.70f),
            background = Color.Transparent,
            onClick = onLeadingAction,
        )
        GlyphButton(
            icon = Icons.Rounded.SkipPrevious,
            description = "上一首",
            touchSize = 54.dp,
            iconSize = 34.dp,
            tint = OnArt,
            background = Color.White.copy(alpha = 0.12f),
            onClick = onPrevious,
        )
        GlyphButton(
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            description = "播放或暂停",
            touchSize = 76.dp,
            iconSize = 42.dp,
            tint = Color(0xFF15161A),
            background = Color.White.copy(alpha = 0.96f),
            onClick = onPlayPause,
        )
        GlyphButton(
            icon = Icons.Rounded.SkipNext,
            description = "下一首",
            touchSize = 54.dp,
            iconSize = 34.dp,
            tint = OnArt,
            background = Color.White.copy(alpha = 0.12f),
            onClick = onNext,
        )
        GlyphButton(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            description = "播放队列",
            touchSize = 44.dp,
            iconSize = 24.dp,
            tint = Color.White.copy(alpha = 0.70f),
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
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(touchSize)
            .clip(CircleShape)
            .background(background)
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

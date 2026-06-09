package app.echo.android.feature.player

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.BlurredArtworkBackground
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

private enum class NowPlayingPage {
    Back,
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
    onImportLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    positionState: PlaybackPositionState? = null,
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

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage == NowPlayingPage.Back.ordinal) {
            onDismiss()
        }
    }

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
                        0f to Color.Black.copy(alpha = 0.44f - 0.12f * lyricsReveal),
                        0.48f to Color.Black.copy(alpha = 0.18f - 0.08f * lyricsReveal),
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
                    NowPlayingPage.Back -> Spacer(Modifier.fillMaxSize())
                    NowPlayingPage.Cover -> NowPlayingCoverPage(
                        status = status,
                        positionMs = activePositionMs,
                        durationMs = activeDurationMs,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
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
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        positionMs = activePositionMs,
                        durationMs = activeDurationMs,
                        onCloseLyrics = {
                            pageScope.launch {
                                pagerState.animateScrollToPage(NowPlayingPage.Cover.ordinal)
                            }
                        },
                        onImportLyrics = onImportLyrics,
                        onAdjustLyricsOffset = onAdjustLyricsOffset,
                        onResetLyricsOffset = onResetLyricsOffset,
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
            onOpenQueue = {},
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
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    positionMs: Long,
    durationMs: Long,
    onCloseLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readyLyrics = (lyricsState as? EchoLyricsLoadState.Ready)?.lyrics
    Column(
        modifier = modifier.fillMaxWidth(),
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
            leadingIcon = Icons.Rounded.Album,
            leadingDescription = "返回封面",
            onLeadingAction = onCloseLyrics,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onOpenQueue = {},
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun LyricsLineList(
    lyrics: EchoLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
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
                    1 -> 0.56f
                    2 -> 0.30f
                    3 -> 0.16f
                    else -> 0.08f
                }
                val secondaryAlpha = when (focusDistance) {
                    0 -> 0.72f
                    1 -> 0.38f
                    2 -> 0.22f
                    3 -> 0.12f
                    else -> 0.06f
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
                    Text(
                        text = line.displayText(active = active, positionMs = positionMs),
                        modifier = Modifier.fillMaxWidth(),
                        color = OnArt.copy(alpha = primaryAlpha),
                        style = if (active) {
                            MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp)
                        } else {
                            MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp)
                        },
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = if (active) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    line.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                        Text(
                            text = translation,
                            modifier = Modifier.fillMaxWidth(),
                            color = OnArt.copy(alpha = secondaryAlpha),
                            style = if (active) {
                                MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp)
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
                            },
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    line.romanization?.takeIf { it.isNotBlank() }?.let { romanization ->
                        Text(
                            text = romanization,
                            modifier = Modifier.fillMaxWidth(),
                            color = OnArt.copy(alpha = secondaryAlpha * 0.82f),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
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

private fun EchoLyricLine.displayText(active: Boolean, positionMs: Long) =
    if (!active || words.isEmpty()) {
        buildAnnotatedString { append(text) }
    } else {
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                val nextStartMs = words.getOrNull(index + 1)?.startMs
                val endMs = word.endMs ?: nextStartMs ?: this@displayText.endMs ?: Long.MAX_VALUE
                val isCurrentWord = positionMs in word.startMs until endMs
                val color = if (isCurrentWord) OnArt else Color.White.copy(alpha = 0.62f)
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlight) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
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
            touchSize = 50.dp,
            iconSize = 34.dp,
            tint = OnArt,
            background = Color.Transparent,
            onClick = onPrevious,
        )
        GlyphButton(
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            description = "播放或暂停",
            touchSize = 72.dp,
            iconSize = 54.dp,
            tint = OnArt,
            background = Color.Transparent,
            onClick = onPlayPause,
        )
        GlyphButton(
            icon = Icons.Rounded.SkipNext,
            description = "下一首",
            touchSize = 50.dp,
            iconSize = 34.dp,
            tint = OnArt,
            background = Color.Transparent,
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

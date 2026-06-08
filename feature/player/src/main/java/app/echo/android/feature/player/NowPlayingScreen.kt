package app.echo.android.feature.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.BlurredArtworkBackground
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import kotlin.math.roundToInt

// 封面毛玻璃背景上的前景色：白色为主，半透明分级
private val OnArt = Color.White
private val OnArtMuted = Color.White.copy(alpha = 0.74f)
private val OnArtFaint = Color.White.copy(alpha = 0.28f)
private val OnArtChip = Color.White.copy(alpha = 0.16f)

@Composable
fun NowPlayingScreen(
    status: EchoPlaybackStatus,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val palette = rememberArtworkPalette(track?.artworkUri, seedKey = track?.id)

    Box(modifier = modifier.fillMaxSize()) {
        BlurredArtworkBackground(
            artworkUri = track?.artworkUri,
            palette = palette,
            modifier = Modifier.fillMaxSize(),
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

            Spacer(Modifier.height(6.dp))
            val artScale by animateFloatAsState(
                targetValue = if (status.isPlaying) 1f else 0.95f,
                animationSpec = tween(durationMillis = 420),
                label = "art-scale",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArtworkTile(
                    artworkUri = track?.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .scale(artScale),
                    accent = palette.vibrant,
                    showSignal = track?.artworkUri == null,
                    cornerRadius = 26.dp,
                    elevation = 28.dp,
                )
            }

            Spacer(Modifier.height(18.dp))
            NowPlayingTrackInfo(
                title = track?.title ?: "未在播放",
                artist = track?.artist ?: "选择一首歌开始",
                album = track?.album,
            )

            Spacer(Modifier.height(14.dp))
            NowPlayingScrubber(
                positionMs = status.positionMs,
                durationMs = status.durationMs,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(8.dp))
            NowPlayingTransport(
                isPlaying = status.isPlaying,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
            )

            Spacer(Modifier.height(16.dp))
            NowPlayingVolume()

            Spacer(Modifier.height(14.dp))
            NowPlayingSecondaryControls(
                shuffleEnabled = status.shuffleEnabled,
                repeatMode = status.repeatMode,
                onCyclePlayMode = onCyclePlayMode,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NowPlayingTopBar(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 38.dp, height = 5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                description = "收起",
                touchSize = 40.dp,
                iconSize = 24.dp,
                tint = OnArt,
                background = OnArtChip,
                onClick = onDismiss,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "正在播放",
                color = OnArtMuted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun NowPlayingTrackInfo(title: String, artist: String, album: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                color = OnArt,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist,
                color = OnArtMuted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            album?.takeIf { it.isNotBlank() }?.let { value ->
                Text(
                    value,
                    color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        GlyphButton(
            icon = Icons.Rounded.StarBorder,
            description = "收藏",
            touchSize = 42.dp,
            iconSize = 22.dp,
            tint = OnArt,
            background = OnArtChip,
            onClick = {},
        )
        Spacer(Modifier.width(8.dp))
        GlyphButton(
            icon = Icons.Rounded.MoreHoriz,
            description = "更多",
            touchSize = 42.dp,
            iconSize = 22.dp,
            tint = OnArt,
            background = OnArtChip,
            onClick = {},
        )
    }
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
            onValueChangeFinished = {
                scrubFraction?.let { fraction ->
                    if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                }
                scrubFraction = null
            },
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(currentMs),
                color = OnArtMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "-" + formatDuration(remainingMs),
                color = OnArtMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun NowPlayingTransport(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            icon = Icons.Rounded.FastRewind,
            description = "上一首",
            touchSize = 64.dp,
            iconSize = 46.dp,
            tint = OnArt,
            background = Color.Transparent,
            onClick = onPrevious,
        )
        GlyphButton(
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            description = "播放或暂停",
            touchSize = 88.dp,
            iconSize = 68.dp,
            tint = OnArt,
            background = Color.Transparent,
            onClick = onPlayPause,
        )
        GlyphButton(
            icon = Icons.Rounded.FastForward,
            description = "下一首",
            touchSize = 64.dp,
            iconSize = 46.dp,
            tint = OnArt,
            background = Color.Transparent,
            onClick = onNext,
        )
    }
}

@Composable
private fun NowPlayingVolume() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volumeFraction by remember {
        mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeDown,
            contentDescription = null,
            tint = OnArtMuted,
            modifier = Modifier.size(20.dp),
        )
        ThinSlider(
            fraction = volumeFraction,
            onValueChange = { fraction ->
                volumeFraction = fraction
                val target = (fraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
            },
            onValueChangeFinished = {},
            modifier = Modifier.weight(1f),
            trackHeight = 5.dp,
            thumbSize = 12.dp,
        )
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = OnArtMuted,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun NowPlayingSecondaryControls(
    shuffleEnabled: Boolean,
    repeatMode: EchoRepeatMode,
    onCyclePlayMode: () -> Unit,
) {
    val orderIcon = when {
        shuffleEnabled -> Icons.Rounded.Shuffle
        repeatMode == EchoRepeatMode.One -> Icons.Rounded.RepeatOne
        repeatMode == EchoRepeatMode.All -> Icons.Rounded.Repeat
        else -> Icons.AutoMirrored.Rounded.PlaylistPlay
    }
    val orderLabel = when {
        shuffleEnabled -> "随机播放"
        repeatMode == EchoRepeatMode.One -> "单曲循环"
        repeatMode == EchoRepeatMode.All -> "列表循环"
        else -> "顺序播放"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左：Signal Path 占位（暂不可点）
        SignalPathIndicator()
        // 中：单按钮循环切换播放顺序
        GlyphButton(
            icon = orderIcon,
            description = orderLabel,
            touchSize = 52.dp,
            iconSize = 30.dp,
            tint = OnArt,
            background = Color.Transparent,
            onClick = onCyclePlayMode,
        )
        // 右：播放队列
        GlyphButton(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            description = "播放队列",
            touchSize = 48.dp,
            iconSize = 28.dp,
            tint = OnArtMuted,
            background = Color.Transparent,
            onClick = {},
        )
    }
}

@Composable
private fun SignalPathIndicator() {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Polyline,
            contentDescription = "Signal Path（信号链路）",
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(28.dp),
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
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 6.dp,
    thumbSize: Dp = 13.dp,
    activeColor: Color = Color.White,
    inactiveColor: Color = OnArtFaint,
    thumbColor: Color = Color.White,
) {
    val f = fraction.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
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

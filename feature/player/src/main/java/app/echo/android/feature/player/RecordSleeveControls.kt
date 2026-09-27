package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.radio.EchoRadioStation

@Composable
internal fun RecordSleeveTransport(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: EchoRepeatMode,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val haptics = rememberEchoHapticPerformer()
    Row(
        Modifier.fillMaxWidth().height(78.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordSleeveIconButton(
            Icons.Outlined.Shuffle, stringResource(R.string.feature_player_shuffle),
            onClick = onToggleShuffle, selected = shuffleEnabled,
        )
        RecordSleeveIconButton(
            PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264),
            onClick = { haptics.tick(); onPrevious() }, iconSize = 34.dp,
        )
        IconButton(
            onClick = { haptics.confirm(); onPlayPause() },
            modifier = Modifier.size(64.dp).background(RecordSleeveStyle.Wine, CircleShape),
        ) {
            Icon(
                if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
                contentDescription = stringResource(R.string.feature_player_play_or_pause_37a70f),
                tint = RecordSleeveStyle.Paper,
                modifier = Modifier.size(36.dp),
            )
        }
        RecordSleeveIconButton(
            PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904),
            onClick = { haptics.tick(); onNext() }, iconSize = 34.dp,
        )
        RecordSleeveIconButton(
            if (repeatMode == EchoRepeatMode.One) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
            stringResource(when (repeatMode) {
                EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
            }),
            onClick = onCycleRepeatMode, selected = repeatMode != EchoRepeatMode.Off,
        )
    }
}

@Composable
internal fun RecordSleeveUtilities(onOpenQueue: () -> Unit, onCast: (() -> Unit)?, castActive: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordSleeveIconButton(
            Icons.AutoMirrored.Outlined.QueueMusic, stringResource(R.string.feature_player_queue_37fa6a),
            onClick = onOpenQueue,
        )
        if (onCast != null) {
            RecordSleeveIconButton(
                PlayerControlIcons.Cast,
                stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast),
                onClick = onCast, selected = castActive,
            )
        }
    }
}

@Composable
internal fun RecordSleeveIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 25.dp,
    selected: Boolean? = null,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp).semantics { selected?.let { this.selected = it } },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, contentDescription = description,
                tint = RecordSleeveStyle.Wine.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(iconSize),
            )
            if (selected != null) {
                Box(Modifier.size(3.dp).background(
                    if (selected) RecordSleeveStyle.Wine else Color.Transparent, CircleShape,
                ))
            }
        }
    }
}

/** Only this leaf reads the playback clock; scrubbing never rebuilds the cover. */
@Composable
internal fun RecordSleeveScrubber(
    trackKey: String?,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onSeek: (Long) -> Unit,
) {
    if (EchoRadioStation.isRadio(trackKey)) {
        Text(stringResource(R.string.radio_live), color = RecordSleeveStyle.Ink)
        return
    }
    val position = positionMsState.value
    val duration = durationMsState.value
    var scrubFraction by remember(trackKey, duration) { mutableStateOf<Float?>(null) }
    val shown = scrubFraction ?: progressFraction(position, duration)
    val shownMs = if (duration > 0) (shown * duration).toLong() else position
    Column(Modifier.fillMaxWidth()) {
        RecordSleeveSeekBar(
            trackKey = trackKey,
            fraction = shown,
            enabled = duration > 0,
            onPreview = { scrubFraction = it },
            onCommit = {
                if (duration > 0) onSeek((it * duration).toLong())
                scrubFraction = null
            },
            onCancel = { scrubFraction = null },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownMs), color = RecordSleeveStyle.Ink,
                fontFamily = RecordSleeveStyle.BodyFont, fontSize = 12.sp, lineHeight = 16.sp)
            Text("-" + formatDuration((duration - shownMs).coerceAtLeast(0)), color = RecordSleeveStyle.Ink,
                fontFamily = RecordSleeveStyle.BodyFont, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** One drawing origin keeps the thumb exactly centered on the thin track. */
@Composable
private fun RecordSleeveSeekBar(
    trackKey: String?,
    fraction: Float,
    enabled: Boolean,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
) {
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    Canvas(Modifier.fillMaxWidth().height(32.dp)
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
            if (!enabled) disabled()
            setProgress {
                if (enabled) commit(it.coerceIn(0f, 1f))
                enabled
            }
        }
        .pointerInput(trackKey, enabled) {
            if (enabled) detectTapGestures {
                commit((it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
            }
        }
        .pointerInput(trackKey, enabled) {
            if (enabled) {
                var target = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        target = (it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        preview(target)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        target = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        preview(target)
                    },
                    onDragEnd = { commit(target) },
                    onDragCancel = { cancel() },
                )
            }
        }) {
        val x = size.width * fraction.coerceIn(0f, 1f)
        val wine = if (enabled) RecordSleeveStyle.Wine else RecordSleeveStyle.Track
        drawLine(RecordSleeveStyle.Track, Offset(0f, center.y), Offset(size.width, center.y), 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(wine, Offset(0f, center.y), Offset(x, center.y), 1.5.dp.toPx(), StrokeCap.Round)
        drawCircle(wine, 5.dp.toPx(), Offset(x, center.y))
    }
}

package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import app.echo.android.design.drawEchoControlRail
import app.echo.android.design.drawEchoControlThumb
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
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val haptics = rememberEchoHapticPerformer()
    val mode = QueuePlaybackMode.from(repeatMode, shuffleEnabled)
    Row(
        Modifier.fillMaxWidth().height(78.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordSleeveIconButton(
            mode.icon(), stringResource(mode.label()),
            onClick = onCyclePlaybackMode, selected = mode != QueuePlaybackMode.Sequential, iconSize = 20.dp,
            animateMode = true,
        )
        RecordSleeveIconButton(
            PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264),
            onClick = { haptics.tick(); onPrevious() }, iconSize = 28.dp,
        )
        PlayerControlButton(
            icon = if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
            description = stringResource(R.string.feature_player_play_or_pause_37a70f),
            onClick = { haptics.confirm(); onPlayPause() },
            touchSize = 64.dp, iconSize = 48.dp, tint = RecordSleeveStyle.Wine,
        )
        RecordSleeveIconButton(
            PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904),
            onClick = { haptics.tick(); onNext() }, iconSize = 28.dp,
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
internal fun RecordSleeveUtilities(onOpenQueue: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordSleeveIconButton(
            Icons.AutoMirrored.Outlined.QueueMusic, stringResource(R.string.feature_player_queue_37fa6a),
            onClick = onOpenQueue, iconSize = 20.dp,
        )
        RecordSleeveIconButton(
            PlayerControlIcons.More,
            stringResource(R.string.feature_player_expand_playback_settings_cf64a0),
            onClick = onMore, iconSize = 22.dp,
        )
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
    animateMode: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp).semantics { selected?.let { this.selected = it } },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PlaybackModeIcon(
                icon, description, RecordSleeveStyle.Wine.copy(alpha = if (enabled) 1f else 0.38f),
                Modifier.size(iconSize), animateMode,
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
            durationMs = duration,
            fraction = shown,
            engaged = scrubFraction != null,
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
    durationMs: Long,
    fraction: Float,
    engaged: Boolean,
    enabled: Boolean,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val face = RecordSleeveStyle.Paper
    Canvas(Modifier.fillMaxWidth().height(48.dp)
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
            if (!enabled) disabled()
            setProgress {
                if (enabled) onCommit(it.coerceIn(0f, 1f))
                enabled
            }
        }
        .playerSeekInput(
            trackKey = trackKey, durationMs = durationMs, enabled = enabled, inset = 12.dp, rtl = rtl,
            onPreview = onPreview, onCommit = onCommit, onCancel = onCancel,
        )) {
        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
            val inset = 12.dp.toPx().coerceAtMost(size.width / 2f)
            val left = Offset(inset, center.y)
            val right = Offset(size.width - inset, center.y)
            val thumb = Offset(inset + (size.width - inset * 2f) * fraction.coerceIn(0f, 1f), center.y)
            val accent = if (enabled) RecordSleeveStyle.Wine else RecordSleeveStyle.Track
            drawEchoControlRail(left, right, left, thumb, accent, RecordSleeveStyle.Track)
            drawEchoControlThumb(thumb, accent, face, engaged, enabled)
        }
    }
}

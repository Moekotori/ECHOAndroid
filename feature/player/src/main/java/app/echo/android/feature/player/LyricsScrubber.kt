package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.android.design.drawEchoControlRail
import app.echo.android.design.drawEchoControlThumb
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.radio.EchoRadioStation

/** Keep seek previews local; the player's position remains the source of truth. */
@Composable
internal fun LyricsScrubber(
    trackKey: String?,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onSeek: (Long) -> Unit,
) {
    if (EchoRadioStation.isRadio(trackKey)) {
        RadioPlaybackProgress()
        return
    }
    val position = positionMsState.value
    val duration = durationMsState.value
    var preview by remember(trackKey, duration) { mutableStateOf<Float?>(null) }
    val fraction = preview ?: progressFraction(position, duration)
    val shownPosition = if (duration > 0L) (fraction * duration).toLong() else position
    Column(Modifier.fillMaxWidth()) {
        LyricsSeekBar(
            trackKey = trackKey,
            fraction = fraction,
            engaged = preview != null,
            enabled = duration > 0L,
            onPreview = { preview = it },
            onCommit = { target ->
                if (duration > 0L) onSeek((target * duration).toLong())
                preview = null
            },
            onCancel = { preview = null },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownPosition), color = OnArtMuted, style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(duration), color = OnArtMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LyricsSeekBar(
    trackKey: String?,
    fraction: Float,
    engaged: Boolean,
    enabled: Boolean,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
) {
    val haptics by rememberUpdatedState(rememberEchoHapticPerformer())
    val preview by rememberUpdatedState<(Float) -> Unit>({ onPreview(it); haptics.seek(it) })
    val commit by rememberUpdatedState<(Float) -> Unit>({ onCommit(it); haptics.endSeek(committed = true) })
    val cancel by rememberUpdatedState<() -> Unit>({ onCancel(); haptics.endSeek(committed = false) })
    val face = MaterialTheme.colorScheme.surface
    val played = MaterialTheme.colorScheme.primary
    val unplayed = OnArt.copy(alpha = 0.20f)
    Canvas(Modifier.fillMaxWidth().height(48.dp)
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
                commit(((it.x - 12.dp.toPx()) / (size.width - 24.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f))
            }
        }
        .pointerInput(trackKey, enabled) {
            if (enabled) {
                var target = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        haptics.grab()
                        target = ((it.x - 12.dp.toPx()) / (size.width - 24.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        preview(target)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        target = ((change.position.x - 12.dp.toPx()) / (size.width - 24.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        preview(target)
                    },
                    onDragEnd = { commit(target) },
                    onDragCancel = { cancel() },
                )
            }
        }) {
        val inset = 12.dp.toPx().coerceAtMost(size.width / 2f)
        val left = Offset(inset, center.y)
        val right = Offset(size.width - inset, center.y)
        val thumb = Offset(inset + (size.width - inset * 2f) * fraction.coerceIn(0f, 1f), center.y)
        val accent = if (enabled) played else played.copy(alpha = 0.38f)
        drawEchoControlRail(left, right, left, thumb, accent, unplayed)
        drawEchoControlThumb(thumb, accent, face, engaged, enabled)
    }
}

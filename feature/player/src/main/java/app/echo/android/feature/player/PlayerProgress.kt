package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.echo.android.design.drawEchoControlRail
import app.echo.android.design.drawEchoControlThumb
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.radio.EchoRadioStation

/** Clock reads stay in this leaf; seeking never rebuilds the cover or control row. */
@Composable
internal fun NowPlayingScrubber(
    trackKey: String?,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onSeek: (Long) -> Unit,
) {
    if (EchoRadioStation.isRadio(trackKey)) {
        RadioPlaybackProgress()
        return
    }
    val positionMs = positionMsState.value
    val durationMs = durationMsState.value
    var scrubFraction by remember(trackKey, durationMs) { mutableStateOf<Float?>(null) }
    val shown = scrubFraction ?: progressFraction(positionMs, durationMs)
    val currentMs = if (durationMs > 0L) (shown * durationMs).toLong() else positionMs
    val remainingMs = (durationMs - currentMs).coerceAtLeast(0L)

    Column(Modifier.fillMaxWidth()) {
        LinearPlayerSeekBar(
            trackKey = trackKey,
            fraction = shown,
            engaged = scrubFraction != null,
            enabled = durationMs > 0L,
            onPreview = { scrubFraction = it },
            onCommit = { fraction ->
                if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                scrubFraction = null
            },
            onCancel = { scrubFraction = null },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(currentMs), color = OnArtMuted,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            Text("-" + formatDuration(remainingMs), color = OnArtMuted,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

/** Shared rail and thumb treatment, inside a full 48 dp seek target. */
@Composable
private fun LinearPlayerSeekBar(
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
    val unplayed = OnArt.copy(alpha = 0.10f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(48.dp)
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
            if (!enabled) disabled()
            setProgress { value ->
                if (enabled) commit(value.coerceIn(0f, 1f))
                enabled
            }
        }
        .pointerInput(trackKey, enabled, rtl) {
            if (enabled) detectTapGestures { point ->
                val inset = 12.dp.toPx()
                val normalized = ((point.x - inset) / (size.width - 2f * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                commit(if (rtl) 1f - normalized else normalized)
            }
        }
        .pointerInput(trackKey, enabled, rtl) {
            if (enabled) {
                var target = 0f
                fun fractionAt(x: Float): Float {
                    val inset = 12.dp.toPx()
                    val normalized = ((x - inset) / (size.width - 2f * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    return if (rtl) 1f - normalized else normalized
                }
                detectDragGestures(
                    orientationLock = Orientation.Horizontal,
                    onDragStart = { _, change, _ -> haptics.grab(); target = fractionAt(change.position.x); preview(target) },
                    onDrag = { change, _ ->
                        change.consume()
                        target = fractionAt(change.position.x)
                        preview(target)
                    },
                    // The release can carry a newer position than the final MOVE event.
                    onDragEnd = { change -> commit(fractionAt(change.position.x)) },
                    onDragCancel = { cancel() },
                )
            }
        }) {
        val inset = 12.dp.toPx().coerceAtMost(size.width / 2f)
        val left = inset
        val right = size.width - inset
        val start = if (rtl) right else left
        val end = if (rtl) left else right
        val fillFraction = fraction.coerceIn(0f, 1f)
        val x = start + (end - start) * fillFraction
        val y = size.height - 14.dp.toPx()
        val accent = if (enabled) played else played.copy(alpha = 0.38f)
        drawEchoControlRail(Offset(left, y), Offset(right, y), Offset(start, y), Offset(x, y), accent, unplayed)
        drawEchoControlThumb(Offset(x, y), accent, face, engaged, enabled)
    }
}

package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoChromeColors
import app.echo.android.design.progressFraction
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.radio.EchoRadioStation

/**
 * Height of the seek strip along the dock's top edge. It equals the row's top padding, so the strip
 * never overlaps the title, cover or play button, which keep their own taps.
 */
internal val MiniPlayerProgressTouchHeight = 7.dp

/** Thin dock rail with a separate touch target; position ticks are read only during drawing. */
@Composable
internal fun MiniPlayerProgress(
    status: EchoPlaybackStatus,
    positionState: State<PlaybackPositionState>?,
    onSeek: ((Long) -> Unit)?,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val latestStatus = rememberUpdatedState(status)
    val durationMs by remember(positionState) {
        derivedStateOf {
            positionState?.value?.durationMs?.takeIf { it > 0L } ?: latestStatus.value.durationMs
        }
    }
    val trackKey = status.track?.id
    val enabled = onSeek != null && trackKey != null && durationMs > 0L && !EchoRadioStation.isRadio(trackKey)
    var preview by remember(trackKey, durationMs) { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val chrome = echoChromeColors()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val alpha = animateFloatAsState(
        targetValue = if (durationMs > 0L) 1f else 0.42f,
        animationSpec = if (!motionEnabled) snap() else EchoMotion.silkFloat(miniPlayerMotionDuration(220, lightweight)),
        label = "mini-player-progress-alpha",
    )
    fun shownFraction(): Float = preview ?: progressFraction(
        positionState?.value?.positionMs ?: latestStatus.value.positionMs, durationMs,
    )
    val commit by rememberUpdatedState<(Float) -> Unit>({ fraction ->
        if (enabled) seek?.invoke((fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0L, durationMs))
        preview = null
    })
    Canvas(
        modifier.fillMaxWidth().height(MiniPlayerProgressTouchHeight)
            .graphicsLayer { this.alpha = alpha.value }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shownFraction(), 0f..1f)
                if (!enabled) disabled()
                setProgress { value ->
                    if (enabled) commit(value)
                    enabled
                }
            }
            .playerSeekInput(
                trackKey = trackKey, durationMs = durationMs, enabled = enabled, rtl = rtl,
                onPreview = { preview = it }, onCommit = commit, onCancel = { preview = null },
            ),
    ) {
        val stroke = (if (preview != null) 3.dp else 1.dp).toPx()
        val y = stroke / 2f
        val start = if (rtl) size.width else 0f
        val end = if (rtl) 0f else size.width
        drawLine(chrome.separator, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        drawLine(chrome.secondary, Offset(start, y), Offset(start + (end - start) * shownFraction(), y), strokeWidth = stroke)
    }
}

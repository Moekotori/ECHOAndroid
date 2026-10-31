package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.progressFraction
import app.echo.android.model.radio.EchoRadioStation

/**
 * Tap/drag seeking with the style's own drawing. Position reads stay in this scope, and [times]
 * draws the readout beneath the rail.
 */
@Composable
internal fun ThemedSeekBar(
    trackKey: String?,
    position: State<Long>,
    duration: State<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    inset: Dp = 12.dp,
    times: @Composable (elapsedMs: Long, totalMs: Long, fraction: Float) -> Unit,
    draw: DrawScope.(fraction: Float, engaged: Boolean, enabled: Boolean) -> Unit,
) {
    if (EchoRadioStation.isRadio(trackKey)) {
        Text(stringResource(R.string.radio_live), color = LocalContentColor.current)
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val total = duration.value
    var preview by remember(trackKey, total) { mutableStateOf<Float?>(null) }
    val fraction = preview ?: progressFraction(position.value, total)
    val enabled = total > 0
    val commit: (Float) -> Unit = {
        if (total > 0) onSeek((it * total).toLong())
        preview = null
    }
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                    if (!enabled) disabled()
                    setProgress { if (enabled) commit(it.coerceIn(0f, 1f)); enabled }
                }
                .playerSeekInput(
                    trackKey = trackKey, durationMs = total, enabled = enabled, inset = inset, rtl = rtl,
                    onPreview = { preview = it }, onCommit = commit, onCancel = { preview = null },
                ),
        ) {
            scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
                draw(fraction.coerceIn(0f, 1f), preview != null, enabled)
            }
        }
        times(if (total > 0) (fraction * total).toLong() else position.value, total, fraction)
    }
}

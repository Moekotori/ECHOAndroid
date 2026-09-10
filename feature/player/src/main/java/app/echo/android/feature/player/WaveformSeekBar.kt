package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/** A stable decorative waveform, like the PC waveform style; not measured audio peaks. */
@Composable
internal fun WaveformSeekBar(
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
    val played = MaterialTheme.colorScheme.primary
    val unplayed = OnArt.copy(alpha = 0.20f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = (maxWidth.value / 5f).toInt().coerceIn(24, 96)
        val heights = remember(trackKey, count) {
            val random = Random(trackKey.orEmpty().hashCode())
            FloatArray(count) { index ->
                val envelope = sin(index.toFloat() / (count - 1) * Math.PI).toFloat()
                (0.16f + random.nextFloat() * 0.55f + envelope * 0.25f).coerceIn(0.16f, 1f)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                if (!enabled) disabled()
                setProgress { value ->
                    if (enabled) commit(value.coerceIn(0f, 1f))
                    enabled
                }
            }
            .pointerInput(trackKey, enabled) {
                if (enabled) detectTapGestures { point ->
                    commit((point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(trackKey, enabled) {
                if (enabled) {
                    var target = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { point ->
                            target = (point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
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
            val step = size.width / count
            val stroke = minOf(2.5.dp.toPx(), step * 0.55f)
            fun bars(color: androidx.compose.ui.graphics.Color) {
                heights.forEachIndexed { index, amplitude ->
                    val x = (index + 0.5f) * step
                    val half = amplitude * 12.dp.toPx()
                    drawLine(color, Offset(x, center.y - half), Offset(x, center.y + half), stroke, StrokeCap.Round)
                }
            }
            bars(unplayed)
            clipRect(right = size.width * fraction.coerceIn(0f, 1f)) { bars(played) }
        }
    }
}

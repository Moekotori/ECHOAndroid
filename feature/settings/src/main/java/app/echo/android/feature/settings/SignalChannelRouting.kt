package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoChannelBalanceMonoMode

/** A static map of configured sources and destinations, never a live level meter. */
@Composable
internal fun SignalChannelRouting(swapped: Boolean, mode: EchoChannelBalanceMonoMode, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.signal_routing_preview)
    val muted = stringResource(R.string.channel_muted)
    val stroke = if (enabled) scheme.primary else scheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.signal_input), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(stringResource(R.string.signal_output), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(28.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceAround) {
                Text("L", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall)
                Text("R", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                fun route(channel: Int, output: Int) {
                    val source = if (swapped) 1 - channel else channel
                    val y1 = size.height * if (source == 0) 0.25f else 0.75f
                    val y2 = size.height * if (output == 0) 0.25f else 0.75f
                    val line = Path().apply {
                        moveTo(0f, y1)
                        cubicTo(size.width * 0.42f, y1, size.width * 0.58f, y2, size.width, y2)
                    }
                    drawPath(line, stroke.copy(alpha = if (mode == EchoChannelBalanceMonoMode.Sum) 0.55f else 0.85f),
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(stroke, 2.dp.toPx(), Offset(0f, y1))
                    drawCircle(stroke, 2.dp.toPx(), Offset(size.width, y2))
                }
                when (mode) {
                    EchoChannelBalanceMonoMode.Off -> { route(0, 0); route(1, 1) }
                    EchoChannelBalanceMonoMode.Sum -> { route(0, 0); route(1, 0); route(0, 1); route(1, 1) }
                    EchoChannelBalanceMonoMode.Left -> route(0, 0)
                    EchoChannelBalanceMonoMode.Right -> route(1, 1)
                }
            }
            Column(Modifier.width(66.dp).fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceAround) {
                Text(if (mode == EchoChannelBalanceMonoMode.Right) "L · $muted" else "L", style = MaterialTheme.typography.labelMedium,
                    color = if (mode == EchoChannelBalanceMonoMode.Right) scheme.onSurfaceVariant else scheme.onSurface)
                Text(if (mode == EchoChannelBalanceMonoMode.Left) "R · $muted" else "R", style = MaterialTheme.typography.labelMedium,
                    color = if (mode == EchoChannelBalanceMonoMode.Left) scheme.onSurfaceVariant else scheme.onSurface)
            }
        }
    }
}

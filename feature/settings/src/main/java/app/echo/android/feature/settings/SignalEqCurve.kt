package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqResponsePoint
import kotlin.math.abs
import kotlin.math.ceil

@Composable
internal fun SignalEqCurve(points: List<EchoEqResponsePoint>) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.eq_curve_reference)
    val range = maxOf(12f, ceil((points.maxOfOrNull { abs(it.gainDb) } ?: 0f) / 6f) * 6f).coerceAtMost(48f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("+${range.toInt()} dB", style = MaterialTheme.typography.labelSmall)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
            for (line in 0..4) {
                val y = size.height * line / 4
                drawLine(grid, Offset(0f, y), Offset(size.width, y), if (line == 2) 2f else 1f)
            }
            for (line in 0..3) {
                val x = size.width * line / 3
                drawLine(grid.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height))
            }
            if (points.size > 1) {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = size.width * index / (points.size - 1)
                    val y = size.height * (1 - point.gainDb.coerceIn(-range, range) / range) / 2
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("20 Hz", "200 Hz", "2 kHz", "20 kHz").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("−${range.toInt()} dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction

/** Seconds stay local to this instrument panel; the cover and station rows never observe them. */
@Composable
internal fun MetroJourneyReadout(position: State<Long>, duration: State<Long>) {
    val elapsed by remember(position, duration) {
        derivedStateOf { position.value.coerceIn(0L, duration.value.coerceAtLeast(0L)) / 1000L }
    }
    val total = duration.value.coerceAtLeast(0L)
    Row(Modifier.fillMaxWidth().drawBehind {
        drawLine(MetroStyle.Rule, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
    }.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(stringResource(R.string.player_style_metro_progress), color = MetroStyle.Muted, fontSize = 9.sp, lineHeight = 12.sp)
            Text(formatDuration(elapsed * 1000L), color = MetroStyle.Ink, fontFamily = FontFamily.Monospace,
                fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-1).sp)
        }
        Canvas(Modifier.weight(1f).padding(horizontal = 28.dp).height(20.dp)) {
            val y = size.height / 2f
            val start = 3.dp.toPx()
            val end = size.width - start
            val fraction = progressFraction(position.value, duration.value)
            drawLine(MetroStyle.Rail, Offset(start, y), Offset(end, y), 1.dp.toPx(), StrokeCap.Round)
            drawLine(MetroStyle.Line, Offset(start, y), Offset(start + (end - start) * fraction, y), 1.5.dp.toPx(), StrokeCap.Round)
            drawCircle(MetroStyle.Line, 3.dp.toPx(), Offset(start + (end - start) * fraction, y))
            drawLine(MetroStyle.Muted, Offset(end - 4.dp.toPx(), y - 3.dp.toPx()), Offset(end, y), 1.dp.toPx())
            drawLine(MetroStyle.Muted, Offset(end - 4.dp.toPx(), y + 3.dp.toPx()), Offset(end, y), 1.dp.toPx())
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.player_style_metro_terminus), color = MetroStyle.Muted, fontSize = 9.sp, lineHeight = 12.sp)
            Text(if (total > 0) formatDuration(total) else "—", color = MetroStyle.Ink,
                fontFamily = FontFamily.Monospace, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-1).sp)
        }
    }
}

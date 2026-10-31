package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The rail continues through the list's spacing; lyric scale/lift never move the track. */
internal fun DrawScope.drawMetroLyricStation(passed: Boolean, focused: Boolean, emphasis: Float, spacing: Float) {
    val x = 24.dp.toPx()
    val y = 13.dp.toPx()
    val gap = (25f * spacing / 2f).dp.toPx()
    val reached = passed || focused
    drawLine(if (reached) MetroStyle.Line else MetroStyle.Rail, Offset(x, -gap), Offset(x, y), 5.dp.toPx())
    drawLine(if (passed) MetroStyle.Line else MetroStyle.Rail, Offset(x, y), Offset(x, size.height + gap), 5.dp.toPx())
    drawLine(if (reached) MetroStyle.Line else MetroStyle.Rail, Offset(x, y), Offset(49.dp.toPx(), y), 1.dp.toPx())
    if (emphasis > 0f) {
        drawCircle(MetroStyle.Line.copy(alpha = 0.09f * emphasis), 17.dp.toPx(), Offset(x, y))
        val plate = Size((size.width - 52.dp.toPx()).coerceAtLeast(0f), size.height + 14.dp.toPx())
        drawRoundRect(MetroStyle.Board.copy(alpha = emphasis), Offset(52.dp.toPx(), -7.dp.toPx()),
            plate, CornerRadius(4.dp.toPx()))
        drawRoundRect(MetroStyle.Line.copy(alpha = 0.12f * emphasis), Offset(52.dp.toPx(), -7.dp.toPx()),
            plate, CornerRadius(4.dp.toPx()), style = Stroke(0.7.dp.toPx()))
    }
    drawCircle(MetroStyle.Paper, 9.dp.toPx(), Offset(x, y))
    drawCircle(if (reached) MetroStyle.Line else MetroStyle.Muted, 8.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
    if (focused) {
        drawRoundRect(MetroStyle.Line, Offset(x - 4.dp.toPx(), y - 4.dp.toPx()),
            Size(8.dp.toPx(), 8.dp.toPx()), CornerRadius(1.dp.toPx()))
    } else if (passed) drawCircle(MetroStyle.Line, 2.5.dp.toPx(), Offset(x, y))
}

@Composable
internal fun MetroLyricStationLabel(number: Int, startMs: Long, focused: Boolean, passed: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("04·${number.toString().padStart(2, '0')}",
            Modifier.background(if (focused) MetroStyle.Line else if (passed) MetroStyle.SoftRed else Color.Transparent,
                RoundedCornerShape(2.dp)).padding(horizontal = 5.dp, vertical = 3.dp),
            color = if (focused) Color.White else if (passed) MetroStyle.Line else MetroStyle.Muted,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.width(8.dp))
        Text(if (startMs >= 0) lyricTimestamp(startMs) else "",
            color = MetroStyle.Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
        )
        Spacer(Modifier.weight(1f))
        if (focused) Text(stringResource(R.string.player_style_metro_current),
            color = MetroStyle.Line, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

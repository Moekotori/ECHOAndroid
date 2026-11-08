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
internal fun DrawScope.drawMetroLyricStation(
    passed: Boolean,
    focused: Boolean,
    emphasis: Float,
    spacing: Float,
    /** How far the train has run down this station's own stretch of rail (0..1). */
    depart: Float = 0f,
    /** How far it has run into this station along the rail above it (0..1). */
    arrive: Float = 0f,
) {
    val x = 24.dp.toPx()
    val y = 13.dp.toPx()
    val gap = (25f * spacing / 2f).dp.toPx()
    val reached = passed || focused
    val railWidth = 5.dp.toPx()
    drawLine(if (reached) MetroStyle.Line else MetroStyle.Rail, Offset(x, -gap), Offset(x, y), railWidth)
    drawLine(if (passed) MetroStyle.Line else MetroStyle.Rail, Offset(x, y), Offset(x, size.height + gap), railWidth)
    // The covered part of the trip in line red, with the train at its head.
    if (arrive > 0f && !reached) {
        drawLine(MetroStyle.Line, Offset(x, -gap), Offset(x, -gap + (y + gap) * arrive), railWidth)
    }
    if (depart > 0f && !passed) {
        drawLine(MetroStyle.Line, Offset(x, y), Offset(x, y + (size.height + gap - y) * depart), railWidth)
    }
    drawLine(if (reached) MetroStyle.Line else MetroStyle.Rail, Offset(x, y), Offset(49.dp.toPx(), y), 1.dp.toPx())
    if (focused && emphasis < 1f) {
        // Arrival ripple while the station takes focus.
        drawCircle(MetroStyle.Line.copy(alpha = 0.4f * (1f - emphasis)), 9.dp.toPx() + 14.dp.toPx() * emphasis,
            Offset(x, y), style = Stroke(2.dp.toPx()))
    }
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
    if (depart > 0f && depart < 1f) drawMetroTrain(Offset(x, y + (size.height + gap - y) * depart))
    else if (arrive > 0f && arrive < 1f) drawMetroTrain(Offset(x, -gap + (y + gap) * arrive))
}

/** A small car running down the rail, windscreen at the front (bottom). */
private fun DrawScope.drawMetroTrain(center: Offset) {
    val width = 8.dp.toPx()
    val height = 13.dp.toPx()
    val edge = 1.5.dp.toPx()
    val left = center.x - width / 2f
    val top = center.y - height / 2f
    drawCircle(MetroStyle.Line.copy(alpha = 0.16f), 10.dp.toPx(), center)
    drawRoundRect(MetroStyle.Paper, Offset(left - edge, top - edge), Size(width + edge * 2f, height + edge * 2f),
        CornerRadius(5.dp.toPx()))
    drawRoundRect(MetroStyle.Line, Offset(left, top), Size(width, height), CornerRadius(3.5.dp.toPx()))
    drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(center.x - 2.5.dp.toPx(), top + height - 5.5.dp.toPx()),
        Size(5.dp.toPx(), 2.5.dp.toPx()), CornerRadius(1.dp.toPx()))
}

@Composable
internal fun MetroLyricStationLabel(number: Int, startMs: Long, focused: Boolean, passed: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("04·${number.toString().padStart(2, '0')}",
            Modifier.background(if (focused) MetroStyle.Line else if (passed) MetroStyle.SoftRed else Color.Transparent,
                RoundedCornerShape(2.dp)).padding(horizontal = 5.dp, vertical = 3.dp),
            color = if (focused) Color.White else if (passed) MetroStyle.Line else MetroStyle.Muted,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.width(8.dp))
        Text(if (startMs >= 0) lyricTimestamp(startMs) else "",
            color = MetroStyle.Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp,
        )
        Spacer(Modifier.weight(1f))
        if (focused) Text(stringResource(R.string.player_style_metro_current),
            color = MetroStyle.Line, fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
    }
}

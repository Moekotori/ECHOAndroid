package app.echo.android.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** Shared thumb treatment for sliders, seek bars and the equalizer's vertical faders. */
fun DrawScope.drawEchoControlThumb(
    center: Offset,
    accent: Color,
    face: Color,
    engaged: Boolean = false,
    enabled: Boolean = true,
) {
    val radius = (if (engaged && enabled) 11f else 9f).dp.toPx()
    if (engaged && enabled) drawCircle(accent.copy(alpha = 0.12f), radius + 3.dp.toPx(), center)
    drawCircle(Color.Black.copy(alpha = if (enabled) 0.12f else 0.04f), radius, center + Offset(0f, 1.dp.toPx()))
    drawCircle(accent, radius, center)
    drawCircle(face, radius - 3.dp.toPx(), center)
}

/** The selected rail is slightly fuller than the quiet remaining rail. */
fun DrawScope.drawEchoControlRail(
    start: Offset,
    end: Offset,
    selectedStart: Offset,
    selectedEnd: Offset,
    accent: Color,
    inactive: Color,
) {
    drawLine(inactive, start, end, 4.dp.toPx(), StrokeCap.Round)
    if (selectedStart != selectedEnd) {
        drawLine(accent, selectedStart, selectedEnd, 6.dp.toPx(), StrokeCap.Round)
    }
}

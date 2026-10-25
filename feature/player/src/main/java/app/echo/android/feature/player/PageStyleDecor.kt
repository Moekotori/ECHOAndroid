package app.echo.android.feature.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoPlayerPageStyle

/**
 * Full-screen backdrop shared by the song and lyrics pages of the Terminal and Scrapbook styles.
 * Everything is built once per size; [lyricsReveal] (0 song page, 1 lyrics page) is read only while
 * drawing, so swiping never recomposes it.
 */
@Composable
internal fun PageStyleDecor(style: EchoPlayerPageStyle, lyricsReveal: () -> Float, modifier: Modifier = Modifier) {
    when (style) {
        EchoPlayerPageStyle.Terminal -> Spacer(modifier.fillMaxSize().drawWithCache {
            val lines = Path().apply {
                var y = 0f
                val step = 3.dp.toPx()
                while (y < size.height) {
                    moveTo(0f, y)
                    lineTo(size.width, y)
                    y += step
                }
            }
            val stroke = Stroke(1.dp.toPx())
            val vignette = Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                Offset(size.width / 2f, size.height / 2f), maxOf(size.width, size.height) * 0.75f,
            )
            onDrawBehind {
                drawRect(vignette)
                drawPath(lines, Color.Black.copy(alpha = 0.22f), style = stroke)
            }
        })
        EchoPlayerPageStyle.Scrapbook -> Spacer(modifier.fillMaxSize().drawWithCache {
            val pitch = 18.dp.toPx()
            val dots = ArrayList<Offset>()
            var y = pitch / 2f
            while (y < size.height) {
                var x = pitch / 2f
                while (x < size.width) {
                    dots.add(Offset(x, y))
                    x += pitch
                }
                y += pitch
            }
            val dotColor = Color(0xFFD3C3A3)
            val dotWidth = 2.4.dp.toPx()
            val tape = listOf(Color(0xFFF6B6C8), Color(0xFFFBD9E3))
            val ring = 34.dp.toPx()
            val rings = ArrayList<Offset>()
            var ringY = ring / 2f
            while (ringY < size.height) {
                rings.add(Offset(9.dp.toPx(), ringY))
                ringY += ring
            }
            val ringColor = Color(0xFF6B6577)
            val margin = Color(0xFFE58C8C)
            onDrawBehind {
                drawPoints(dots, PointMode.Points, dotColor, dotWidth, StrokeCap.Round)
                val reveal = lyricsReveal().coerceIn(0f, 1f)
                if (reveal < 1f) {
                    rotate(-4f, Offset(0f, 40.dp.toPx())) {
                        val stripe = 14.dp.toPx()
                        for (i in 0 until 15) {
                            drawRect(tape[i % 2].copy(alpha = 0.9f * (1f - reveal)), Offset(-20.dp.toPx() + i * stripe, 30.dp.toPx()),
                                androidx.compose.ui.geometry.Size(stripe, 28.dp.toPx()))
                        }
                    }
                }
                if (reveal > 0f) {
                    drawLine(margin.copy(alpha = 0.8f * reveal), Offset(20.dp.toPx(), 0f), Offset(20.dp.toPx(), size.height), 1.dp.toPx())
                    drawLine(margin.copy(alpha = 0.8f * reveal), Offset(23.dp.toPx(), 0f), Offset(23.dp.toPx(), size.height), 1.dp.toPx())
                    drawPoints(rings, PointMode.Points, ringColor.copy(alpha = reveal), 9.dp.toPx(), StrokeCap.Round)
                }
            }
        })
        else -> Unit
    }
}

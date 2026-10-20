package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import app.echo.android.model.settings.EchoLyricsBackdrop
import app.echo.android.model.settings.EchoPlayerPageStyle
import kotlin.math.PI
import kotlin.math.sin

/** Static miniatures. They draw once per size and never read live state. */
@Composable
internal fun playerPageStyleOptions(): List<SettingsOption> =
    EchoPlayerPageStyle.entries.map { style ->
        SettingsOption(style.id, stringResource(playerPageStyleLabel(style)), pageStyleThumbnail(style))
    }

@Composable
internal fun lyricsBackdropOptions(): List<SettingsOption> =
    EchoLyricsBackdrop.entries.map { style ->
        SettingsOption(style.id, stringResource(lyricsBackdropLabel(style)), backdropThumbnail(style))
    }

private fun pageStyleThumbnail(style: EchoPlayerPageStyle): DrawScope.(Color) -> Unit = when (style) {
    EchoPlayerPageStyle.Classic -> { accent ->
        drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF1B1A20))))
        val cover = size.height * 0.58f
        drawRoundRect(
            Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f))),
            topLeft = Offset(size.width * 0.12f, (size.height - cover) / 2f),
            size = Size(cover, cover), cornerRadius = CornerRadius(cover * 0.14f),
        )
        textBars(Offset(size.width * 0.12f + cover + size.width * 0.07f, size.height * 0.34f), size.width * 0.34f, Color.White)
    }
    EchoPlayerPageStyle.RecordSleeve -> { accent ->
        drawRect(Color(0xFF2A2522))
        val disc = size.height * 0.40f
        val center = Offset(size.width * 0.62f, size.height / 2f)
        drawCircle(Color(0xFF0E0D0F), disc, center)
        drawCircle(Color.White.copy(alpha = 0.10f), disc * 0.72f, center, style = Stroke(1.2f))
        drawCircle(Color.White.copy(alpha = 0.10f), disc * 0.50f, center, style = Stroke(1.2f))
        drawCircle(accent, disc * 0.26f, center)
        val sleeve = size.height * 0.66f
        drawRoundRect(
            Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.50f))),
            topLeft = Offset(size.width * 0.14f, (size.height - sleeve) / 2f),
            size = Size(sleeve, sleeve), cornerRadius = CornerRadius(4f),
        )
    }
    EchoPlayerPageStyle.PixelHandheld -> { _ ->
        drawRect(Color(0xFF26302A))
        val screenW = size.width * 0.58f
        val screenH = size.height * 0.60f
        val left = (size.width - screenW) / 2f
        val top = size.height * 0.12f
        drawRoundRect(Color(0xFFA9C58B), Offset(left, top), Size(screenW, screenH), CornerRadius(5f))
        val px = screenW / 12f
        listOf(2 to 2, 3 to 2, 4 to 2, 4 to 3, 6 to 4, 7 to 4, 8 to 4, 8 to 3, 9 to 3).forEach { (cx, cy) ->
            drawRect(Color(0xFF34502F), Offset(left + cx * px, top + cy * px), Size(px * 0.9f, px * 0.9f))
        }
        drawCircle(Color(0xFF7C8F82), size.height * 0.07f, Offset(size.width * 0.30f, size.height * 0.86f))
        drawCircle(Color(0xFFD4707A), size.height * 0.06f, Offset(size.width * 0.70f, size.height * 0.86f))
    }
    EchoPlayerPageStyle.TypePoster -> { accent ->
        drawRect(Color(0xFFF1EADB))
        val ink = Color(0xFF211E1B)
        val h = size.height * 0.17f
        val gap = size.height * 0.07f
        var y = size.height * 0.12f
        listOf(0.74f to ink, 0.56f to accent, 0.66f to ink).forEach { (w, c) ->
            drawRoundRect(c, Offset(size.width * 0.10f, y), Size(size.width * w, h), CornerRadius(h * 0.2f))
            y += h + gap
        }
    }
    EchoPlayerPageStyle.AfterglowMist -> { accent ->
        drawRect(Color(0xFFEDE8F4))
        blob(accent.copy(alpha = 0.60f), Offset(size.width * 0.28f, size.height * 0.30f), size.height * 0.85f)
        blob(Color(0xFFFFA6C1).copy(alpha = 0.55f), Offset(size.width * 0.78f, size.height * 0.72f), size.height * 0.80f)
        textBars(Offset(size.width * 0.14f, size.height * 0.40f), size.width * 0.40f, Color(0xFF2A2630))
    }
    EchoPlayerPageStyle.AfterglowNight -> { _ ->
        drawRect(Color(0xFF0B0E1D))
        blob(Color(0xFF6B5BFF).copy(alpha = 0.60f), Offset(size.width * 0.26f, size.height * 0.34f), size.height * 0.90f)
        blob(Color(0xFFFF7A59).copy(alpha = 0.42f), Offset(size.width * 0.80f, size.height * 0.78f), size.height * 0.80f)
        listOf(0.12f to 0.20f, 0.46f to 0.12f, 0.88f to 0.28f, 0.62f to 0.52f).forEach { (x, y) ->
            drawCircle(Color.White.copy(alpha = 0.75f), 1.4f, Offset(size.width * x, size.height * y))
        }
        textBars(Offset(size.width * 0.14f, size.height * 0.42f), size.width * 0.40f, Color.White)
    }
}

private fun backdropThumbnail(style: EchoLyricsBackdrop): DrawScope.(Color) -> Unit = when (style) {
    EchoLyricsBackdrop.Fluid -> { accent ->
        drawRect(Color(0xFF17151C))
        blob(accent.copy(alpha = 0.75f), Offset(size.width * 0.25f, size.height * 0.35f), size.height * 0.95f)
        blob(Color(0xFF5B7CFF).copy(alpha = 0.60f), Offset(size.width * 0.72f, size.height * 0.30f), size.height * 0.85f)
        blob(Color(0xFFFF8FB5).copy(alpha = 0.55f), Offset(size.width * 0.55f, size.height * 0.85f), size.height * 0.85f)
    }
    EchoLyricsBackdrop.Mesh -> { accent ->
        drawRect(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF1A1A2A), Color(0xFF3B5BA8).copy(alpha = 0.7f))))
        val line = Color.White.copy(alpha = 0.22f)
        for (i in 1..3) {
            drawLine(line, Offset(size.width * i / 4f, 0f), Offset(size.width * i / 4f, size.height), 1f)
        }
        for (i in 1..2) {
            drawLine(line, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f)
        }
        for (ix in 1..3) for (iy in 1..2) {
            drawCircle(Color.White.copy(alpha = 0.7f), 2.2f, Offset(size.width * ix / 4f, size.height * iy / 3f))
        }
    }
    EchoLyricsBackdrop.Halftone -> { accent ->
        drawRect(Color(0xFF17151C))
        val cols = 14
        val rows = 5
        val stepX = size.width / cols
        val stepY = size.height / rows
        for (c in 0 until cols) for (r in 0 until rows) {
            val k = 1f - c / (cols - 1f) * 0.85f
            drawCircle(accent.copy(alpha = 0.9f), stepX * 0.46f * k, Offset(stepX * (c + 0.5f), stepY * (r + 0.5f)))
        }
    }
    EchoLyricsBackdrop.Ridge -> { accent ->
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1822), Color(0xFF0F0E14))))
        val lines = 6
        for (i in 0 until lines) {
            val base = size.height * (0.22f + i * 0.13f)
            val path = Path()
            val steps = 28
            for (s in 0..steps) {
                val t = s / steps.toFloat()
                val y = base - sin((t * 2f + i * 0.22f) * PI).toFloat() * size.height * 0.10f * (0.5f + t)
                if (s == 0) path.moveTo(0f, y) else path.lineTo(size.width * t, y)
            }
            drawPath(path, accent.copy(alpha = 0.25f + i * 0.11f), style = Stroke(1.6f, cap = StrokeCap.Round))
        }
    }
    EchoLyricsBackdrop.Vinyl -> { accent ->
        drawRect(Color(0xFF15131A))
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        for (i in 1..5) {
            drawCircle(Color.White.copy(alpha = 0.06f + i * 0.015f), size.height * 0.10f * i, center, style = Stroke(1.2f))
        }
        drawCircle(Color(0xFF0A090C), size.height * 0.42f, center, style = Stroke(size.height * 0.04f))
        drawCircle(accent, size.height * 0.16f, center)
        drawCircle(Color(0xFF15131A), size.height * 0.03f, center)
    }
    EchoLyricsBackdrop.Iso -> { accent ->
        drawRect(Color(0xFF15131A))
        val width = size.width * 0.15f
        listOf(0.26f, 0.44f, 0.34f, 0.56f, 0.30f).forEachIndexed { index, rise ->
            isoColumn(size.width * (0.14f + index * 0.18f), size.height * (0.80f - (index % 2) * 0.10f), width, size.height * rise, accent)
        }
    }
}

/** One isometric column: a lit top and two darker sides. */
private fun DrawScope.isoColumn(centerX: Float, bottomY: Float, width: Float, rise: Float, color: Color) {
    val halfWidth = width / 2f
    val halfHeight = width / 4f
    val topY = bottomY - rise
    fun face(points: List<Offset>, fill: Color) {
        val path = Path()
        points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
        path.close()
        drawPath(path, fill)
    }
    face(listOf(Offset(centerX - halfWidth, topY), Offset(centerX, topY + halfHeight), Offset(centerX, bottomY + halfHeight),
        Offset(centerX - halfWidth, bottomY)), color.copy(alpha = 0.70f))
    face(listOf(Offset(centerX + halfWidth, topY), Offset(centerX, topY + halfHeight), Offset(centerX, bottomY + halfHeight),
        Offset(centerX + halfWidth, bottomY)), color.copy(alpha = 0.42f))
    face(listOf(Offset(centerX, topY - halfHeight), Offset(centerX + halfWidth, topY), Offset(centerX, topY + halfHeight),
        Offset(centerX - halfWidth, topY)), color)
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

/** Title + two quieter caption lines, as a hint of text. */
private fun DrawScope.textBars(origin: Offset, width: Float, color: Color) {
    val h = size.height * 0.085f
    drawRoundRect(color.copy(alpha = 0.85f), origin, Size(width, h), CornerRadius(h / 2f))
    drawRoundRect(color.copy(alpha = 0.45f), origin + Offset(0f, h * 1.9f), Size(width * 0.66f, h * 0.8f), CornerRadius(h / 2f))
    drawRoundRect(color.copy(alpha = 0.30f), origin + Offset(0f, h * 3.4f), Size(width * 0.80f, h * 0.8f), CornerRadius(h / 2f))
}

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
    EchoPlayerPageStyle.Terminal -> { _ ->
        val green = Color(0xFF7CFF9B)
        drawRect(Color(0xFF060A07))
        drawRect(green, Offset(size.width * 0.08f, size.height * 0.10f), Size(size.width * 0.84f, size.height * 0.16f), style = Stroke(1.4f))
        drawRect(green, Offset(size.width * 0.08f, size.height * 0.34f), Size(size.height * 0.34f, size.height * 0.34f), style = Stroke(1.4f))
        listOf(0.34f to 0.42f, 0.34f to 0.52f, 0.34f to 0.62f).forEachIndexed { i, (_, y) ->
            drawRect(green.copy(alpha = if (i == 0) 0.95f else 0.55f), Offset(size.width * 0.42f, size.height * y), Size(size.width * (0.40f - i * 0.06f), size.height * 0.05f))
        }
        for (i in 0 until 10) {
            drawRect(if (i < 4) green else green.copy(alpha = 0.22f), Offset(size.width * (0.08f + i * 0.085f), size.height * 0.78f), Size(size.width * 0.07f, size.height * 0.12f))
        }
    }
    EchoPlayerPageStyle.Scrapbook -> { _ ->
        drawRect(Color(0xFFF3E8D2))
        for (i in 0..5) for (j in 0..2) drawCircle(Color(0xFFD3C3A3), 1.6f, Offset(size.width * (0.1f + i * 0.16f), size.height * (0.2f + j * 0.3f)))
        val photo = Size(size.height * 0.52f, size.height * 0.62f)
        val topLeft = Offset(size.width * 0.5f - photo.width / 2f, size.height * 0.12f)
        drawRoundRect(Color.White, topLeft, photo, CornerRadius(2f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2F7A), Color(0xFFD9638A), Color(0xFFFFB06B)), topLeft.y, topLeft.y + photo.width),
            topLeft + Offset(photo.width * 0.08f, photo.width * 0.08f), Size(photo.width * 0.84f, photo.width * 0.84f))
        drawRect(Color(0xFFFFE27A).copy(alpha = 0.85f), topLeft + Offset(-6f, -4f), Size(photo.width * 0.4f, 8f))
        drawCircle(Color(0xFFF9B3C6), size.height * 0.11f, Offset(size.width * 0.72f, size.height * 0.74f))
        drawCircle(Color.White, size.height * 0.11f, Offset(size.width * 0.72f, size.height * 0.74f), style = Stroke(2.4f))
    }
    EchoPlayerPageStyle.MetroLine -> { _ ->
        drawRect(MetroStyle.Paper)
        drawRoundRect(MetroStyle.Sign, Offset(size.width * 0.06f, size.height * 0.08f),
            Size(size.width * 0.88f, size.height * 0.19f), CornerRadius(3f))
        drawRoundRect(MetroStyle.Line, Offset(size.width * 0.09f, size.height * 0.12f),
            Size(size.height * 0.11f, size.height * 0.11f), CornerRadius(2f))
        drawRect(MetroStyle.Amber, Offset(size.width * 0.24f, size.height * 0.16f), Size(size.width * 0.57f, size.height * 0.025f))
        drawRect(Color.White, Offset(size.width * 0.06f, size.height * 0.32f), Size(size.width * 0.88f, size.height * 0.20f))
        drawRect(MetroStyle.Line, Offset(size.width * 0.06f, size.height * 0.32f), Size(size.width * 0.018f, size.height * 0.20f))
        textBars(Offset(size.width * 0.12f, size.height * 0.36f), size.width * 0.43f, MetroStyle.Ink)
        drawRect(MetroStyle.Rule, Offset(size.width * 0.76f, size.height * 0.345f), Size(size.height * 0.15f, size.height * 0.15f))
        val x = size.width * 0.15f
        drawLine(MetroStyle.Rail, Offset(x, size.height * 0.61f), Offset(x, size.height * 0.93f), 4f, StrokeCap.Round)
        drawLine(MetroStyle.Line, Offset(x, size.height * 0.61f), Offset(x, size.height * 0.78f), 4f, StrokeCap.Round)
        for (i in 0..2) {
            val y = size.height * (0.61f + i * 0.16f)
            if (i == 1) drawRect(Color.White, Offset(size.width * 0.25f, y - size.height * 0.06f), Size(size.width * 0.68f, size.height * 0.12f))
            drawCircle(Color.White, 4f, Offset(x, y))
            drawCircle(if (i <= 1) MetroStyle.Line else MetroStyle.Rail, 4f, Offset(x, y), style = Stroke(2f))
            drawRoundRect(MetroStyle.Ink.copy(alpha = if (i == 1) 0.9f else 0.45f),
                Offset(size.width * 0.30f, y - 2f), Size(size.width * 0.38f, 4f), CornerRadius(1f))
            drawRect(MetroStyle.Muted.copy(alpha = 0.5f), Offset(size.width * 0.80f, y - 1f), Size(size.width * 0.09f, 2f))
        }
    }
    EchoPlayerPageStyle.ChatThread -> { _ ->
        val violet = Color(0xFF6C5CE7)
        drawRect(Color(0xFFECE9F5))
        val bubble = CornerRadius(9f)
        drawRoundRect(Color.White, Offset(size.width * 0.08f, size.height * 0.10f), Size(size.width * 0.46f, size.height * 0.34f), bubble)
        drawRoundRect(Color.White, Offset(size.width * 0.08f, size.height * 0.50f), Size(size.width * 0.34f, size.height * 0.14f), bubble)
        drawRoundRect(violet, Offset(size.width * 0.08f, size.height * 0.70f), Size(size.width * 0.62f, size.height * 0.20f), bubble)
        for (i in 0 until 9) {
            drawRect(Color.White.copy(alpha = if (i < 4) 1f else 0.45f), Offset(size.width * (0.20f + i * 0.050f), size.height * 0.76f), Size(size.width * 0.03f, size.height * (0.06f + (i % 3) * 0.025f)))
        }
        drawCircle(violet.copy(alpha = 0.55f), 3f, Offset(size.width * 0.76f, size.height * 0.30f))
    }
    EchoPlayerPageStyle.Halftone -> { _ ->
        drawRect(Color(0xFF08080B))
        val cols = 14
        val rows = 5
        val stepX = size.width / cols
        val stepY = size.height / rows
        for (c in 0 until cols) for (r in 0 until rows) {
            val glow = 1f - kotlin.math.hypot((c - cols * 0.62f) / cols, (r - rows * 0.35f) / rows * 0.8f) * 1.7f
            val k = glow.coerceIn(0.12f, 1f)
            drawCircle(Color(0xFFF3D9A4).copy(alpha = 0.55f + 0.45f * k), stepX * 0.46f * k, Offset(stepX * (c + 0.5f), stepY * (r + 0.5f)))
        }
    }
    EchoPlayerPageStyle.PixelMosaic -> { _ ->
        drawRect(Color(0xFF08080B))
        val cols = 18
        val rows = 6
        val stepX = size.width / cols
        val stepY = size.height / rows
        for (c in 0 until cols) for (r in 0 until rows) {
            val hill = (r > 3 && (c + r) % 7 < 3)
            val glow = 1f - kotlin.math.hypot((c - cols * 0.62f) / cols, (r - rows * 0.4f) / rows * 0.9f) * 2.2f
            val color = when {
                hill -> Color(0xFF1C1D4A)
                glow > 0.4f -> Color(0xFFF3D9A4)
                else -> androidx.compose.ui.graphics.lerp(Color(0xFF2B2F7A), Color(0xFFD9638A), r / (rows - 1f))
            }
            drawRect(color, Offset(c * stepX + 0.8f, r * stepY + 0.8f), Size(stepX - 1.6f, stepY - 1.6f))
        }
    }
    EchoPlayerPageStyle.Contours -> { _ ->
        drawRect(Color(0xFF08080B))
        val center = Offset(size.width * 0.58f, size.height * 0.46f)
        for (i in 1..6) {
            drawOval(
                androidx.compose.ui.graphics.lerp(Color(0xFF8A7BFF), Color(0xFFFFE0B0), i / 6f).copy(alpha = 0.9f),
                topLeft = center - Offset(size.height * 0.16f * i * 1.25f, size.height * 0.07f * i * 1.4f),
                size = Size(size.height * 0.32f * i * 1.25f, size.height * 0.14f * i * 1.4f),
                style = Stroke(1.6f),
            )
        }
    }
    EchoPlayerPageStyle.Dither -> { _ ->
        val dark = Color(0xFF1A1230)
        val light = Color(0xFFF1D7B8)
        drawRect(dark)
        val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        val cols = 40
        val rows = 12
        val stepX = size.width / cols
        val stepY = size.height / rows
        for (c in 0 until cols) for (r in 0 until rows) {
            val glow = (1f - kotlin.math.hypot((c - cols * 0.62f) / cols, (r - rows * 0.4f) / rows * 0.9f) * 2.4f).coerceIn(0f, 1f)
            if (glow * 1.15f > (bayer[(r and 3) * 4 + (c and 3)] + 0.5f) / 16f) drawRect(light, Offset(c * stepX, r * stepY), Size(stepX, stepY))
        }
    }
    EchoPlayerPageStyle.GlyphField -> { _ ->
        drawRect(Color(0xFF08080B))
        val cols = 16
        val rows = 5
        val stepX = size.width / cols
        val stepY = size.height / rows
        for (c in 0 until cols) for (r in 0 until rows) {
            val glow = (1f - kotlin.math.hypot((c - cols * 0.62f) / cols, (r - rows * 0.4f) / rows * 0.9f) * 2.2f).coerceIn(0f, 1f)
            if (glow > 0.12f) drawCircle(Color(0xFFF3D9A4).copy(alpha = 0.35f + 0.65f * glow), stepX * 0.12f + stepX * 0.2f * glow, Offset(stepX * (c + 0.5f), stepY * (r + 0.5f)))
        }
        for (c in 0 until cols) drawRect(Color(0xFF8A7BFF).copy(alpha = 0.5f), Offset(c * stepX + stepX * 0.35f, size.height * 0.9f), Size(stepX * 0.3f, 2f))
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

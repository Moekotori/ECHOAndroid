package app.echo.android.feature.player

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import kotlin.math.ceil

/** A quick attack, sustained light, then a short tail across word boundaries. */
internal fun lyricWordGlowStrength(startMs: Long, endMs: Long?, now: Long): Float {
    if (endMs == null || endMs <= startMs || now <= startMs) return 0f
    val attackMs = minOf(70L, maxOf(1L, (endMs - startMs) / 3))
    val attack = ((now - startMs).toFloat() / attackMs).coerceIn(0f, 1f)
    val release = (1f - (now - endMs).coerceAtLeast(0L) / 220f).coerceIn(0f, 1f)
    return attack * attack * (3f - 2f * attack) * release * release * (3f - 2f * release)
}

/** Geometry is refreshed on word/layout changes, never on the advancing wipe edge. */
internal class KaraokeWordEffects(private val color: Color) {
    private var layout: TextLayoutResult? = null
    private var wordIndex = -1
    private var glyphs = emptyList<Pair<Rect, Boolean>>()
    private var front: Rect? = null
    private var frontX = 0f
    private var frontIndex = -1
    val frontPath = Path()
    // Reuse one shader and matrix: the feather follows the singing edge without
    // allocating gradients, measuring text or running another animation every frame.
    private val matrix = Matrix()
    private val shader = LinearGradient(0f, 0f, 1f, 0f,
        intArrayOf(color.toArgb(), lerp(color, Color.White.copy(alpha = color.alpha), 0.26f).toArgb(),
            color.copy(alpha = 0f).toArgb()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    val inkBrush = ShaderBrush(shader)

    fun prepare(result: TextLayoutResult, index: Int, shapes: List<LyricWordShape>) {
        if (wordIndex == index && layout?.multiParagraph === result.multiParagraph && layout?.size == result.size) return
        layout = result
        wordIndex = index
        frontIndex = -1
        frontPath.rewind()
        val shape = shapes.getOrNull(index)
        if (shape == null || shape.isEmpty) {
            glyphs = emptyList()
            front = null
            return
        }
        glyphs = shape.glyphs.map { offset ->
            result.getBoundingBox(offset) to (result.getBidiRunDirection(offset) == ResolvedTextDirection.Rtl)
        }
    }

    fun updateFront(fraction: Float) {
        if (glyphs.isEmpty()) { front = null; return }
        val progress = fraction.coerceIn(0f, 1f) * glyphs.size
        val index = (ceil(progress).toInt() - 1).coerceIn(0, glyphs.lastIndex)
        val (bounds, rtl) = glyphs[index]
        val part = (progress - index).coerceIn(0f, 1f)
        front = bounds
        if (frontIndex != index) {
            frontIndex = index
            frontPath.rewind()
            frontPath.addRect(bounds)
        }
        frontX = if (rtl) bounds.right - bounds.width * part else bounds.left + bounds.width * part
        // Close the feather as a glyph finishes, meeting completed ink without a pop.
        val feather = minOf(bounds.height * 0.16f, bounds.width * part, bounds.width * (1f - part)).coerceAtLeast(0.01f)
        val direction = if (rtl) -1f else 1f
        matrix.setScale(feather * direction, 1f)
        matrix.postTranslate(frontX - feather * direction, 0f)
        shader.setLocalMatrix(matrix)
    }

    fun drawGlyphGlow(scope: DrawScope, result: TextLayoutResult, strength: Float) = with(scope) {
        if (strength <= 0f) return@with
        val bounds = front ?: return@with
        val level = strength.coerceIn(0f, 1f)
        // Light follows the actual glyph ink rather than a spotlight behind it.
        clipPath(frontPath) {
            drawText(result, color = color.copy(alpha = color.alpha * level * 0.16f),
                shadow = Shadow(color.copy(alpha = color.alpha * level * 0.58f),
                    blurRadius = bounds.height * 0.15f))
        }
    }
}

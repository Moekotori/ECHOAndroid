package app.echo.android.feature.player

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.settings.EchoLyricsWordHighlightStyle as Style
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val CompletedInk = 0.86f

/**
 * Draws one line in a per-word highlight style other than the sweep. Every word's progress is read from the
 * clock in the draw phase; only the few words that currently differ from the settled look are drawn on their
 * own, everything else is one clipped pass for the finished words plus the muted base text.
 *
 * Word outlines are built once per text layout and reused, so a frame allocates nothing per word.
 */
internal class KaraokeWordStyleLayer(
    private val style: Style,
    private val words: List<EchoLyricWord>,
    private val shapes: List<LyricWordShape>,
    private val estimatedTiming: Boolean,
    private val lineEndMs: Long?,
) {
    private val count = shapes.size
    private val moves = when (style) {
        Style.Lift, Style.Hop, Style.Swell, Style.Flip, Style.Wave, Style.Lens -> true
        else -> false
    }
    private val focusStyle = style.usesFocus
    private val progress = FloatArray(count)
    private val dynamic = IntArray(count)
    private var dynamicCount = 0

    private var layout: TextLayoutResult? = null
    private val paths = arrayOfNulls<Path>(count)
    private val bounds = Array(count) { Rect.Zero }
    private val completedPath = Path()
    private var completedBuilt = 0
    private val tailPath = Path()
    private var tailFrom = -1
    private val dots = Path()
    private val star = Path().apply {
        // A four-point sparkle in a unit box centred on the origin.
        val points = floatArrayOf(50f, 0f, 61f, 39f, 100f, 50f, 61f, 61f, 50f, 100f, 39f, 61f, 0f, 50f, 39f, 39f)
        for (index in 0 until points.size step 2) {
            val x = (points[index] - 50f) / 100f
            val y = (points[index + 1] - 50f) / 100f
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    private val glowPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    private var glowRadius = -1f

    fun draw(
        scope: DrawScope,
        result: TextLayoutResult,
        baseColor: Color,
        highlight: Color,
        ink: Float,
        intensity: Float,
        now: Long,
    ) = with(scope) {
        attach(result)
        val amp = intensity.coerceIn(0.45f, 1.35f) * ink
        val em = emPx(result)

        var completed = 0
        var lastStarted = -1
        var leading = true
        for (i in 0 until count) {
            val word = words[i]
            val p = if (estimatedTiming) lyricEstimatedEnvelope(word.startMs, now)
            else lyricWordFraction(word.startMs, lyricWordEndMs(words, i, lineEndMs), now)
            progress[i] = p
            if (p > 0f) lastStarted = i
            if (leading) { if (p >= 1f) completed = i + 1 else leading = false }
        }
        val partial = if (completed < count && progress[completed] > 0f) completed else -1
        val head = lyricWordFocusHead(completed, partial, if (partial >= 0) progress[partial] else 0f)
        collectDynamic(completed, lastStarted, head)
        val tailStart = lastStarted + 1
        val hasTail = style == Style.Lift && tailStart < count

        val canvas = drawContext.canvas
        // 1. Muted ink for everything, minus the words that are about to be drawn somewhere else.
        if (!(moves && dynamicCount > 0) && !hasTail) drawText(result, color = baseColor)
        else {
            canvas.save()
            if (moves) for (k in 0 until dynamicCount) canvas.clipPath(wordPath(result, dynamic[k]), ClipOp.Difference)
            if (hasTail) canvas.clipPath(tailPath(result, tailStart), ClipOp.Difference)
            drawText(result, color = baseColor)
            canvas.restore()
        }
        // 2. Finished words keep the settled look in a single clipped pass.
        if (completed > 0) {
            extendCompleted(result, completed)
            val factor = if (style == Style.Spot) CompletedInk * 0.7f else CompletedInk
            canvas.save()
            canvas.clipPath(completedPath)
            for (k in 0 until dynamicCount) if (dynamic[k] < completed) {
                canvas.clipPath(wordPath(result, dynamic[k]), ClipOp.Difference)
            }
            drawText(result, color = highlight.copy(alpha = highlight.alpha * ink * factor))
            canvas.restore()
            if (style == Style.Underline) {
                for (i in 0 until completed) if (!shapes[i].isEmpty) {
                    drawBar(result, i, 1f, highlight.copy(alpha = highlight.alpha * ink * CompletedInk), em)
                }
            }
        }
        // 3. Words that have not started yet rest slightly low, then rise into place as they are sung.
        if (hasTail) {
            canvas.save()
            canvas.translate(0f, LiftRise * em * amp)
            canvas.clipPath(tailPath(result, tailStart))
            drawText(result, color = baseColor)
            canvas.restore()
        }
        // 4. The few words whose look is changing right now.
        for (k in 0 until dynamicCount) {
            val index = dynamic[k]
            drawWord(result, index, index < completed, progress[index],
                if (focusStyle) lyricWordFocusWeight(index, head) else 0f,
                baseColor, highlight, ink, amp, intensity, em)
        }
    }

    private fun DrawScope.drawWord(
        result: TextLayoutResult,
        index: Int,
        finished: Boolean,
        p: Float,
        focus: Float,
        baseColor: Color,
        highlight: Color,
        ink: Float,
        amp: Float,
        intensity: Float,
        em: Float,
    ) {
        val path = wordPath(result, index)
        val box = bounds[index]
        val bump = lyricWordBump(p)
        var dy = 0f
        var scale = 1f
        var squash = 1f
        var pivotY = box.center.y
        var mix = p
        when (style) {
            Style.Lift -> dy = (1f - p) * LiftRise * em * amp
            Style.Hop -> dy = -bump * 0.16f * em * amp
            Style.Swell -> scale = 1f + bump * 0.12f * amp
            Style.Flip -> squash = cos(40f * bump * amp * (PI.toFloat() / 180f))
            Style.Wave -> dy = -focus * 0.22f * em * amp
            Style.Lens -> { scale = 1f + focus * 0.1f * amp; pivotY = box.top + box.height * 0.8f }
            Style.Spot -> mix = p * 0.7f + focus * 0.3f
            else -> Unit
        }
        val inkAlpha = highlight.alpha * ink * (if (finished) CompletedInk else lyricWordHighlightAlpha(p, intensity))
        val canvas = drawContext.canvas
        if (style == Style.Halo) drawGlow(path, highlight, bump * 0.55f * ink, em)

        canvas.save()
        if (dy != 0f || scale != 1f || squash != 1f) {
            canvas.translate(box.center.x, pivotY + dy)
            canvas.scale(scale, scale * squash)
            canvas.translate(-box.center.x, -pivotY)
        }
        canvas.clipPath(path)
        if (moves) drawText(result, color = baseColor)
        when (style) {
            Style.Scan -> {
                if (!estimatedTiming) canvas.clipRect(box.left, box.top - 1f, box.left + box.width * p, box.bottom + 1f)
                drawText(result, color = highlight.copy(alpha = inkAlpha * (if (estimatedTiming) p else 1f)))
            }
            Style.Dots -> {
                val cell = 0.2f * em
                val radius = p * 1.06f * cell * 0.7071f
                val native = dots.asAndroidPath()
                dots.rewind()
                var y = box.top
                while (y < box.bottom) {
                    var x = box.left
                    while (x < box.right) {
                        native.addCircle(x + cell / 2f, y + cell / 2f, radius, android.graphics.Path.Direction.CW)
                        x += cell
                    }
                    y += cell
                }
                canvas.clipPath(dots)
                drawText(result, color = highlight.copy(alpha = inkAlpha))
            }
            else -> {
                val alpha = inkAlpha * mix
                if (alpha > 0.002f) drawText(result, color = highlight.copy(alpha = alpha))
            }
        }
        canvas.restore()

        when (style) {
            Style.Underline -> drawBar(result, index, p, highlight.copy(alpha = highlight.alpha * ink * CompletedInk), em)
            Style.Spark -> {
                val opacity = (bump * 1.6f).coerceIn(0f, 1f) * ink
                if (opacity > 0.01f) {
                    val size = 0.36f * em * (0.3f + bump * 1.1f)
                    canvas.save()
                    canvas.translate(box.right - 0.04f * em, box.top + 0.08f * em)
                    canvas.rotate(bump * 80f)
                    canvas.scale(size, size)
                    drawPath(star, highlight.copy(alpha = highlight.alpha * opacity))
                    canvas.restore()
                }
            }
            Style.Scan -> if (!estimatedTiming) {
                val opacity = (bump * 4f).coerceIn(0f, 1f) * ink
                if (opacity > 0.01f) {
                    val x = box.left + box.width * p
                    val width = max(1.5f, 0.04f * em)
                    val top = box.top + 0.05f * em
                    val height = (box.height - 0.1f * em).coerceAtLeast(1f)
                    drawRect(highlight.copy(alpha = highlight.alpha * opacity * 0.22f),
                        Offset(x - width * 3f, top), Size(width * 6f, height))
                    drawRect(highlight.copy(alpha = highlight.alpha * opacity),
                        Offset(x - width / 2f, top), Size(width, height))
                }
            }
            else -> Unit
        }
    }

    /** Underline: a bar that grows with real word timing, or fades in as a whole when timing is only estimated. */
    private fun DrawScope.drawBar(result: TextLayoutResult, index: Int, p: Float, color: Color, em: Float) {
        wordPath(result, index)
        val box = bounds[index]
        val thickness = max(2f, 0.05f * em)
        val baseline = result.getLineBaseline(result.getLineForOffset(shapes[index].first))
        val top = baseline + 0.18f * em - thickness
        if (estimatedTiming && p < 1f) {
            drawRect(color.copy(alpha = color.alpha * p), Offset(box.left, top), Size(box.width, thickness))
        } else {
            drawRect(color, Offset(box.left, top), Size(box.width * p.coerceIn(0f, 1f), thickness))
        }
    }

    private fun DrawScope.drawGlow(path: Path, color: Color, alpha: Float, em: Float) {
        // Blur mask filters only render on hardware canvases from API 28.
        if (alpha <= 0.01f || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val radius = em * 0.32f
        if (glowRadius != radius) {
            glowPaint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            glowRadius = radius
        }
        glowPaint.color = color.copy(alpha = color.alpha * alpha).toArgb()
        drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), glowPaint) }
    }

    /** Ascending word indexes that currently differ from the settled finished/unsung look. */
    private fun collectDynamic(completed: Int, lastStarted: Int, head: Float) {
        dynamicCount = 0
        var from = completed
        var to = lastStarted
        if (focusStyle) {
            from = min(from, floor(head).toInt() - FocusReach).coerceAtLeast(0)
            to = max(to, min(count - 1, ceil(head).toInt() + FocusReach))
        }
        for (i in from..to) {
            if (shapes[i].isEmpty) continue
            val active = i in completed..lastStarted
            if (active || (focusStyle && lyricWordFocusWeight(i, head) > 0f)) dynamic[dynamicCount++] = i
        }
    }

    private fun attach(result: TextLayoutResult) {
        val same = layout?.multiParagraph === result.multiParagraph && layout?.size == result.size
        layout = result
        if (same) return
        paths.fill(null)
        completedPath.rewind()
        completedBuilt = 0
        tailPath.rewind()
        tailFrom = -1
    }

    private fun wordPath(result: TextLayoutResult, index: Int): Path =
        paths[index] ?: result.getPathForRange(shapes[index].first, shapes[index].endExclusive).also {
            paths[index] = it
            bounds[index] = it.getBounds()
        }

    private fun extendCompleted(result: TextLayoutResult, completed: Int) {
        if (completed < completedBuilt) { completedPath.rewind(); completedBuilt = 0 }
        while (completedBuilt < completed) {
            if (!shapes[completedBuilt].isEmpty) completedPath.addPath(wordPath(result, completedBuilt))
            completedBuilt++
        }
    }

    private fun tailPath(result: TextLayoutResult, from: Int): Path {
        if (tailFrom != from) {
            tailPath.rewind()
            for (i in from until count) if (!shapes[i].isEmpty) tailPath.addPath(wordPath(result, i))
            tailFrom = from
        }
        return tailPath
    }

    private fun emPx(result: TextLayoutResult): Float {
        val input = result.layoutInput
        val size = input.style.fontSize
        return if (size.isSp) with(input.density) { size.toPx() } else 16f * input.density.density
    }

    private companion object {
        const val LiftRise = 0.22f
        const val FocusReach = 4
    }
}

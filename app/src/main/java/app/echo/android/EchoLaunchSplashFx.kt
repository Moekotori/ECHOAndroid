package app.echo.android

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The splash clock plus the ambient layer (colour wash, light shafts, bokeh, floating notes).
 * State lives in preallocated arrays: stepping and drawing allocate nothing per particle, and
 * [time] is only read from draw/graphicsLayer blocks so ticks never recompose.
 */
internal class EchoLaunchSplashFx {
    val time = mutableFloatStateOf(0f)

    /** Splash time at which the hand-off started, or -1 while still holding. */
    val exitAt = mutableFloatStateOf(-1f)

    var density = 1f
    var originX = 0f
    var originY = 0f
    var mascotWidth = 0f

    private val random = Random(7)
    private val dotX = FloatArray(DotCount) { random.nextFloat() }
    private val dotY = FloatArray(DotCount) { random.nextFloat() }
    private val dotRadius = FloatArray(DotCount) { 1.5f + random.nextFloat() * 5.5f }
    private val dotSpeed = FloatArray(DotCount) { 0.012f + random.nextFloat() * 0.028f }
    private val dotPhase = FloatArray(DotCount) { random.nextFloat() * TwoPi }
    private val dotTwinkle = FloatArray(DotCount) { 0.6f + random.nextFloat() }
    private val dotColor = IntArray(DotCount) { it % ParticleColors.size }
    private val dotIsStar = BooleanArray(DotCount) { it % 3 == 0 }

    private val noteActive = BooleanArray(NoteCount)
    private val noteX = FloatArray(NoteCount)
    private val noteY = FloatArray(NoteCount)
    private val noteRise = FloatArray(NoteCount)
    private val noteSway = FloatArray(NoteCount)
    private val noteFreq = FloatArray(NoteCount)
    private val notePhase = FloatArray(NoteCount)
    private val noteLife = FloatArray(NoteCount)
    private val noteAge = FloatArray(NoteCount)
    private val noteSize = FloatArray(NoteCount)
    private val noteTilt = FloatArray(NoteCount)
    private val noteColor = IntArray(NoteCount)
    private val noteIsStar = BooleanArray(NoteCount)
    private var nextNoteAt = 1.5f

    private val rayPath = Path()

    fun advance(dt: Float) {
        val t = time.floatValue + dt
        time.floatValue = t
        for (i in 0 until DotCount) {
            dotY[i] -= dotSpeed[i] * dt
            if (dotY[i] < -0.05f) {
                dotY[i] = 1.05f
                dotX[i] = random.nextFloat()
            }
        }
        for (i in 0 until NoteCount) {
            if (!noteActive[i]) continue
            noteAge[i] += dt
            if (noteAge[i] > noteLife[i]) noteActive[i] = false
        }
        if (t > nextNoteAt && mascotWidth > 0f) {
            val slot = noteActive.indexOfFirst { !it }
            if (slot >= 0) spawnNote(slot)
            nextNoteAt = t + 0.55f + random.nextFloat() * 0.4f
        }
    }

    /** 0..1 progress of the hand-off, shifted by [delay] seconds. */
    fun exitProgress(delay: Float, duration: Float): Float {
        val start = exitAt.floatValue
        if (start < 0f) return 0f
        return launchSplashProgress(time.floatValue - start, delay, duration)
    }

    private fun spawnNote(slot: Int) {
        val side = if (random.nextBoolean()) -1f else 1f
        noteActive[slot] = true
        noteX[slot] = originX + side * mascotWidth * (0.21f + random.nextFloat() * 0.2f)
        noteY[slot] = originY + mascotWidth * random.nextFloat() * 0.21f
        noteRise[slot] = (22f + random.nextFloat() * 12f) * density
        noteSway[slot] = (8f + random.nextFloat() * 7f) * density
        noteFreq[slot] = 1.1f + random.nextFloat() * 0.8f
        notePhase[slot] = random.nextFloat() * TwoPi
        noteLife[slot] = 3.2f + random.nextFloat()
        noteAge[slot] = 0f
        noteSize[slot] = (10f + random.nextFloat() * 5f) * density
        noteTilt[slot] = (random.nextFloat() - 0.5f) * 0.6f
        noteColor[slot] = random.nextInt(ParticleColors.size)
        noteIsStar[slot] = random.nextFloat() < 0.35f
    }

    fun draw(scope: DrawScope, palette: LaunchSplashPalette) = with(scope) {
        val t = time.floatValue
        if (palette.showAtmosphere) {
            drawBlobs(t, palette)
            drawRays(t, palette)
        }
        drawDots(t)
        drawNotes()
    }

    private fun DrawScope.drawBlobs(t: Float, palette: LaunchSplashPalette) {
        val w = size.width
        val h = size.height
        val a = 0.5f - 0.5f * cos(TwoPi * t / 14f)
        val b = 0.5f - 0.5f * cos(TwoPi * t / 17f)
        drawBlob(
            palette.blobA, launchSplashProgress(t, 0f, 1.4f),
            Offset(w * 0.15f + w * 0.108f * a, h * 0.06f + w * 0.45f + w * 0.072f * a), w * 0.45f * (1f + 0.08f * a),
        )
        drawBlob(
            palette.blobB, launchSplashProgress(t, 0.3f, 1.4f),
            Offset(w * 0.9f - w * 0.112f * b, h * 0.38f + w * 0.4f - w * 0.048f * b), w * 0.4f * (1f + 0.1f * b),
        )
    }

    private fun DrawScope.drawBlob(color: Color, fade: Float, center: Offset, radius: Float) {
        if (fade <= 0f) return
        val alpha = 0.8f * fade
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = alpha),
                0.55f to color.copy(alpha = alpha * 0.6f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    /** Light shafts falling from above the screen, swaying slowly. */
    private fun DrawScope.drawRays(t: Float, palette: LaunchSplashPalette) {
        val fade = launchSplashProgress(t, 0.2f, 2.2f)
        if (fade <= 0f) return
        val apex = Offset(size.width / 2f, -0.06f * size.height)
        val length = size.height * 1.2f
        val brush = Brush.verticalGradient(
            colors = listOf(palette.ray.copy(alpha = palette.ray.alpha * fade), Color.Transparent),
            startY = apex.y,
            endY = size.height * 0.7f,
        )
        rotate(3f * sin(TwoPi * t / 9f) * fade, pivot = apex) {
            for (angle in RayAngles) {
                drawRay(apex, length, angle, RayFeather, brush, 0.4f)
                drawRay(apex, length, angle, RayCore, brush, 1f)
            }
        }
    }

    private fun DrawScope.drawRay(apex: Offset, length: Float, angle: Float, half: Float, brush: Brush, alpha: Float) {
        val left = (angle - half) * DegreesToRadians
        val right = (angle + half) * DegreesToRadians
        rayPath.reset()
        rayPath.moveTo(apex.x, apex.y)
        rayPath.lineTo(apex.x + sin(left) * length, apex.y + cos(left) * length)
        rayPath.lineTo(apex.x + sin(right) * length, apex.y + cos(right) * length)
        rayPath.close()
        drawPath(rayPath, brush, alpha = alpha)
    }

    private fun DrawScope.drawDots(t: Float) {
        val fadeIn = launchSplashProgress(t, 0f, 1.4f)
        if (fadeIn <= 0f) return
        for (i in 0 until DotCount) {
            val y = dotY[i]
            val twinkle = 0.5f + 0.5f * sin(t * dotTwinkle[i] * 2f + dotPhase[i])
            val edge = (min(y, 1f - y) * 6f).coerceIn(0f, 1f)
            val alpha = (0.18f + 0.5f * twinkle) * fadeIn * edge
            val x = dotX[i] * size.width + sin(t * 0.4f + dotPhase[i]) * 6f * density
            val py = y * size.height
            val radius = dotRadius[i] * density
            val color = ParticleColors[dotColor[i]]
            if (dotIsStar[i]) {
                val factor = radius * 0.25f * (0.7f + 0.5f * twinkle)
                translate(x, py) {
                    scale(factor, pivot = Offset.Zero) {
                        translate(-12f, -12f) { drawPath(StarPath, color.copy(alpha = (alpha + 0.15f).coerceAtMost(1f))) }
                    }
                }
            } else {
                drawCircle(color.copy(alpha = alpha * 0.15f), radius * 2.4f, Offset(x, py))
                drawCircle(color.copy(alpha = alpha * 0.3f), radius * 1.5f, Offset(x, py))
                drawCircle(color.copy(alpha = alpha * 0.55f), radius, Offset(x, py))
            }
        }
    }

    private fun DrawScope.drawNotes() {
        for (i in 0 until NoteCount) {
            if (!noteActive[i]) continue
            val age = noteAge[i]
            val k = age / noteLife[i]
            val alpha = min(k / 0.15f, 1f) * min((1f - k) / 0.35f, 1f)
            val wave = sin(age * noteFreq[i] + notePhase[i])
            val color = ParticleColors[noteColor[i]]
            val factor = noteSize[i] / 24f * (0.7f + 0.3f * min(k * 5f, 1f))
            translate(noteX[i] + wave * noteSway[i], noteY[i] - noteRise[i] * age) {
                rotate((noteTilt[i] + wave * 0.25f) * RadiansToDegrees, pivot = Offset.Zero) {
                    scale(factor, pivot = Offset.Zero) {
                        translate(-12f, -12f) {
                            if (noteIsStar[i]) {
                                drawPath(StarPath, color.copy(alpha = alpha * 0.95f))
                            } else {
                                drawPath(NotePath, color.copy(alpha = alpha * 0.25f), style = NoteGlowStroke)
                                drawPath(NotePath, color.copy(alpha = alpha * 0.95f), style = NoteStroke)
                                drawCircle(color.copy(alpha = alpha * 0.95f), 3.2f, Offset(6.5f, 18f))
                                drawCircle(color.copy(alpha = alpha * 0.95f), 3.2f, Offset(17.5f, 16f))
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val DotCount = 30
        const val NoteCount = 6
        const val TwoPi = (2.0 * PI).toFloat()
        const val DegreesToRadians = (PI / 180.0).toFloat()
        const val RadiansToDegrees = (180.0 / PI).toFloat()
        const val RayCore = 2.2f
        const val RayFeather = 4.8f
        val RayAngles = floatArrayOf(-30f, -14f, 3f, 20f, 36f)

        val ParticleColors = arrayOf(Color(0xFF8CCBF2), Color(0xFFF7B3D3), Color(0xFFBEA8F4), Color(0xFFF5C96A))
        val NoteStroke = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val NoteGlowStroke = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val NotePath: Path = PathParser().parsePathString("M9 18V5l11-2v13").toPath()
        val StarPath: Path = PathParser()
            .parsePathString("M12 0C12.7 6.4 15.6 9.6 24 12 15.6 14.4 12.7 17.6 12 24 11.3 17.6 8.4 14.4 0 12 8.4 9.6 11.3 6.4 12 0z")
            .toPath()
    }
}

internal fun fractionOf(value: Float): Float = value - floor(value)

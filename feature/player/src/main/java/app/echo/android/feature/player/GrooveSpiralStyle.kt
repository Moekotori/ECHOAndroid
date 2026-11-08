package app.echo.android.feature.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import kotlin.math.max

/** Ink-blue night, a black record and one coral accent for the stylus and the line being sung. */
internal object GrooveSpiralStyle {
    val Ink = Color(0xFF0B0E17)
    val Record = Color(0xFF07090E)
    val Text = Color(0xFFE9ECF7)
    val Dim = Color(0xFFA8B0C8)
    val Accent = Color(0xFFFF7A63)

    /** Label diameter as a share of the record's diameter. */
    const val LabelFraction = 0.3053f
}

/** What is cut into the groove: the track's facts, drifting slowly, or the lyrics, led past the stylus. */
internal sealed interface GrooveText {
    class Meta(val text: String) : GrooveText

    /** [lines] must be sorted by start time and contain only synced, non-blank lines. */
    class Lyrics(val lines: List<GrooveLine>, val position: State<Long>, val lastEndMs: Long) : GrooveText
}

/**
 * A record whose groove carries text. The cover is the label and turns while [spinning]; the groove path and
 * every text measurement are built once per size, and playback position is only read while drawing.
 */
@Composable
internal fun GrooveDisc(
    artworkUri: String?,
    text: GrooveText,
    spinning: Boolean,
    modifier: Modifier = Modifier,
) {
    val spin = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        spin.snapTo(spin.value % 360f)
        while (true) spin.animateTo(spin.value + 360f, tween(SPIN_MS, easing = LinearEasing))
    }
    val spinState = spin.asState()
    BoxWithConstraints(modifier.aspectRatio(1f)) {
        Spacer(Modifier.fillMaxSize().drawWithCache { drawGroove(text, spinState) })
        EchoArtworkImage(
            artworkUri, null,
            Modifier.align(Alignment.Center).size(maxWidth * GrooveSpiralStyle.LabelFraction)
                .graphicsLayer { rotationZ = spin.value }
                .border(1.5.dp, GrooveSpiralStyle.Ink, CircleShape),
            shape = CircleShape, sizeClass = EchoArtworkSize.Card,
        )
    }
}

private fun CacheDrawScope.drawGroove(text: GrooveText, spin: State<Float>): DrawResult {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val spiral = GrooveSpiralGeometry.Default
    val groove = android.graphics.Path().apply {
        for (k in spiral.xs.indices) {
            val x = center.x + spiral.xs[k] * radius
            val y = center.y + spiral.ys[k] * radius
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    val rings = Path().apply {
        var r = 0.34f * radius
        while (r <= 0.93f * radius) { addOval(Rect(center, r)); r += 0.018f * radius }
    }
    val textSize = 0.105f * radius
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.SERIF
        letterSpacing = 0.06f
    }
    val sung = Paint(paint).apply { color = GrooveSpiralStyle.Accent.toArgb() }
    val needleLength = spiral.needleLength * radius
    val needle = Offset(center.x + spiral.xs[spiral.needleIndex] * radius, center.y + spiral.ys[spiral.needleIndex] * radius)

    val lyrics = text as? GrooveText.Lyrics
    val track = lyrics?.let { source ->
        val starts = LongArray(source.lines.size) { source.lines[it].startMs }
        val ends = LongArray(starts.size) { i ->
            if (i < starts.lastIndex) starts[i + 1] else max(source.lastEndMs, starts[i] + FALLBACK_LAST_LINE_MS)
        }
        val lengths = FloatArray(starts.size) { paint.measureText(source.lines[it].text) }
        GrooveLyricsTrack(starts, ends, lengths, gap = textSize * 2f, introLead = textSize * 5f)
    }
    val meta = (text as? GrooveText.Meta)?.let { source ->
        val unit = source.text + META_SEPARATOR
        val unitLength = paint.measureText(unit).coerceAtLeast(1f)
        val repeats = (spiral.totalLength * radius / unitLength).toInt() + 2
        unit.repeat(repeats) to unitLength
    }
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.14f), 0.35f to Color.Transparent, 0.65f to Color.Transparent,
        1f to Color.White.copy(alpha = 0.1f), start = Offset(center.x - radius, center.y - radius),
        end = Offset(center.x + radius, center.y + radius),
    )
    val ringColor = Color.White.copy(alpha = 0.05f)
    val armColor = Color(0xFFC7CDE0)
    val pivot = Offset(center.x + 0.86f * radius, center.y - 0.94f * radius)
    val headBase = Offset(center.x + 0.12f * radius, center.y - 0.73f * radius)
    val distanceScale = radius / REFERENCE_RADIUS

    return onDrawBehind {
        drawCircle(GrooveSpiralStyle.Record, radius, center)
        drawPath(rings, ringColor, style = Stroke(1f))
        drawLine(armColor.copy(alpha = 0.9f), pivot, headBase, 0.026f * radius, StrokeCap.Round)
        drawCircle(armColor, 0.07f * radius, pivot)
        drawCircle(GrooveSpiralStyle.Ink, 0.03f * radius, pivot)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (meta != null) {
                paint.color = GrooveSpiralStyle.Dim.copy(alpha = 0.55f).toArgb()
                val drift = (spin.value / 360f) % 1f
                native.drawTextOnPath(meta.first, groove, -meta.second + drift * meta.second, 0f, paint)
            } else if (track != null && lyrics != null && track.count > 0) {
                val position = lyrics.position.value
                val cursor = track.cursor(position)
                val current = track.indexAt(position)
                for (i in 0 until track.count) {
                    val start = needleLength - cursor + track.offsets[i]
                    val length = track.lengths[i]
                    if (start > spiral.totalLength * radius || start + length < 0f) continue
                    val line = lyrics.lines[i].text
                    val away = start + length / 2f - needleLength
                    val alpha = when {
                        i == current -> 1f
                        away < 0f -> max(0.07f, 0.5f + away / (1500f * distanceScale))
                        else -> max(0.05f, 0.5f - away / (1300f * distanceScale))
                    }
                    paint.color = (if (i == current) GrooveSpiralStyle.Text else GrooveSpiralStyle.Dim).toArgb()
                    paint.alpha = (alpha * 255f).toInt()
                    native.drawTextOnPath(line, groove, start, 0f, paint)
                    if (i == current) {
                        val chars = line.codePointCount(0, line.length)
                        val done = (track.progress(i, position) * chars).toInt()
                        if (done > 0) {
                            native.drawTextOnPath(line.substring(0, line.offsetByCodePoints(0, done)), groove, start, 0f, sung)
                        }
                    }
                }
            }
        }
        // Stylus on top of the groove.
        drawLine(GrooveSpiralStyle.Accent, headBase, needle, 0.034f * radius, StrokeCap.Round)
        drawCircle(Color.White, 0.012f * radius, needle)
        drawCircle(brush = rim, radius = radius, center = center)
    }
}

private const val SPIN_MS = 14_000
private const val FALLBACK_LAST_LINE_MS = 4_000L
private const val META_SEPARATOR = "  ·  "

/** The groove is laid out for a record of this radius in dp-less units; distances scale from it. */
private const val REFERENCE_RADIUS = 190f

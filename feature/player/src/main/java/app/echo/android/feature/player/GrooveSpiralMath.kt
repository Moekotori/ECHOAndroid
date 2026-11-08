package app.echo.android.feature.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** One lyric line as the groove sees it: when it starts and what it says. */
internal class GrooveLine(val startMs: Long, val text: String)

/**
 * An Archimedean groove running clockwise from the outer rim to the label, in unit-disc space
 * (the record's radius is 1) so one layout serves every size. Built once; callers scale by the radius.
 */
internal class GrooveSpiralGeometry(
    val outerRadius: Float = 0.926f,
    val innerRadius: Float = 0.337f,
    val turns: Float = 3.4f,
    steps: Int = 680, // 200 per turn, so one full turn lands exactly on a point
) {
    /** Offsets from the record's centre, measured from 12 o'clock, clockwise. */
    val xs = FloatArray(steps + 1)
    val ys = FloatArray(steps + 1)

    /** Cumulative arc length at every point. */
    val lengths = FloatArray(steps + 1)
    val totalLength: Float
    val needleIndex: Int

    /** Where the stylus rides: one full turn in, directly above the centre, where text is upright. */
    val needleLength: Float

    init {
        val total = turns * 2f * PI.toFloat()
        var needle = -1
        for (k in 0..steps) {
            val theta = k.toFloat() / steps * total
            val radius = outerRadius - (outerRadius - innerRadius) * theta / total
            xs[k] = sin(theta) * radius
            ys[k] = -cos(theta) * radius
            if (k > 0) lengths[k] = lengths[k - 1] + hypot(xs[k] - xs[k - 1], ys[k] - ys[k - 1])
            if (needle < 0 && theta >= 2f * PI.toFloat() - 1e-3f) needle = k
        }
        needleIndex = needle.coerceAtLeast(0)
        needleLength = lengths[needleIndex]
        totalLength = lengths[steps]
    }

    companion object {
        val Default = GrooveSpiralGeometry()
    }
}

/**
 * Lays the lyric lines end to end along the groove and maps playback time to how far the record has
 * turned under the stylus. [lengths] are measured text widths in the same unit as [gap].
 */
internal class GrooveLyricsTrack(
    private val starts: LongArray,
    ends: LongArray,
    val lengths: FloatArray,
    val gap: Float,
    private val introLead: Float,
) {
    val count = starts.size
    private val singEnds = LongArray(count) { starts[it] + ((ends[it] - starts[it]) * SING_SHARE).toLong() }

    /** Distance from the first line's start to each line's start. */
    val offsets = FloatArray(count).also { out ->
        var at = 0f
        for (i in 0 until count) { out[i] = at; at += lengths[i] + gap }
    }
    private val restSpans = LongArray(count) { (ends[it] - singEnds[it]).coerceAtLeast(1L) }

    fun indexAt(positionMs: Long): Int {
        var low = 0
        var high = count - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= positionMs) { found = mid; low = mid + 1 } else high = mid - 1
        }
        return found
    }

    /** Share of the line already sung, 0..1. */
    fun progress(index: Int, positionMs: Long): Float {
        if (index < 0 || index >= count) return 0f
        val span = (singEnds[index] - starts[index]).coerceAtLeast(1L)
        return ((positionMs - starts[index]).toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * The point of the text under the stylus. A line sweeps past at the pace it is sung, then the gap to the
     * next line passes during the rest of its slot, so the value never jumps at a line boundary.
     */
    fun cursor(positionMs: Long): Float {
        if (count == 0) return 0f
        val index = indexAt(positionMs)
        if (index < 0) {
            val lead = starts[0].coerceAtLeast(1L)
            return -introLead * (1f - (positionMs.toFloat() / lead).coerceIn(0f, 1f))
        }
        val rest = ((positionMs - singEnds[index]).toFloat() / restSpans[index]).coerceIn(0f, 1f)
        return offsets[index] + lengths[index] * progress(index, positionMs) + gap * rest
    }

    private companion object {
        /** The rest of a slot is breathing room between lines. */
        const val SING_SHARE = 0.88f
    }
}

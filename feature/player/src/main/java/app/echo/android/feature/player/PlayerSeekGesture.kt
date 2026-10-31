package app.echo.android.feature.player

import kotlin.math.abs

/** One finger owns one seek. Direction is decided once, before consuming any movement. */
internal class PlayerSeekGesture(
    private val downX: Float,
    private val downY: Float,
    private val slop: Float,
    private val fractionAt: (Float) -> Float,
    private val onStart: () -> Unit,
    private val onPreview: (Float) -> Unit,
    private val onCommit: (Float) -> Unit,
    private val onCancel: () -> Unit,
) {
    var dragging = false
        private set
    var finished = false
        private set

    fun move(x: Float, y: Float): Boolean {
        if (finished) return false
        if (!dragging) {
            val dx = abs(x - downX)
            val dy = abs(y - downY)
            if (dx * dx + dy * dy <= slop * slop) return false
            if (dx <= dy) { cancel(); return false }
            dragging = true
            onStart()
        }
        onPreview(fractionAt(x))
        return true
    }

    fun release(x: Float, y: Float, inside: Boolean): Boolean {
        if (finished) return false
        // UP can contain movement absent from the last MOVE, including the first crossing of slop.
        move(x, y)
        if (finished) return false
        if (!dragging && !inside) { cancel(); return false }
        finished = true
        onCommit(fractionAt(x))
        return true
    }

    fun cancel() {
        if (finished) return
        finished = true
        onCancel()
    }
}

internal fun playerSeekFraction(x: Float, width: Float, inset: Float, rtl: Boolean): Float {
    val edge = inset.coerceIn(0f, width.coerceAtLeast(0f) / 2f)
    val fraction = ((x - edge) / (width - edge * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return if (rtl) 1f - fraction else fraction
}

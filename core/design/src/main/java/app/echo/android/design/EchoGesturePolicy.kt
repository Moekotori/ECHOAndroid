package app.echo.android.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** Nested scroll begins after touch slop; keep that drag's axis until it ends. */
internal class EchoPagerDragAxis {
    private var horizontal: Boolean? = null
    val isVertical: Boolean get() = horizontal == false

    fun ownsScroll(consumed: Offset, available: Offset): Boolean {
        if (horizontal == null && (consumed != Offset.Zero || available != Offset.Zero)) {
            horizontal = echoHorizontalGestureOwnsScroll(consumed, available)
        }
        return horizontal == true
    }

    fun reset() { horizontal = null }
}

/** Account for what the child already consumed; tiny diagonal residue must not steal its scroll. */
fun echoHorizontalGestureOwnsScroll(consumed: Offset, available: Offset): Boolean {
    val horizontal = abs(consumed.x) + abs(available.x)
    val vertical = abs(consumed.y) + abs(available.y)
    return horizontal > vertical
}

/** Settle a displaced pager, but never turn a vertical list fling's residue into paging. */
fun echoPagerOwnsFling(consumed: Velocity, available: Velocity, pageOffsetFraction: Float): Boolean {
    if (abs(pageOffsetFraction) > 0.001f) return true
    return abs(available.x) >= 40f && echoHorizontalGestureOwnsScroll(
        Offset(consumed.x, consumed.y), Offset(available.x, available.y),
    )
}

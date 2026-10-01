package app.echo.android.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

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

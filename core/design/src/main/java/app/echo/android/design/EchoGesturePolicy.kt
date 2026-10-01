package app.echo.android.design

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** Account for what the child already consumed; tiny diagonal residue must not steal its scroll. */
fun echoHorizontalGestureOwnsScroll(consumed: Offset, available: Offset): Boolean {
    val horizontal = abs(consumed.x) + abs(available.x)
    val vertical = abs(consumed.y) + abs(available.y)
    return horizontal > vertical
}

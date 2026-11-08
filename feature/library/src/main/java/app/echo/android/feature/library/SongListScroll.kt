package app.echo.android.feature.library

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import app.echo.android.design.EchoMotion
import kotlin.math.abs

/** Bound animated work to one viewport, regardless of the library's size. */
internal suspend fun LazyListState.scrollToSong(index: Int, lightweight: Boolean) {
    val layout = layoutInfo
    val height = (layout.viewportEndOffset - layout.viewportStartOffset - layout.afterContentPadding)
        .coerceAtLeast(0)
    val targetTop = if (index == 0) 0 else layout.viewportStartOffset + height / 3
    if (lightweight || height == 0) {
        scrollToItem(index, -targetTop)
        return
    }
    val visible = layout.visibleItemsInfo.firstOrNull { it.index == index }
    val distance = visible?.let { it.offset - targetTop }
    if (distance != null && abs(distance) <= height) {
        if (distance != 0) animateScrollBy(distance.toFloat(), tween(220, easing = EchoMotion.Silk))
    } else {
        // Teleport close to the destination, then settle over a short, fixed distance.
        val entry = if (index >= firstVisibleItemIndex) height / 4 else -height / 4
        scrollToItem(index, -targetTop - entry)
        animateScrollBy(entry.toFloat(), tween(220, easing = EchoMotion.Silk))
    }
}

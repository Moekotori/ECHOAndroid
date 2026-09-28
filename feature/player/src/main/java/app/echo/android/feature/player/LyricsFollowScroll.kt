package app.echo.android.feature.player

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/** Owned by one lyric list, retaining velocity when the next short line interrupts a spring. */
internal class LyricsFollowMotion {
    var velocity = 0f
    var anchored = false
    fun reset() { velocity = 0f; anchored = false }
}

internal fun lyricFollowVelocity(distance: Float, previousVelocity: Float): Float {
    if (!previousVelocity.isFinite() || distance * previousVelocity <= 0f || !distance.isFinite()) return 0f
    // A small layout correction must not inherit a full line's speed and overshoot its anchor.
    val limit = abs(distance) * 10f
    return previousVelocity.coerceIn(-limit, limit)
}

internal suspend fun followLyricsLine(
    list: LazyListState,
    index: Int,
    motion: LyricsFollowMotion,
    lightweight: Boolean,
    motionMode: String,
) {
    if (!motion.anchored || list.layoutInfo.visibleItemsInfo.none { it.index == index }) {
        // Initial content / a far seek should not fly past an entire song's lyrics.
        motion.velocity = 0f
        list.scrollToItem(index)
        withFrameNanos { }
        motion.anchored = true
    }
    val distance = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset?.toFloat() ?: return
    if (abs(distance) < 0.5f) { motion.velocity = 0f; return }
    list.scroll {
        var previous = 0f
        AnimationState(0f, initialVelocity = if (lightweight) 0f else lyricFollowVelocity(distance, motion.velocity))
            .animateTo(
                targetValue = distance,
                animationSpec = if (lightweight) tween(240) else spring(
                    dampingRatio = 1f,
                    stiffness = when (motionMode) { "calm" -> 240f; "stage" -> 110f; else -> 160f },
                    visibilityThreshold = 0.5f,
                ),
            ) {
                val requested = value - previous
                val consumed = scrollBy(requested)
                previous = value
                motion.velocity = velocity
                if (abs(requested - consumed) > 0.5f) cancelAnimation()
            }
    }
    motion.velocity = 0f
}

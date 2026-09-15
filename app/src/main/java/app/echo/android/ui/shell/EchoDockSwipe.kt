package app.echo.android.ui.shell

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.echo.android.EchoTab

/** Share the pager's scroll ownership so taps and page gestures can interrupt dock drags. */
@Composable
internal fun rememberDockSwipeModifier(
    pager: PagerState,
    flingBehavior: TargetedFlingBehavior,
): Modifier {
    val tabWidth = remember { floatArrayOf(1f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val state = remember(pager) {
        object : DraggableState {
            fun scrollDelta(pixels: Float): Float {
                val pageSize = pager.layoutInfo.pageSize.toFloat()
                if (pageSize <= 0f) return 0f
                val position = pager.currentPage + pager.currentPageOffsetFraction
                val target = (position - pixels / tabWidth[0]).coerceIn(
                    EchoPagerPage.Now.ordinal.toFloat(),
                    EchoPagerPage.Diagnostics.ordinal.toFloat(),
                )
                return (target - position) * pageSize
            }

            override suspend fun drag(
                dragPriority: MutatePriority,
                block: suspend DragScope.() -> Unit,
            ) {
                pager.scroll(dragPriority) {
                    val scrollScope = this
                    block(object : DragScope {
                        override fun dragBy(pixels: Float) {
                            scrollScope.scrollBy(scrollDelta(pixels))
                        }
                    })
                }
            }

            override fun dispatchRawDelta(delta: Float) {
                pager.dispatchRawDelta(scrollDelta(delta))
            }
        }
    }
    return Modifier
        .onSizeChanged { tabWidth[0] = (it.width.toFloat() / EchoTab.entries.size).coerceAtLeast(1f) }
        .draggable(
            state = state,
            orientation = Orientation.Horizontal,
            reverseDirection = rtl,
            onDragStopped = { velocity ->
                val pageSize = pager.layoutInfo.pageSize.toFloat().coerceAtLeast(1f)
                val pageVelocity = -velocity * pageSize / tabWidth[0]
                pager.scroll {
                    with(flingBehavior) { performFling(pageVelocity) }
                }
            },
        )
}

package app.echo.android.ui.shell

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Recover an idle partial page without letting the recovery's own scroll state cancel it. */
internal suspend fun Flow<Boolean>.recoverStrandedPager(
    isStillStranded: () -> Boolean,
    settle: suspend () -> Unit,
) = coroutineScope {
    val recoveryScope = this
    collectLatest { stranded ->
        if (!stranded) return@collectLatest
        // Allow the normal drag-to-fling handoff before taking ownership of scrolling.
        delay(120)
        if (!isStillStranded()) return@collectLatest
        // Keep observing while animating. Busy state cancels the grace period, but must
        // not cancel the animation that caused it. A new pager mutation may interrupt
        // this child without ending the observer; leaving the owner cancels both.
        recoveryScope.launch {
            settle()
        }
    }
}

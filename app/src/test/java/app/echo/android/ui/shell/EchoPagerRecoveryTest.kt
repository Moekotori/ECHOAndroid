package app.echo.android.ui.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoPagerRecoveryTest {
    @Test
    fun recoveryFinishesWhenItsOwnAnimationMakesPagerBusy() = runBlocking {
        val stranded = MutableStateFlow(true)
        val finished = CompletableDeferred<Unit>()
        val observer = launch {
            stranded.recoverStrandedPager(isStillStranded = { stranded.value }) {
                stranded.value = false // animateScrollToPage starts scrolling.
                delay(16) // Suspend across a frame while the observer receives busy state.
                currentCoroutineContext().ensureActive()
                finished.complete(Unit)
            }
        }
        try {
            withTimeout(2_000) { finished.await() }
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test
    fun interruptedScrollDoesNotDisableFutureRecovery() = runBlocking {
        val stranded = MutableStateFlow(true)
        val finished = CompletableDeferred<Unit>()
        var attempts = 0
        val observer = launch {
            stranded.recoverStrandedPager(isStillStranded = { stranded.value }) {
                attempts++
                stranded.value = false
                yield()
                if (attempts == 1) {
                    stranded.value = true
                    throw CancellationException("New gesture owns the pager")
                }
                finished.complete(Unit)
            }
        }
        try {
            withTimeout(2_000) { finished.await() }
            assertEquals(2, attempts)
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test
    fun normalFlingTakingOverDuringGracePeriodSkipsRecovery() = runBlocking {
        var attempts = 0
        flowOf(true).recoverStrandedPager(isStillStranded = { false }) { attempts++ }
        assertEquals(0, attempts)
    }
}

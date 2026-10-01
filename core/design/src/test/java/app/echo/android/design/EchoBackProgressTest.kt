package app.echo.android.design

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class EchoBackProgressTest {
    private class Frames : MonotonicFrameClock {
        val ticks = Channel<Long>(Channel.UNLIMITED)
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(ticks.receive())
    }

    @Test fun manualInputCancelsRecoveryWithoutAStaleWrite() = runBlocking {
        withTimeout(1000) {
            val frames = Frames()
            val progress = EchoBackProgress(CoroutineScope(coroutineContext + frames)) { false }
            progress.update(0.8f)
            val old = progress.restore()!!
            yield()
            repeat(4) { frames.ticks.send(it * 16_000_000L); yield() }
            assertTrue(progress.value in 0f..0.8f)
            progress.update(0.55f)
            old.join()
            assertTrue(old.isCancelled)
            assertEquals(0.55f, progress.value, 0f)
            progress.reset()
            assertEquals(0f, progress.value, 0f)
        }
    }

    @Test fun cancelledGestureRestoresFromItsCurrentPosition() = runBlocking {
        withTimeout(1000) {
            val frames = Frames()
            val progress = EchoBackProgress(CoroutineScope(coroutineContext + frames)) { true }
            progress.update(0.6f)
            val restore = progress.restore()!!
            assertEquals(0.6f, progress.value, 0f)
            yield()
            repeat(20) { frames.ticks.send(it * 16_000_000L); yield() }
            restore.join()
            assertEquals(0f, progress.value, 0f)
        }
    }
}

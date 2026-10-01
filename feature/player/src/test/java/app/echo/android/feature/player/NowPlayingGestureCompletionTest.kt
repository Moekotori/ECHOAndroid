package app.echo.android.feature.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NowPlayingGestureCompletionTest {
    @Test fun normalReleaseSurvivesObserverCleanup() = runBlocking {
        val completion = NowPlayingGestureCompletion()
        completion.begin()
        val result = completion.result!!
        completion.finish(true)
        completion.finish(false)
        assertTrue(result.await())
    }
    @Test fun cancellationNeverCommits() = runBlocking {
        val completion = NowPlayingGestureCompletion()
        completion.begin()
        completion.finish(false)
        assertFalse(completion.result!!.await())
    }
    @Test fun newFingerCancelsPendingOutcomeWithoutReusingIt() = runBlocking {
        val completion = NowPlayingGestureCompletion()
        completion.begin()
        val previous = completion.result!!
        completion.begin()
        assertFalse(previous.await())
        assertFalse(completion.result!!.isCompleted)
        completion.finish(true)
        assertTrue(completion.result!!.await())
    }
}

package app.echo.android.playback

import androidx.media3.common.Player
import app.echo.android.model.playback.EchoSleepTimerCancellationReason
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class EchoSleepTimerCancellationEventsTest {
    @Test fun repeatedCallbacksNotifyOnceAndNormalCompletionDoesNotNotify() = runBlocking {
        val feedback = EchoSleepTimerCancellationEvents()
        val received = mutableListOf<EchoSleepTimerCancellationReason>()
        val collect = launch(start = CoroutineStart.UNDISPATCHED) { feedback.notices.collect { received += it } }
        var finishing = true
        var cancelled = 0
        repeat(2) {
            feedback.cancelIfFinishing(finishing, EchoSleepTimerCancellationReason.OutputChanged) {
                cancelled++; finishing = false
            }
        }
        yield()
        assertEquals(1, cancelled)
        assertEquals(listOf(EchoSleepTimerCancellationReason.OutputChanged), received)
        feedback.cancelIfFinishing(false, EchoSleepTimerCancellationReason.PlaybackChanged) { fail() }
        yield()
        assertEquals(1, received.size)
        collect.cancel()
    }

    @Test fun backgroundNoticesAreNotReplayed() = runBlocking {
        val feedback = EchoSleepTimerCancellationEvents()
        feedback.cancelIfFinishing(true, EchoSleepTimerCancellationReason.AudioFocusLost) { }
        val received = mutableListOf<EchoSleepTimerCancellationReason>()
        val collect = launch(start = CoroutineStart.UNDISPATCHED) { feedback.notices.collect { received += it } }
        yield()
        assertTrue(received.isEmpty())
        collect.cancel()
    }

    @Test fun commandsKeepTrackChangesDistinctFromPlaybackAndSeek() {
        assertEquals(EchoSleepTimerCancellationReason.TrackChanged, sleepTimerCancellationForCommand(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals(EchoSleepTimerCancellationReason.Seeked, sleepTimerCancellationForCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertEquals(EchoSleepTimerCancellationReason.PlaybackChanged, sleepTimerCancellationForCommand(Player.COMMAND_PLAY_PAUSE))
        assertEquals(EchoSleepTimerCancellationReason.QueueChanged, sleepTimerCancellationForCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS))
    }
}

package app.echo.android.playback

import app.echo.android.model.playback.EchoSleepTimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSleepTimerPolicyTest {
    @Test
    fun timedRemainingCountsDownAndPausesAtZero() {
        val remaining = EchoSleepTimerPolicy.remainingMs(
            mode = EchoSleepTimerMode.Timed,
            nowEpochMs = 10_000L,
            timedEndEpochMs = 25_000L,
            trackRemainingMs = 180_000L,
            trackDurationKnown = true,
        )
        assertEquals(15_000L, remaining)
        assertFalse(EchoSleepTimerPolicy.shouldPause(EchoSleepTimerMode.Timed, remaining))
        assertTrue(EchoSleepTimerPolicy.shouldPause(EchoSleepTimerMode.Timed, 0L))
        assertEquals(1f, EchoSleepTimerPolicy.fadeMultiplier(remaining), 0.001f)
        assertEquals(0.5f, EchoSleepTimerPolicy.fadeMultiplier(4_000L), 0.001f)
        assertEquals(0f, EchoSleepTimerPolicy.fadeMultiplier(0L), 0.001f)
        assertEquals(1f, EchoSleepTimerPolicy.fadeMultiplier(4_000L, allowFade = false), 0.001f)
        assertEquals(
            1f,
            EchoSleepTimerPolicy.fadeMultiplier(0L, mode = EchoSleepTimerMode.Off),
            0.001f,
        )
        assertTrue(EchoSleepTimerPolicy.isTimedMinutesSelected(15, 15))
        assertFalse(EchoSleepTimerPolicy.isTimedMinutesSelected(30, 15))
    }

    @Test
    fun endOfTrackUsesTrackRemainingAndFadesNearTheEnd() {
        val remaining = EchoSleepTimerPolicy.remainingMs(
            mode = EchoSleepTimerMode.EndOfTrack,
            nowEpochMs = 0L,
            timedEndEpochMs = null,
            trackRemainingMs = 4_000L,
            trackDurationKnown = true,
        )
        assertEquals(4_000L, remaining)
        assertEquals(0.5f, EchoSleepTimerPolicy.fadeMultiplier(remaining), 0.001f)
        assertTrue(EchoSleepTimerPolicy.shouldPauseAtEndOfMediaItem(EchoSleepTimerMode.EndOfTrack))
        assertTrue(EchoSleepTimerPolicy.shouldCancelEndOfTrackOnSeek(EchoSleepTimerMode.EndOfTrack))
        assertFalse(EchoSleepTimerPolicy.shouldPauseAtEndOfMediaItem(EchoSleepTimerMode.Timed))
        assertEquals(250L, EchoSleepTimerPolicy.tickMs(4_000L))
        assertEquals(1_000L, EchoSleepTimerPolicy.tickMs(20_000L))
        assertEquals(1_000L, EchoSleepTimerPolicy.tickMs(0L))
    }

    @Test
    fun unknownDurationDoesNotPauseImmediately() {
        val remaining = EchoSleepTimerPolicy.remainingMs(
            mode = EchoSleepTimerMode.EndOfTrack,
            nowEpochMs = 0L,
            timedEndEpochMs = null,
            trackRemainingMs = 0L,
            trackDurationKnown = false,
        )
        assertEquals(EchoSleepTimerPolicy.UnknownRemainingMs, remaining)
        assertFalse(EchoSleepTimerPolicy.shouldPause(EchoSleepTimerMode.EndOfTrack, remaining))
        assertEquals(1f, EchoSleepTimerPolicy.fadeMultiplier(remaining), 0.001f)
    }
}

package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutePausePolicyTest {
    @Test fun unknownVolumeRangeDoesNotMeanSilentAndUsbIgnoresSystemMute() {
        assertFalse(mutePauseIsMuted(false, false, false, false, 0, 0))
        assertTrue(mutePauseIsMuted(false, false, false, false, 0, 15))
        assertFalse(mutePauseIsMuted(false, false, true, true, 0, 15))
        assertTrue(mutePauseIsMuted(true, false, true, false, 8, 15))
        assertFalse(mutePauseIsMuted(true, true, true, true, 0, 15))
    }

    @Test fun sleepTimerExpiredEvenWhenItWasStartedAfterMuteCannotResumePlayback() {
        val policy = MutePausePolicy()
        assertEquals(MutePauseAction.Pause, policy.update(true, true, "track", true, true, epoch = 0))
        assertEquals(MutePauseAction.None, policy.update(true, false, "track", false, true, epoch = 1))
        assertEquals(MutePauseAction.None, policy.update(true, false, "track", false, true, epoch = 1))
    }

    @Test fun playbackStartingAfterAlreadyMutedIsStillPaused() {
        val policy = MutePausePolicy()
        assertEquals(MutePauseAction.None, policy.update(true, true, null, false, true))
        assertEquals(MutePauseAction.None, policy.update(true, true, "restored-track", false, true))
        assertEquals(MutePauseAction.Pause, policy.update(true, true, "restored-track", true, true))
        assertEquals(MutePauseAction.Resume, policy.update(true, false, "restored-track", false, true))
    }

    @Test fun resumesOnlyItsOwnPauseOnce() {
        val policy = MutePausePolicy()
        assertEquals(MutePauseAction.Pause, policy.update(true, true, "track/output", true, true))
        assertEquals(MutePauseAction.None, policy.update(true, true, "track/output", false, true))
        assertEquals(MutePauseAction.Resume, policy.update(true, false, "track/output", false, true))
        assertEquals(MutePauseAction.None, policy.update(true, false, "track/output", false, true))
    }

    @Test fun manualCommandsTrackChangesAndDisabledOptionCancelOwnership() {
        for (cancel in 0..3) {
            val policy = MutePausePolicy()
            policy.update(true, true, "original", true, true)
            when (cancel) {
                0 -> policy.interrupt()
                1 -> policy.update(true, true, "another/output", false, true)
                2 -> policy.update(false, true, "original", false, true)
                3 -> policy.update(true, false, "original", false, false)
            }
            assertEquals(MutePauseAction.None, policy.update(true, false, "original", false, true))
        }
    }

    @Test fun existingManualPauseAndDefaultOffAreNeverResumed() {
        val policy = MutePausePolicy()
        assertEquals(MutePauseAction.None, policy.update(false, true, "track", true, true))
        assertEquals(MutePauseAction.None, policy.update(true, true, "track", false, true))
        assertEquals(MutePauseAction.None, policy.update(true, false, "track", false, true))
    }
}

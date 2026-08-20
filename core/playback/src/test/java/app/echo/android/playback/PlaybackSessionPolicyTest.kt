package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionPolicyTest {
    @Test
    fun emptyPersistIsSkippedUntilRestoreCompletes() {
        assertFalse(
            PlaybackSessionPolicy.shouldPersistSavedSession(
                restoreCompleted = false,
                hasPendingPlay = false,
                queueEmpty = true,
            ),
        )
    }

    @Test
    fun pendingPlayBlocksEmptySessionWrite() {
        assertFalse(
            PlaybackSessionPolicy.shouldPersistSavedSession(
                restoreCompleted = true,
                hasPendingPlay = true,
                queueEmpty = true,
            ),
        )
    }

    @Test
    fun restoreCompleteMayPersistEmptyQueue() {
        assertTrue(
            PlaybackSessionPolicy.shouldPersistSavedSession(
                restoreCompleted = true,
                hasPendingPlay = false,
                queueEmpty = true,
            ),
        )
    }

    @Test
    fun usbUnmuteDoesNotRestoreZero() {
        assertEquals(0.75f, PlaybackSessionPolicy.restoredVolumeAfterUsbMute(0f, 0.75f))
        assertEquals(0.4f, PlaybackSessionPolicy.restoredVolumeAfterUsbMute(0.4f, 1f), 0.0001f)
    }

    @Test
    fun errorAndIdleRequirePrepareBeforePlay() {
        assertTrue(PlaybackSessionPolicy.shouldPrepareBeforePlay(hasPlayerError = true, playbackStateIdle = false))
        assertTrue(PlaybackSessionPolicy.shouldPrepareBeforePlay(hasPlayerError = false, playbackStateIdle = true))
        assertFalse(PlaybackSessionPolicy.shouldPrepareBeforePlay(hasPlayerError = false, playbackStateIdle = false))
    }

    @Test
    fun pendingQueueReplaceSkipsRestore() {
        assertTrue(shouldSkipSavedSessionRestore(listOf(false, true)))
        assertFalse(shouldSkipSavedSessionRestore(listOf(false, false)))
        assertFalse(shouldSkipSavedSessionRestore(emptyList()))
    }

    @Test
    fun driverTestDoesNotClaimWhilePlaying() {
        assertFalse(PlaybackSessionPolicy.shouldClaimUsbInterfaceForDriverTest(isPlayingToUsb = true))
        assertTrue(PlaybackSessionPolicy.shouldClaimUsbInterfaceForDriverTest(isPlayingToUsb = false))
    }
}

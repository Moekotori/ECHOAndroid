package app.echo.android.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaybackRestoreTest {
    @Test
    fun queueReplacingPlaySkipsRestore() {
        assertTrue(shouldSkipSavedSessionRestore(listOf(true)))
        assertTrue(shouldSkipSavedSessionRestore(listOf(false, true)))
    }

    @Test
    fun playPauseAndSkipDoNotSkipRestore() {
        assertFalse(shouldSkipSavedSessionRestore(emptyList()))
        assertFalse(shouldSkipSavedSessionRestore(listOf(false)))
        assertFalse(shouldSkipSavedSessionRestore(listOf(false, false)))
    }

    @Test
    fun playPauseAndSkipRestoreBeforeFlush() {
        assertTrue(shouldRestoreSavedSessionBeforeFlushingPending(listOf(false)))
        assertTrue(shouldRestoreSavedSessionBeforeFlushingPending(emptyList()))
        assertFalse(shouldRestoreSavedSessionBeforeFlushingPending(listOf(true)))
    }

    @Test
    fun commandsStayQueuedUntilRestoreAndFlushComplete() {
        assertTrue(shouldQueueControllerActionUntilSessionReady(sessionReadyForCommands = false))
        assertFalse(shouldQueueControllerActionUntilSessionReady(sessionReadyForCommands = true))
    }

    @Test
    fun playPauseIntentIsCapturedAtTapNotToggledAtFlush() {
        assertTrue(pendingPlayPauseShouldPlay(currentlyPlaying = false))
        assertFalse(pendingPlayPauseShouldPlay(currentlyPlaying = true))
    }

    @Test
    fun restoreLoadFailureDoesNotMarkRestoreComplete() {
        assertFalse(shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = true))
        assertTrue(shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false))
        assertFalse(
            PlaybackSessionPolicy.shouldPersistSavedSession(
                restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = true),
                hasPendingPlay = false,
                queueEmpty = true,
            ),
        )
    }
}

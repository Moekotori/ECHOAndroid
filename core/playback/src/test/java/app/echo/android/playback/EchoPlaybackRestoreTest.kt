package app.echo.android.playback

import org.junit.Assert.assertEquals
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

    @Test
    fun pendingQueueReplaceAfterSessionLoadAbandonsRestore() {
        assertTrue(shouldAbandonSavedSessionRestoreForPendingQueueReplace(listOf(true)))
        assertTrue(shouldAbandonSavedSessionRestoreForPendingQueueReplace(listOf(false, true)))
        assertFalse(shouldAbandonSavedSessionRestoreForPendingQueueReplace(listOf(false)))
        assertFalse(shouldAbandonSavedSessionRestoreForPendingQueueReplace(emptyList()))
    }

    @Test
    fun restoredWebDavPlayIsBlockedUntilCredentialsAreRegistered() {
        assertFalse(
            shouldAllowRestoredPlayWhenReady(
                playWhenReady = true,
                queueRequiresWebDavAuth = true,
                webDavAuthReady = false,
            ),
        )
        assertTrue(
            shouldAllowRestoredPlayWhenReady(
                playWhenReady = true,
                queueRequiresWebDavAuth = true,
                webDavAuthReady = true,
            ),
        )
        assertTrue(
            shouldAllowRestoredPlayWhenReady(
                playWhenReady = true,
                queueRequiresWebDavAuth = false,
                webDavAuthReady = false,
            ),
        )
        assertFalse(
            shouldAllowRestoredPlayWhenReady(
                playWhenReady = false,
                queueRequiresWebDavAuth = true,
                webDavAuthReady = true,
            ),
        )
    }

    @Test
    fun pendingRestorePlayStaysPendingUntilMatchingCredentialsAreReady() {
        assertFalse(
            shouldApplyPendingRestorePlay(
                pendingRestorePlayUntilAuth = true,
                queueRequiresWebDavAuth = true,
                webDavAuthReady = false,
            ),
        )
        assertTrue(
            shouldApplyPendingRestorePlay(
                pendingRestorePlayUntilAuth = true,
                queueRequiresWebDavAuth = true,
                webDavAuthReady = true,
            ),
        )
        assertFalse(
            shouldApplyPendingRestorePlay(
                pendingRestorePlayUntilAuth = false,
                queueRequiresWebDavAuth = true,
                webDavAuthReady = true,
            ),
        )
        assertFalse(
            shouldApplyPendingRestorePlay(
                pendingRestorePlayUntilAuth = true,
                queueRequiresWebDavAuth = false,
                webDavAuthReady = true,
                queueRequiresSubsonicAuth = true,
                subsonicAuthReady = false,
            ),
        )
        assertTrue(
            shouldApplyPendingRestorePlay(
                pendingRestorePlayUntilAuth = true,
                queueRequiresWebDavAuth = false,
                webDavAuthReady = true,
                queueRequiresSubsonicAuth = true,
                subsonicAuthReady = true,
            ),
        )
    }

    @Test
    fun attachDoesNotReplaceReadyRegistryWithEmptyCredentials() {
        assertFalse(
            shouldReplaceRegisteredRemoteCredentials(
                incomingEmpty = true,
                registryAlreadyReady = true,
                allowClearIfEmpty = false,
            ),
        )
        assertTrue(
            shouldReplaceRegisteredRemoteCredentials(
                incomingEmpty = false,
                registryAlreadyReady = true,
                allowClearIfEmpty = false,
            ),
        )
        assertTrue(
            shouldReplaceRegisteredRemoteCredentials(
                incomingEmpty = true,
                registryAlreadyReady = true,
                allowClearIfEmpty = true,
            ),
        )
        assertTrue(
            shouldReplaceRegisteredRemoteCredentials(
                incomingEmpty = true,
                registryAlreadyReady = false,
                allowClearIfEmpty = false,
            ),
        )
    }

    @Test
    fun replayGainUriLookupUsesPlayerQueueWhenSavedSessionRestoreIsSkipped() {
        val emptyControllerMaps = emptyMap<String, String>()
        val playerQueueUris = mapOf(
            "track-1" to "https://dav.example/music/a.flac",
            "track-2" to "https://dav.example/music/b.flac",
        )
        val merged = mergePlayerQueueReplayGainUris(emptyControllerMaps, playerQueueUris)

        assertEquals(
            "https://dav.example/music/a.flac",
            replayGainUriForMediaId("track-1", merged),
        )
        assertEquals(
            "https://dav.example/music/b.flac",
            replayGainUriForMediaId("track-2", merged),
        )
        assertEquals(null, replayGainUriForMediaId("track-1", emptyControllerMaps))
    }
}

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
    fun pendingAuthRestorePlayBlocksSessionPersist() {
        assertTrue(
            PlaybackSessionPolicy.hasBlockingPendingPlay(
                pendingQueueReplace = false,
                pendingAuthRestorePlay = true,
            ),
        )
        assertFalse(
            PlaybackSessionPolicy.hasBlockingPendingPlay(
                pendingQueueReplace = false,
                pendingAuthRestorePlay = false,
            ),
        )
        assertFalse(
            PlaybackSessionPolicy.shouldPersistSavedSession(
                restoreCompleted = true,
                hasPendingPlay = PlaybackSessionPolicy.hasBlockingPendingPlay(
                    pendingQueueReplace = false,
                    pendingAuthRestorePlay = true,
                ),
                queueEmpty = false,
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
    fun playPauseAndTracksChangedDoNotRemapQueue() {
        assertFalse(
            PlaybackSessionPolicy.shouldRemapFullQueue(
                timelineChanged = false,
                isPlayingChanged = true,
                tracksChanged = true,
                playWhenReadyChanged = true,
            ),
        )
    }

    @Test
    fun timelineChangeRemapsQueue() {
        assertTrue(
            PlaybackSessionPolicy.shouldRemapFullQueue(
                timelineChanged = true,
                isPlayingChanged = false,
                tracksChanged = false,
            ),
        )
    }

    @Test
    fun mediaItemTransitionRemapsQueueDuration() {
        assertTrue(
            PlaybackSessionPolicy.shouldRemapFullQueue(
                timelineChanged = false,
                mediaItemTransitioned = true,
            ),
        )
    }

    @Test
    fun unchangedSignatureSkipsSessionPersist() {
        assertTrue(
            PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = false,
                signature = "1|false|a;b;",
                lastSignature = "1|false|a;b;",
                positionBucket = 3L,
                lastPositionBucket = 3L,
            ),
        )
        assertFalse(
            PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = true,
                signature = "1|false|a;b;",
                lastSignature = "1|false|a;b;",
                positionBucket = 3L,
                lastPositionBucket = 3L,
            ),
        )
        assertFalse(
            PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = false,
                signature = "2|false|a;b;",
                lastSignature = "1|false|a;b;",
                positionBucket = 3L,
                lastPositionBucket = 3L,
            ),
        )
    }

    @Test
    fun seekToNewPositionBucketIsNotSkippedAsUnchangedPersist() {
        assertFalse(
            PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = false,
                signature = "1|false|a;b;",
                lastSignature = "1|false|a;b;",
                positionBucket = 4L,
                lastPositionBucket = 3L,
            ),
        )
        assertFalse(
            PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = false,
                signature = "1|false|a;b;",
                lastSignature = "1|false|a;b;",
                positionBucket = 3L,
                lastPositionBucket = 3L,
                persistBecauseOfSeek = true,
            ),
        )
    }

    @Test
    fun cachedQueueSnapshotIsReusedOnlyWhenMediaIdsMatch() {
        val ids = listOf("a", "b", "c")
        assertTrue(PlaybackSessionPolicy.shouldReuseCachedQueueSnapshot(ids, ids))
        assertFalse(
            PlaybackSessionPolicy.shouldReuseCachedQueueSnapshot(
                cachedMediaIds = listOf("a", "b", "c"),
                playerMediaIds = listOf("x", "y", "z"),
            ),
        )
        assertFalse(PlaybackSessionPolicy.shouldReuseCachedQueueSnapshot(emptyList(), ids))
    }

    @Test
    fun nullSessionIsNotWrittenWhilePlayerStillHasItems() {
        assertFalse(PlaybackSessionPolicy.shouldPersistNullSavedSession(mediaItemCount = 4))
        assertTrue(PlaybackSessionPolicy.shouldPersistNullSavedSession(mediaItemCount = 0))
        assertFalse(
            PlaybackSessionPolicy.shouldPersistSavedSession(
                restoreCompleted = true,
                hasPendingPlay = false,
                queueEmpty = true,
            ) && PlaybackSessionPolicy.shouldPersistNullSavedSession(mediaItemCount = 3),
        )
        assertTrue(
            PlaybackSessionPolicy.shouldPersistSavedSession(
                restoreCompleted = true,
                hasPendingPlay = false,
                queueEmpty = true,
            ) && PlaybackSessionPolicy.shouldPersistNullSavedSession(mediaItemCount = 0),
        )
    }

    @Test
    fun driverTestDoesNotClaimWhilePlaying() {
        assertFalse(PlaybackSessionPolicy.shouldClaimUsbInterfaceForDriverTest(isPlayingToUsb = true))
        assertTrue(PlaybackSessionPolicy.shouldClaimUsbInterfaceForDriverTest(isPlayingToUsb = false))
    }

    @Test
    fun playAllClearsLeftoverRepeatOneAndShuffle() {
        assertFalse(
            PlaybackSessionPolicy.shuffleEnabledForQueueReplace(PlaybackQueueReplaceIntent.PlayAll),
        )
        assertEquals(
            app.echo.android.model.playback.EchoRepeatMode.Off,
            PlaybackSessionPolicy.repeatModeForQueueReplace(PlaybackQueueReplaceIntent.PlayAll),
        )
        assertEquals(
            app.echo.android.model.playback.EchoRepeatMode.Off,
            PlaybackSessionPolicy.repeatModeForQueueReplace(PlaybackQueueReplaceIntent.Shuffle),
        )
        assertTrue(
            PlaybackSessionPolicy.shuffleEnabledForQueueReplace(PlaybackQueueReplaceIntent.Shuffle),
        )
    }

    @Test
    fun skipDoesNotForcePlayAndPreparesOnlyWhenIdleOrErrored() {
        assertFalse(PlaybackSessionPolicy.skipShouldCallPlay())
        assertTrue(PlaybackSessionPolicy.shouldPrepareBeforePlay(hasPlayerError = true, playbackStateIdle = false))
        assertTrue(PlaybackSessionPolicy.shouldPrepareBeforePlay(hasPlayerError = false, playbackStateIdle = true))
        assertFalse(PlaybackSessionPolicy.shouldPrepareBeforePlay(hasPlayerError = false, playbackStateIdle = false))
        assertTrue(
            PlaybackSessionPolicy.shouldPrepareAfterExternalSkip(
                hasPlayerError = true,
                playbackStateIdle = false,
                mediaItemCount = 3,
            ),
        )
        assertFalse(
            PlaybackSessionPolicy.shouldPrepareAfterExternalSkip(
                hasPlayerError = true,
                playbackStateIdle = false,
                mediaItemCount = 0,
            ),
        )
        assertEquals(2, PlaybackSessionPolicy.queueStartIndex(listOf("a", "b", "c"), "c"))
        assertEquals(0, PlaybackSessionPolicy.queueStartIndex(listOf("a", "b"), "missing"))
    }

    @Test
    fun tickerPositionIsCopiedOntoPlaybackStatus() {
        val status = app.echo.android.model.playback.EchoPlaybackStatus(positionMs = 0L, durationMs = 10_000L)
        val live = app.echo.android.model.playback.PlaybackPositionState(
            positionMs = 12_500L,
            durationMs = 180_000L,
        )
        val merged = PlaybackSessionPolicy.playbackStatusWithLivePosition(status, live)
        assertEquals(12_500L, merged.positionMs)
        assertEquals(180_000L, merged.durationMs)
        assertEquals(
            12_500L,
            PlaybackSessionPolicy.playbackStatusWithLivePosition(
                status.copy(positionMs = 3_000L),
                live.copy(durationMs = 0L),
            ).positionMs,
        )
    }
}

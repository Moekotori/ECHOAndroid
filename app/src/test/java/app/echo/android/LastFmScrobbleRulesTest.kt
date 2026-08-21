package app.echo.android

import app.echo.android.model.playback.EchoPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmScrobbleRulesTest {
    @Test
    fun unknownDurationScrobblesAfterFourMinutesOfListening() {
        assertFalse(LastFmScrobbleRules.shouldScrobble(durationMs = 0L, listenedMs = 239_000L))
        assertTrue(LastFmScrobbleRules.shouldScrobble(durationMs = 0L, listenedMs = 240_000L))
    }

    @Test
    fun shortTracksNeverScrobble() {
        assertFalse(LastFmScrobbleRules.shouldScrobble(durationMs = 20_000L, listenedMs = 20_000L))
    }

    @Test
    fun usesListeningTimeNotSeekPosition() {
        assertFalse(LastFmScrobbleRules.shouldScrobble(durationMs = 200_000L, listenedMs = 5_000L))
        assertTrue(LastFmScrobbleRules.shouldScrobble(durationMs = 200_000L, listenedMs = 100_000L))
    }

    @Test
    fun pausedGapsAreNotCountedAsListening() {
        val afterPlay = LastFmScrobbleRules.accumulatedPlayMs(
            previouslyAccumulatedMs = 10_000L,
            wasPlaying = true,
            lastTickEpochMs = 1_000L,
            nowEpochMs = 6_000L,
        )
        val afterPause = LastFmScrobbleRules.accumulatedPlayMs(
            previouslyAccumulatedMs = afterPlay,
            wasPlaying = false,
            lastTickEpochMs = 6_000L,
            nowEpochMs = 60_000L,
        )
        assertEquals(15_000L, afterPlay)
        assertEquals(15_000L, afterPause)
    }

    @Test
    fun failedSubmitDoesNotKeepSubmittedFlag() {
        assertFalse(LastFmScrobbleRules.keepSubmittedFlag(attemptSucceeded = false))
        assertTrue(LastFmScrobbleRules.keepSubmittedFlag(attemptSucceeded = true))
    }

    @Test
    fun failedSubmitRetriesAfterBackoffNotEveryTick() {
        assertTrue(
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = false,
                lastAttemptEpochMs = 0L,
                nowEpochMs = 1_000L,
            ),
        )
        assertFalse(
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = false,
                lastAttemptEpochMs = 1_000L,
                nowEpochMs = 2_000L,
            ),
        )
        assertTrue(
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = false,
                lastAttemptEpochMs = 1_000L,
                nowEpochMs = 31_000L,
            ),
        )
        assertFalse(
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = true,
                lastAttemptEpochMs = 1_000L,
                nowEpochMs = 31_000L,
            ),
        )
    }

    @Test
    fun missingTrackDoesNotClearActiveScrobbleWhenCredentialsReady() {
        assertFalse(LastFmScrobbleRules.shouldClearActiveScrobble(credentialsReady = true))
        assertTrue(LastFmScrobbleRules.shouldClearActiveScrobble(credentialsReady = false))
    }

    @Test
    fun flushOnTrackChangeUsesListeningThreshold() {
        assertTrue(
            LastFmScrobbleRules.shouldFlushScrobbleBeforeReplacing(
                alreadyScrobbled = false,
                durationMs = 180_000L,
                listenedMs = 90_000L,
            ),
        )
        assertFalse(
            LastFmScrobbleRules.shouldFlushScrobbleBeforeReplacing(
                alreadyScrobbled = true,
                durationMs = 180_000L,
                listenedMs = 90_000L,
            ),
        )
        assertFalse(
            LastFmScrobbleRules.shouldFlushScrobbleBeforeReplacing(
                alreadyScrobbled = false,
                durationMs = 180_000L,
                listenedMs = 10_000L,
            ),
        )
    }

    @Test
    fun repeatOneRestartsListenAfterPositionWrap() {
        assertTrue(
            LastFmScrobbleRules.shouldStartNewListenAfterRepeat(
                alreadyScrobbled = true,
                previousPositionMs = 95_000L,
                currentPositionMs = 400L,
            ),
        )
        assertFalse(
            LastFmScrobbleRules.shouldStartNewListenAfterRepeat(
                alreadyScrobbled = false,
                previousPositionMs = 95_000L,
                currentPositionMs = 400L,
            ),
        )
        assertFalse(
            LastFmScrobbleRules.shouldStartNewListenAfterRepeat(
                alreadyScrobbled = true,
                previousPositionMs = 1_000L,
                currentPositionMs = 2_000L,
            ),
        )
    }

    @Test
    fun terminalPlaybackStateClearsMissingTrackScrobble() {
        assertTrue(
            LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(EchoPlaybackState.Idle),
        )
        assertTrue(
            LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(EchoPlaybackState.Stopped),
        )
        assertFalse(
            LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(EchoPlaybackState.Loading),
        )
    }
}

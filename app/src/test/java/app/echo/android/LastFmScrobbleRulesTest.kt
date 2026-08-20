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
    fun missingTrackDoesNotClearActiveScrobbleWhenCredentialsReady() {
        assertFalse(LastFmScrobbleRules.shouldClearActiveScrobble(credentialsReady = true))
        assertTrue(LastFmScrobbleRules.shouldClearActiveScrobble(credentialsReady = false))
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

package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoTrackTransitionOptionsTest {
    @Test
    fun fadeDurationRoundsToHundredMillisecondsInsideTheAllowedRange() {
        assertEquals(500, roundFadeDurationMs(500))
        assertEquals(1_500, roundFadeDurationMs(1_499))
        assertEquals(1_500, roundFadeDurationMs(1_500))
        assertEquals(5_000, roundFadeDurationMs(9_999))
        assertEquals(1_500, EchoTrackTransitionOptions(fadeDurationMs = 1_450).normalized().fadeDurationMs)
    }

    @Test
    fun preampIsClampedToTheUiRange() {
        assertEquals(EchoReplayGainPreampMinDb, normalizeReplayGainPreampDb(-40f))
        assertEquals(EchoReplayGainPreampMaxDb, normalizeReplayGainPreampDb(12f))
        assertEquals(3f, normalizeReplayGainPreampDb(3f))
    }
}

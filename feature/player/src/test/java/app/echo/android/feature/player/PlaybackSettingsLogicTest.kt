package app.echo.android.feature.player

import app.echo.android.model.playback.EchoReplayGainPreampMaxDb
import app.echo.android.model.playback.EchoReplayGainPreampMinDb
import app.echo.android.model.playback.EchoSleepTimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSettingsLogicTest {
    @Test
    fun nightcoreRequiresMatchingPitchAboveOne() {
        assertFalse(isNightcorePlayback(1f, 1f))
        assertFalse(isNightcorePlayback(0.75f, 0.75f))
        assertFalse(isNightcorePlayback(1.5f, 1f))
        assertTrue(isNightcorePlayback(1.25f, 1.25f))
        assertTrue(isNightcorePlayback(2f, 2f))
    }

    @Test
    fun nightcoreHidesSpeedsBelowMinimum() {
        assertEquals(PlaybackSpeedOptions, playbackSpeedChoices(false))
        assertEquals(listOf(1.25f, 1.5f, 2f), playbackSpeedChoices(true))
    }

    @Test
    fun enablingNightcoreRaisesSpeedToMinimum() {
        assertEquals(1.25f, playbackSpeedForNightcoreToggle(0.75f, true))
        assertEquals(1.5f, playbackSpeedForNightcoreToggle(1.5f, true))
        assertEquals(0.75f, playbackSpeedForNightcoreToggle(0.75f, false))
    }

    @Test
    fun customSleepTimerIsAnyTimedValueOutsidePresets() {
        assertFalse(isCustomSleepTimer(EchoSleepTimerMode.Off, 45))
        assertFalse(isCustomSleepTimer(EchoSleepTimerMode.EndOfTrack, 45))
        assertFalse(isCustomSleepTimer(EchoSleepTimerMode.Timed, 15))
        assertFalse(isCustomSleepTimer(EchoSleepTimerMode.Timed, 30))
        assertFalse(isCustomSleepTimer(EchoSleepTimerMode.Timed, 60))
        assertTrue(isCustomSleepTimer(EchoSleepTimerMode.Timed, 45))
        assertTrue(isCustomSleepTimer(EchoSleepTimerMode.Timed, 180))
        assertFalse(isCustomSleepTimer(EchoSleepTimerMode.Timed, null))
    }

    @Test
    fun sleepTimerMinutesStayInRange() {
        assertEquals(1, coerceSleepTimerMinutes(0))
        assertEquals(180, coerceSleepTimerMinutes(240))
        assertEquals(40, stepSleepTimerMinutes(45, -SleepTimerCustomStepMinutes))
        assertEquals(1, stepSleepTimerMinutes(3, -SleepTimerCustomStepMinutes))
        assertEquals(180, stepSleepTimerMinutes(178, SleepTimerCustomStepMinutes))
    }

    @Test
    fun replayGainPreampStepsStopAtBounds() {
        assertFalse(canLowerReplayGainPreamp(EchoReplayGainPreampMinDb))
        assertTrue(canLowerReplayGainPreamp(0f))
        assertFalse(canRaiseReplayGainPreamp(EchoReplayGainPreampMaxDb))
        assertTrue(canRaiseReplayGainPreamp(0f))
    }

    @Test
    fun speedAndGainLabelsUseCompactUnits() {
        assertEquals("1x", formatPlaybackSpeedLabel(1f))
        assertEquals("2x", formatPlaybackSpeedLabel(2f))
        assertEquals("0dB", formatReplayGainDb(0f))
        assertEquals("+3dB", formatReplayGainDb(3f))
        assertEquals("-3dB", formatReplayGainDb(-3f))
    }

    @Test
    fun sleepTimerRemainingRoundsUpToMinutes() {
        assertEquals("1m", formatSleepTimerRemaining(1L))
        assertEquals("1m", formatSleepTimerRemaining(60_000L))
        assertEquals("1h 0m", formatSleepTimerRemaining(60 * 60 * 1000L))
        assertEquals("1h 30m", formatSleepTimerRemaining(90 * 60 * 1000L))
    }
}

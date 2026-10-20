package app.echo.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMotionTest {
    @Test fun calmKeepsTheSameSizeAndBaselineAcrossFocusChanges() {
        val calm = lyricsMotionProfile("calm")
        for (distance in 0..4) assertEquals(calm.scale(true, 0), calm.scale(false, distance), 0f)
        assertEquals(0f, calm.liftDp, 0f)
    }

    @Test fun stageHasAVisibleFocusChangeWithoutEnlargingTheReservedTextBounds() {
        val smooth = lyricsMotionProfile("smooth")
        val stage = lyricsMotionProfile("stage")
        assertTrue(smooth.scale(false, 1) - stage.scale(false, 1) >= 0.07f)
        assertTrue(stage.liftDp - smooth.liftDp >= 8f)
        for (mode in listOf("calm", "smooth", "stage")) {
            val profile = lyricsMotionProfile(mode)
            assertEquals(1f, profile.scale(true, 0), 0f)
            for (distance in 0..4) assertTrue(profile.scale(false, distance) in 0.7f..1f)
        }
    }

    @Test fun unknownSavedModeUsesTheSmoothDefault() {
        assertEquals(lyricsMotionProfile("smooth"), lyricsMotionProfile("unknown"))
    }
}

package app.echo.android.model.playback

import org.junit.Assert.*
import org.junit.Test

class EchoVolumeSettingsTest {
    @Test fun volumePreampAndReplayGainShareTheCeiling() {
        val volume = EchoVolumeSettings(levelDb = -6f, gainDb = 12f, limitDb = -2f)
        assertEquals(-2f, volume.combinedGainDb(3f), 0f)
        assertEquals(-6f, volume.combinedGainDb(-12f), 0f)
        assertEquals(30f, EchoVolumeSettings().combinedGainDb(30f), 0f)
        assertFalse(EchoVolumeSettings().changesSamples)
        assertTrue(EchoVolumeSettings(muted = true).changesSamples)
    }

    @Test fun invalidSettingsAreBoundedAndFineAdjustmentPreservesMute() {
        val volume = EchoVolumeSettings(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 9f).normalized
        assertEquals(EchoVolumeSettings(), volume)
        assertEquals(-60f, EchoVolumeSettings(levelDb = -99f).normalized.levelDb, 0f)
        assertEquals(12f, EchoVolumeSettings(gainDb = 99f).normalized.gainDb, 0f)
        val muted = EchoVolumeSettings(levelDb = -0.1f, stepDb = 0.5f, muted = true)
        assertEquals(0f, muted.adjusted(1).levelDb, 0f)
        assertTrue(muted.adjusted(1).muted)
        assertEquals(-60f, EchoVolumeSettings(levelDb = -60f).adjusted(-1).levelDb, 0f)
    }
}

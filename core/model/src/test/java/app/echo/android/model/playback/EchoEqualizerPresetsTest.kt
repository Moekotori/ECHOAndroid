package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoEqualizerPresetsTest {
    @Test
    fun everyPresetHasFiveInRangeBands() {
        EchoEqualizerPresets.presets.forEach { preset ->
            assertEquals(preset.id, EchoEqualizerPresets.defaultFrequenciesHz.size, preset.gainsDb.size)
            preset.gainsDb.forEach { gain ->
                assertTrue(preset.id, gain in -12f..12f)
            }
        }
    }

    @Test
    fun harmanLiftsBassAndTamesAir() {
        val gains = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Harman)
        assertEquals(5, gains.size)
        assertTrue(gains[0] > 4f)
        assertTrue(gains[0] > gains[1])
        assertTrue(gains[3] > 1f)
        assertTrue(gains[4] < 0f)
    }

    @Test
    fun normalizeKeepsKnownPresetsAndCustom() {
        assertEquals(EchoEqualizerPreset.Harman, EchoEqualizerPresets.normalizePresetId("harman"))
        assertEquals(EchoEqualizerPreset.Night, EchoEqualizerPresets.normalizePresetId("night"))
        assertEquals(EchoEqualizerPreset.Custom, EchoEqualizerPresets.normalizePresetId("custom"))
        assertEquals(EchoEqualizerPreset.Flat, EchoEqualizerPresets.normalizePresetId("nope"))
        assertEquals(EchoEqualizerPreset.Flat, EchoEqualizerPresets.normalizePresetId(null))
    }
}

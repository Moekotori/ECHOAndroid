package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoEqualizerPresetsTest {
    @Test
    fun everyPresetHasThirtyOneInRangeBands() {
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
        assertEquals(31, gains.size)
        assertEquals(1.5f, gains[5], 0f)
        assertTrue(gains[5] > gains[14])
        assertEquals(-0.2f, gains[26], 0f)
    }

    @Test
    fun normalizeKeepsKnownPresetsAndCustom() {
        assertEquals(EchoEqualizerPreset.Harman, EchoEqualizerPresets.normalizePresetId("harman"))
        assertEquals(EchoEqualizerPreset.Night, EchoEqualizerPresets.normalizePresetId("night"))
        assertEquals(EchoEqualizerPreset.Custom, EchoEqualizerPresets.normalizePresetId("custom"))
        assertEquals(EchoEqualizerPreset.Flat, EchoEqualizerPresets.normalizePresetId("nope"))
        assertEquals(EchoEqualizerPreset.Flat, EchoEqualizerPresets.normalizePresetId(null))
    }

    @Test
    fun androidPresetsHaveDistinctIdsAndSurviveLookup() {
        val presets = EchoEqualizerPresets.presets
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        EchoAndroidEqPresets.definitions.forEach { preset ->
            assertEquals(preset.id, EchoEqualizerPresets.normalizePresetId(preset.id))
            assertEquals(preset.gainsDb, EchoEqualizerPresets.gainsForPreset(preset.id))
            assertEquals(preset.preampDb, EchoEqualizerPresets.preampFor(preset.id), 0f)
        }
    }
}

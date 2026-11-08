package app.echo.android.model.playback

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class EchoPcEqParityTest {
    @Test fun catalogueHasTheExactPcCentersAndAll35Presets() {
        assertEquals(31, EchoEqualizerPresets.preciseFrequenciesHz.size)
        assertEquals(31.5f, EchoEqualizerPresets.preciseFrequenciesHz[2], 0f)
        assertEquals(35, EchoEqualizerPresets.presets.size)
        assertTrue(EchoEqualizerPresets.presets.all { it.gainsDb.size == 31 })
        assertEquals(4.318f, EchoEqualizerPresets.GraphicQ, 0f)
        assertEquals(-2.3f, EchoEqualizerPresets.preampFor("harman-target"), 0f)
        val named = requireNotNull(EchoEqualizerUserPresets.normalize(EchoEqualizerUserPreset("bass","Bass",graphicPresetId = EchoEqualizerPreset.Bass)))
        assertEquals(EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Bass), named.gainsDb)
    }
    @Test fun oldFiveBandShareCodeKeepsItsOriginalFiveFilters() {
        val payload = "1|0|Legacy|custom|1,2,3,4,5|-3|"
        val code = "ECHOEQ1." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + "."
        val preset = requireNotNull(EchoEqualizerShareCodec.decode(code, "old"))
        assertTrue(preset.parametric)
        assertEquals(listOf(60f,230f,910f,3600f,14000f), preset.filters.map { it.frequencyHz })
        assertEquals(listOf(1f,2f,3f,4f,5f), preset.filters.map { it.gainDb })
        assertTrue(preset.filters.all { it.q == 1f })
    }
    @Test fun pcJsonAndSharePreserveAllAudibleOptions() {
        val band = OpraEqBand("peak_dip", 2500f, -3f, 2f, null, enabled = true,
            scope = EchoEqBandScope.Side, dynamicMode = EchoEqDynamicMode.Down,
            dynamicThresholdDb = -30f, dynamicRatio = 3f, dynamicAttackMs = 2f, dynamicReleaseMs = 90f)
        val preset = EchoEqualizerUserPreset("test", "PC", parametric = true, filters = listOf(band, band.copy(enabled = false)),
            options = EchoEqOptions(70f, 2f), preampDb = -3f)
        val json = EchoEqPcJsonCodec.decode(EchoEqPcJsonCodec.encode(listOf(preset))).single()
        assertEquals(preset.filters, json.filters); assertEquals(preset.options, json.options)
        val code = requireNotNull(EchoEqualizerShareCodec.encode(preset))
        val shared = requireNotNull(EchoEqualizerShareCodec.decode(code, "new"))
        assertEquals(preset.filters, shared.filters); assertEquals(preset.options, shared.options)
        assertNull(EchoParametricEq.sanitize(List(5) { band }))
        assertFalse(band.copy(enabled = false).affectsFrequencyResponse())
    }
}

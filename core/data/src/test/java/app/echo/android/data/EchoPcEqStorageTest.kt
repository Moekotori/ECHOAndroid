package app.echo.android.data

import app.echo.android.model.playback.*
import app.echo.android.model.backup.*
import org.junit.Assert.*
import org.junit.Test

class EchoPcEqStorageTest {
    @Test fun independentCorrectionRoundTripsExtendedParameters() {
        val value = EchoHeadphoneCorrectionSettings(true,"id","Headphones",-35f,
            listOf(OpraEqBand("peak_dip",12f,22f,200f,null)))
        assertEquals(value,EchoHeadphoneCorrectionCodec.decode(EchoHeadphoneCorrectionCodec.encode(value)))
    }
    @Test fun legacyReadPreservesTheFiveBandTopologyAndLeaves31BandDataAlone() {
        val old = LegacyGraphicEq.filters("custom", "1,2,3,4,5", false)
        assertEquals(5, old.size); assertEquals(14000f, old.last().frequencyHz, 0f)
        assertTrue(LegacyGraphicEq.filters("flat", List(31) { "0" }.joinToString(","), false).isEmpty())
        assertTrue(LegacyGraphicEq.filters("custom", "1,2,3,4,5", true).isEmpty())
    }
    @Test fun settingsPresetsAndMigrationKeepScopeDynamicsAndStrength() {
        val band = OpraEqBand("peak_dip", 1000f, 0f, 1f, null, scope = EchoEqBandScope.Mid, dynamicMode = EchoEqDynamicMode.Up)
        assertEquals(listOf(band), parseEqualizerFilters(formatEqualizerFilters(listOf(band))))
        val preset = EchoEqualizerUserPreset("p", "Dynamics", parametric = true, filters = listOf(band), options = EchoEqOptions(60f, -2f))
        assertEquals(preset, EchoEqualizerUserPresetCodec.decode(EchoEqualizerUserPresetCodec.encode(listOf(preset))).single())
        val document = EchoBackupDocument(settings = EchoBackupSettings(equalizerOptions = preset.options, equalizerFilters = preset.filters))
        val decoded = EchoBackupCodec.decode(EchoBackupCodec.encode(document))
        assertEquals(preset.options, decoded.settings.equalizerOptions)
        assertEquals(preset.filters, decoded.settings.equalizerFilters)
    }
}

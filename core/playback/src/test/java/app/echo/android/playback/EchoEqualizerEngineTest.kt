package app.echo.android.playback

import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.OpraEqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EchoEqualizerEngineTest {
    @Test
    fun peakingFilterMatchesGainAtCenterFrequency() {
        val band = OpraEqBand(
            type = EchoEqFilterType.PeakDip,
            frequencyHz = 1_000f,
            gainDb = 6f,
            q = 1.2f,
            slope = null,
        )
        val atCenter = EchoBiquadMath.sampleCurveDb(listOf(band), 1_000f)
        val farAway = EchoBiquadMath.sampleCurveDb(listOf(band), 40f)
        assertEquals(6f, atCenter, 0.15f)
        assertTrue(abs(farAway) < 0.4f)
    }

    @Test
    fun graphicFiltersDropNearZeroBands() {
        val filters = EchoEqualizerEngine.graphicFilters(
            EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Bass),
        )
        assertTrue(filters.isNotEmpty())
        assertTrue(filters.all { abs(it.gainDb) >= EchoEqualizerEngine.GainEpsilonDb })
        assertTrue(filters.all { it.type == EchoEqFilterType.PeakDip })
        assertFalse(
            EchoEqualizerEngine.graphicFilters(
                EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Flat),
            ).any(),
        )
    }

    @Test
    fun visualizationOmitsPreampSoSlidersShowTheCurve() {
        val filters = listOf(
            OpraEqBand(EchoEqFilterType.PeakDip, 60f, 4f, 1f, null),
            OpraEqBand(EchoEqFilterType.PeakDip, 3_600f, -3f, 1.4f, null),
        )
        val gains = EchoEqualizerEngine.visualizationGainsDb(filters)
        assertEquals(5, gains.size)
        assertTrue(gains.first() > 2f)
        assertTrue(gains[3] < -1f)
        val withPreamp = EchoBiquadMath.sampleCurveDb(filters, 60f, preampDb = -8f)
        assertTrue(withPreamp < gains.first() - 6f)
    }

    @Test
    fun parametricProcessingKeepsNarrowOpraFiltersInsteadOfFiveBandMapping() {
        val opra = listOf(
            OpraEqBand(EchoEqFilterType.LowShelf, 105f, -1.4f, 0.7f, null),
            OpraEqBand(EchoEqFilterType.PeakDip, 1_400f, -1.3f, 2.18f, null),
            OpraEqBand(EchoEqFilterType.PeakDip, 5_275f, 3.1f, 3.96f, null),
            OpraEqBand(EchoEqFilterType.PeakDip, 7_619f, 3.3f, 4.48f, null),
            OpraEqBand(EchoEqFilterType.HighShelf, 10_000f, 2.2f, 0.7f, null),
        )
        val visualized = EchoEqualizerEngine.visualizationGainsDb(opra)
        val mapped = EchoEqualizerEngine.graphicFilters(visualized)
        val exactAtPeak = EchoBiquadMath.sampleCurveDb(opra, 7_619f)
        val mappedAtPeak = EchoBiquadMath.sampleCurveDb(mapped, 7_619f)
        assertTrue(abs(exactAtPeak - mappedAtPeak) > 0.75f)
        val processing = EchoEqualizerEngine.processingFilters(
            parametric = true,
            filters = opra,
            gainsDb = visualized,
        )
        assertEquals(opra, processing)
        assertTrue(EchoEqualizerEngine.shouldProcess(true, -4f, opra))
        assertFalse(EchoEqualizerEngine.shouldProcess(false, -4f, opra))
    }

    @Test
    fun unknownFilterTypesAreIgnoredInsteadOfBreakingTheCurve() {
        val unknown = OpraEqBand("mystery", 1_000f, 12f, 1f, null)
        val known = OpraEqBand(EchoEqFilterType.PeakDip, 1_000f, 3f, 1f, null)
        assertEquals(0f, EchoBiquadMath.sampleCurveDb(listOf(unknown), 1_000f), 0.01f)
        assertEquals(3f, EchoBiquadMath.sampleCurveDb(listOf(unknown, known), 1_000f), 0.15f)
    }
}

package app.echo.android.playback

import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test

class EchoPeqEditorTest {
    @Test fun manualFiltersRetainDisabledStateAndRestoreWithHeadroom() {
        val c = EchoEqualizerController()
        val filters = listOf(OpraEqBand("low_shelf", 100f, 6f, 0.707f, null), OpraEqBand("peak_dip", 3000f, -2f, 2f, null))
        c.setParametricFilters(filters)
        val edited = c.state.value
        assertFalse(edited.enabled)
        assertTrue(edited.parametric)
        assertEquals(filters, edited.filters)
        assertTrue(edited.preampDb < -6f)
        val restored = EchoEqualizerController()
        restored.setConfig(edited.enabled, edited.presetId, edited.gainsDb, edited.preampDb, edited.filters, edited.sourceLabel)
        assertEquals(edited.filters, restored.state.value.filters)
        assertEquals(edited.responseCurve, restored.state.value.responseCurve)
    }

    @Test fun rejectsInvalidOrUnboundedFiltersAndClampsFiniteValues() {
        val c = EchoEqualizerController()
        val band = OpraEqBand("peak_dip", 1000f, 2f, 1f, null)
        c.setParametricFilters(listOf(band))
        val valid = c.state.value
        c.setParametricFilters(List(13) { band })
        assertEquals(valid, c.state.value)
        c.setParametricFilters(listOf(band.copy(gainDb = Float.NaN)))
        assertEquals(valid, c.state.value)
        c.setParametricFilters(listOf(band.copy(type = "unsupported")))
        assertEquals(valid, c.state.value)
        c.setParametricFilters(listOf(band.copy(frequencyHz = 50000f, gainDb = 25f, q = 30f)))
        assertEquals(band.copy(frequencyHz = 20000f, gainDb = 12f, q = 10f), c.state.value.filters.single())
        c.setPreset(EchoEqualizerPreset.Flat)
        assertFalse(c.state.value.parametric)
        assertTrue(c.state.value.filters.isEmpty())
    }
}

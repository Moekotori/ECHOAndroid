package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.OpraEqBand
import org.junit.Assert.*
import org.junit.Test

class EqPresetEditsTest {
    @Test fun graphicEditsIgnoreIdentityButIncludePreampAndCanReturnToSavedValues() {
        val saved = EchoEqualizerUserPreset("saved", "Warm", gainsDb = listOf(1f, 2f), preampDb = -2f)
        val current = saved.copy(id = "current", name = "Custom", graphicPresetId = "custom", updatedAtEpochMs = 30)
        assertFalse(saved.hasEdits(current))
        assertFalse(saved.hasEdits(current.copy(preampDb = -2.0001f)))
        assertTrue(saved.hasEdits(current.copy(preampDb = -1f)))
        assertTrue(saved.hasEdits(current.copy(gainsDb = listOf(1f, 3f))))
        assertTrue(saved.hasEdits(current.copy(gainsDb = emptyList())))
        assertFalse(saved.hasEdits(current.copy(gainsDb = saved.gainsDb)))
    }

    @Test fun parametricEditsCompareFilterParametersAndModeNotUnusedGraphicBands() {
        val band = OpraEqBand("peak", 1000f, 2f, 1f, null)
        val saved = EchoEqualizerUserPreset("saved", "PEQ", parametric = true, filters = listOf(band))
        assertFalse(saved.hasEdits(saved.copy(filters = listOf(band.copy(type = "peak_dip")), gainsDb = listOf(8f))))
        assertTrue(saved.hasEdits(saved.copy(filters = listOf(band.copy(q = 2f)))))
        assertTrue(saved.hasEdits(saved.copy(filters = listOf(band.copy(frequencyHz = 900f)))))
        assertTrue(saved.hasEdits(saved.copy(filters = listOf(band.copy(slope = 12f)))))
        assertTrue(saved.hasEdits(saved.copy(parametric = false)))
    }
}

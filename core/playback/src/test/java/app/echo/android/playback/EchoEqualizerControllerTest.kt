package app.echo.android.playback

import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.OpraEqBand
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class EchoEqualizerControllerTest {
    @Test fun parametricResetKeepsPeqModeAndMsebWithoutLeavingPassFiltersActive() {
        val controller = EchoEqualizerController()
        controller.setConfig(true, EchoEqualizerPreset.Custom, emptyList(), -6f,
            listOf(OpraEqBand("high_pass", 120f, 0f, null, 24f)), "Correction")
        val mseb = app.echo.android.model.playback.EchoMsebState(true)
            .withValue(app.echo.android.model.playback.EchoMsebParameter.Air, 30f)
        controller.setMseb(mseb)
        controller.reset()
        val state = controller.state.value
        assertTrue(state.parametric)
        assertTrue(state.enabled)
        assertEquals(EchoEqualizerPreset.Custom, state.presetId)
        assertEquals(1, state.filters.size)
        assertEquals("peak_dip", state.filters.single().type)
        assertEquals(0f, state.preampDb, 0f)
        assertFalse(state.active)
        assertNull(state.sourceLabel)
        assertEquals(mseb, state.mseb)
        assertTrue(controller.processingRuntime().shouldProcess)
        val restored = EchoEqualizerController()
        restored.setConfig(state.enabled, state.presetId, state.gainsDb, state.preampDb, state.filters, mseb = state.mseb)
        assertEquals(controller.processingRuntime(), restored.processingRuntime())
        controller.setEnabled(false)
        controller.reset()
        assertFalse(controller.state.value.enabled)
        assertTrue(controller.state.value.parametric)
    }

    @Test fun graphicResetStillUsesFlatGraphicMode() {
        val controller = EchoEqualizerController()
        controller.setConfig(true, EchoEqualizerPreset.Bass, emptyList(), -6f)
        controller.reset()
        assertFalse(controller.state.value.parametric)
        assertEquals(EchoEqualizerPreset.Flat, controller.state.value.presetId)
        assertFalse(controller.processingRuntime().shouldProcess)
    }

    @Test fun graphicPreampSurvivesBandChangesAndRestoration() {
        val controller = EchoEqualizerController()
        controller.setConfig(true, EchoEqualizerPreset.Bass, listOf(4f, 2f, 0f, 0f, 0f), -6f)
        controller.setBandGain(0, 3f)
        assertEquals(-6f, controller.state.value.preampDb, 0.001f)
        val restored = EchoEqualizerController()
        restored.setConfig(true, controller.state.value.presetId, controller.state.value.gainsDb, -6f)
        assertEquals(controller.state.value.responseCurve, restored.state.value.responseCurve)
    }

    @Test fun graphicEventCannotAccidentallyDiscardOpraFilters() {
        val controller = EchoEqualizerController()
        val filters = listOf(OpraEqBand("peak_dip", 1500f, -3f, 2.1f, null))
        controller.setConfig(true, EchoEqualizerPreset.Custom, emptyList(), -5f, filters, "Test correction")
        controller.setBandGain(0, 8f)
        assertEquals(filters, controller.state.value.filters)
        assertTrue(controller.state.value.parametric)
        controller.setPreamp(-7f)
        assertEquals(filters, controller.state.value.filters)
        assertEquals(-7f, controller.state.value.preampDb, 0.001f)
    }

    @Test fun userPresetKeepsMoreThanTwelveParametricBands() {
        val controller = EchoEqualizerController()
        val filters = (1..13).map { index ->
            OpraEqBand("peak_dip", 80f * index, if (index == 1) 4f else -1f, 1.1f, null)
        }
        assertTrue(
            controller.applyUserPreset(
                EchoEqualizerUserPreset(
                    id = "wide",
                    name = "OPRA",
                    parametric = true,
                    preampDb = -6f,
                    filters = filters,
                    sourceLabel = "Wide correction",
                    opraEqId = "eq-wide",
                ),
            ),
        )
        assertEquals(13, controller.state.value.filters.size)
        assertEquals(-6f, controller.state.value.preampDb, 0.001f)
        assertTrue(controller.state.value.enabled)
        assertEquals("Wide correction", controller.state.value.sourceLabel)
    }
}

package app.echo.android.playback

import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.OpraEqBand
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class EchoEqualizerControllerTest {
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

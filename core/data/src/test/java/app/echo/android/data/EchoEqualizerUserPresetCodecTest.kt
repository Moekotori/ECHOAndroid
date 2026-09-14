package app.echo.android.data

import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.OpraEqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoEqualizerUserPresetCodecTest {
    @Test
    fun roundTripsGraphicAndParametricPresets() {
        val graphic = EchoEqualizerUserPreset(
            id = "g1",
            name = "Evening",
            updatedAtEpochMs = 10L,
            parametric = false,
            graphicPresetId = EchoEqualizerPreset.Bass,
            gainsDb = listOf(4f, 2.8f, 0.4f, -0.8f, -1.2f),
            preampDb = -2.5f,
        )
        val parametric = EchoEqualizerUserPreset(
            id = "p1",
            name = "HD 650",
            updatedAtEpochMs = 11L,
            parametric = true,
            graphicPresetId = EchoEqualizerPreset.Custom,
            preampDb = -6.1f,
            filters = listOf(
                OpraEqBand(EchoEqFilterType.LowShelf, 105f, -1.4f, 0.7f, null),
                OpraEqBand(EchoEqFilterType.PeakDip, 7_619f, 3.3f, 4.48f, null),
            ),
            sourceLabel = "Sennheiser / HD 650 / oratory1990",
            opraEqId = "eq-1",
        )
        val restored = EchoEqualizerUserPresetCodec.decode(
            EchoEqualizerUserPresetCodec.encode(listOf(graphic, parametric)),
        )
        assertEquals(2, restored.size)
        assertEquals("Evening", restored[0].name)
        assertEquals(EchoEqualizerPreset.Bass, restored[0].graphicPresetId)
        assertEquals(-2.5f, restored[0].preampDb, 0.01f)
        assertEquals("p1", restored[1].id)
        assertTrue(restored[1].parametric)
        assertEquals(2, restored[1].filters.size)
        assertEquals("eq-1", restored[1].opraEqId)
        assertEquals(4.48f, restored[1].filters[1].q!!, 0.001f)
    }

    @Test
    fun invalidPayloadBecomesEmptyList() {
        assertTrue(EchoEqualizerUserPresetCodec.decode(null).isEmpty())
        assertTrue(EchoEqualizerUserPresetCodec.decode("").isEmpty())
        assertTrue(EchoEqualizerUserPresetCodec.decode("not-json").isEmpty())
        assertTrue(EchoEqualizerUserPresetCodec.decode("[{\"id\":\"\",\"name\":\"X\"}]").isEmpty())
    }

    @Test
    fun decodeDropsBrokenEntriesAndCapsCount() {
        val many = (1..30).map { index ->
            EchoEqualizerUserPreset(id = "id-$index", name = "Preset $index", updatedAtEpochMs = index.toLong())
        }
        val restored = EchoEqualizerUserPresetCodec.decode(EchoEqualizerUserPresetCodec.encode(many))
        assertEquals(EchoEqualizerUserPresets.MaxCount, restored.size)
    }
}

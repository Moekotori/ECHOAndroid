package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoEqualizerShareCodecTest {
    @Test
    fun roundTripsGraphicAndParametricCurves() {
        val graphic = EchoEqualizerUserPresets.normalize(
            EchoEqualizerUserPreset(
                id = "g1",
                name = "Evening",
                graphicPresetId = EchoEqualizerPreset.Bass,
                gainsDb = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Bass),
                preampDb = -2.5f,
            ),
        )!!
        val restoredGraphic = EchoEqualizerShareCodec.decode(EchoEqualizerShareCodec.encode(graphic)!!, "new-g")
        assertNotNull(restoredGraphic)
        assertEquals("Evening", restoredGraphic!!.name)
        assertEquals(EchoEqualizerPreset.Bass, restoredGraphic.graphicPresetId)
        assertEquals(-2.5f, restoredGraphic.preampDb, 0.01f)
        assertEquals(graphic.gainsDb.size, restoredGraphic.gainsDb.size)

        val parametric = EchoEqualizerUserPresets.normalize(
            EchoEqualizerUserPreset(
                id = "p1",
                name = "HD 650",
                parametric = true,
                preampDb = -6.1f,
                filters = listOf(
                    OpraEqBand(EchoEqFilterType.LowShelf, 105f, -1.4f, 0.7f, null),
                    OpraEqBand(EchoEqFilterType.HighPass, 20f, 0f, null, 12f),
                ),
                sourceLabel = "Sennheiser / HD 650 / oratory1990",
                opraEqId = "eq-local",
            ),
        )!!
        val code = EchoEqualizerShareCodec.encode(parametric)!!
        assertTrue(code.startsWith(EchoEqualizerShareCodec.Prefix))
        val restored = EchoEqualizerShareCodec.decode(code, "imported")!!
        assertEquals("imported", restored.id)
        assertTrue(restored.parametric)
        assertEquals(2, restored.filters.size)
        assertEquals(EchoEqFilterType.LowShelf, restored.filters[0].type)
        assertEquals(12f, restored.filters[1].slope!!, 0.01f)
        assertEquals("Sennheiser / HD 650 / oratory1990", restored.sourceLabel)
        assertNull(restored.opraEqId)
    }

    @Test
    fun extractsCodeFromWrappedMessage() {
        val preset = EchoEqualizerUserPresets.normalize(
            EchoEqualizerUserPreset(id = "g1", name = "Night mix", graphicPresetId = EchoEqualizerPreset.Night),
        )!!
        val code = EchoEqualizerShareCodec.encode(preset)!!
        val wrapped = "My curve\n$code\ntry this"
        assertEquals(code, EchoEqualizerShareCodec.extract(wrapped))
        val restored = EchoEqualizerShareCodec.decode(wrapped, "from-chat")
        assertEquals("Night mix", restored!!.name)
        assertEquals(EchoEqualizerPreset.Night, restored.graphicPresetId)
    }

    @Test
    fun rejectsGarbageAndUnknownVersion() {
        assertNull(EchoEqualizerShareCodec.decode("", "x"))
        assertNull(EchoEqualizerShareCodec.decode("not-a-code", "x"))
        assertNull(EchoEqualizerShareCodec.decode(EchoEqualizerShareCodec.Prefix + "@@@@", "x"))
        assertNull(EchoEqualizerShareCodec.extract("hello"))
    }
}

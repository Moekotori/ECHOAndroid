package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("eq-local", restored.opraEqId)
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

    @Test
    fun importsEqualizerApoAndAutoEqText() {
        val apo = """
            # Sennheiser HD 650
            # https://github.com/jaakkopasanen/AutoEq
            Preamp: -6.1 dB
            Filter 1: ON LSC Fc 105 Hz Gain -1.4 dB Q 0.7
            Filter 2: ON PK Fc 7619 Hz Gain 3.3 dB Q 4.48
            Filter 3: ON HP Fc 20 Hz
        """.trimIndent()
        val imported = EchoEqualizerShareCodec.decode(apo, "apo-1")
        assertNotNull(imported)
        assertEquals("Sennheiser HD 650", imported!!.name)
        assertTrue(imported.parametric)
        assertEquals(-6.1f, imported.preampDb, 0.01f)
        assertEquals(3, imported.filters.size)
        assertEquals(EchoEqFilterType.LowShelf, imported.filters[0].type)
        assertEquals(EchoEqFilterType.PeakDip, imported.filters[1].type)
        assertEquals(EchoEqFilterType.HighPass, imported.filters[2].type)
        assertEquals(12f, imported.filters[2].slope!!, 0.01f)

        val exported = EchoEqualizerApoCodec.encode(imported)
        assertNotNull(exported)
        val roundTrip = EchoEqualizerApoCodec.parse(exported!!, "apo-2")
        assertEquals(imported.filters.size, roundTrip!!.filters.size)
        assertEquals(imported.preampDb, roundTrip.preampDb, 0.01f)
    }

    @Test
    fun importsGraphicEqLine() {
        val imported = EchoEqualizerShareCodec.decode(
            "Preamp: -2.5 dB\nGraphicEQ: 60 4.0; 230 2.8; 910 0.4; 3600 -0.8; 14000 -1.2",
            "g",
        )
        assertNotNull(imported)
        assertEquals(5, imported!!.filters.size)
        assertEquals(60f, imported.filters[0].frequencyHz, 0.01f)
        assertEquals(4f, imported.filters[0].gainDb, 0.01f)
        assertEquals(-2.5f, imported.preampDb, 0.01f)
    }

    @Test
    fun importsApoTextFromWindowsAndPreservesFilterSettings() {
        val text = "\uFEFF# My EQ\r\nPreamp: -3 dB\r\nPreamp: -.5 dB\r\n" +
            "Channel: R\r\nFilter 1: ON PK Fc 1e3 Hz Gain 2.75 dB Q 1.25 # custom\r\n" +
            "Filter 2: OFF HS Fc 5000 Hz Gain -1 dB\r\nFilter 3: OFF"

        val preset = EchoEqualizerShareCodec.decode(text, "windows")!!
        assertEquals("My EQ", preset.name)
        assertEquals(-3.5f, preset.preampDb, 0f)
        assertEquals(2, preset.filters.size)
        assertEquals(EchoEqBandScope.Right, preset.filters[0].scope)
        assertEquals(1000f, preset.filters[0].frequencyHz, 0f)
        assertEquals(2.75f, preset.filters[0].gainDb, 0f)
        assertEquals(1.25f, preset.filters[0].q!!, 0f)
        assertFalse(preset.filters[1].enabled)
    }

    @Test
    fun refusesToImportPartialApoConfiguration() {
        val preamp = "Preamp: -4 dB"
        assertNull(EchoEqualizerShareCodec.decode("$preamp\nFilter: ON IIR Order 2 Coefficients 1 0 0 1 0 0", "iir"))
        assertNull(EchoEqualizerShareCodec.decode("$preamp\nFilter: ON PK Fc 1000 Hz Gain 2 dB BW Oct 1", "bandwidth"))
        assertNull(EchoEqualizerShareCodec.decode("$preamp\nInclude: other.txt", "include"))
        val points = (1..33).joinToString("; ") { "${it * 100} 1" }
        assertNull(EchoEqualizerShareCodec.decode("$preamp\nGraphicEQ: $points", "too-many"))
    }
}

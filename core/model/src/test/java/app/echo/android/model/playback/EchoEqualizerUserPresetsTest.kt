package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoEqualizerUserPresetsTest {
    @Test
    fun captureKeepsGraphicAndParametricSnapshots() {
        val graphic = EchoEqualizerUserPresets.capture(
            id = "g1",
            name = "  Evening  ",
            state = EchoEqualizerState(
                presetId = EchoEqualizerPreset.Bass,
                bands = EchoEqualizerPresets.defaultBands(
                    EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Bass),
                ),
                preampDb = -2.5f,
            ),
            updatedAtEpochMs = 10L,
        )
        assertNotNull(graphic)
        assertEquals("Evening", graphic!!.name)
        assertFalse(graphic.parametric)
        assertEquals(EchoEqualizerPreset.Bass, graphic.graphicPresetId)

        val filters = listOf(OpraEqBand(EchoEqFilterType.PeakDip, 1_000f, -3f, 1.2f, null))
        val parametric = EchoEqualizerUserPresets.capture(
            id = "p1",
            name = "HD 650",
            state = EchoEqualizerState(
                presetId = EchoEqualizerPreset.Custom,
                parametric = true,
                filters = filters,
                sourceLabel = "Sennheiser / HD 650 / oratory1990",
                preampDb = -6.1f,
                bands = EchoEqualizerPresets.defaultBands(),
            ),
            updatedAtEpochMs = 11L,
            opraEqId = "eq-1",
        )
        assertNotNull(parametric)
        assertTrue(parametric!!.parametric)
        assertEquals(filters, parametric.filters)
        assertEquals("eq-1", parametric.opraEqId)
        assertEquals("Sennheiser / HD 650 / oratory1990", parametric.sourceLabel)
    }

    @Test
    fun upsertReplacesSameOpraIdAndCapsList() {
        val first = requireNotNull(
            EchoEqualizerUserPresets.captureOpra(
                id = "a",
                name = "Old",
                preset = opraPreset("eq-1", listOf(OpraEqBand(EchoEqFilterType.PeakDip, 80f, 2f, 0.7f, null))),
                updatedAtEpochMs = 1L,
            ),
        )
        val updated = requireNotNull(
            EchoEqualizerUserPresets.captureOpra(
                id = "b",
                name = "New",
                preset = opraPreset("eq-1", listOf(OpraEqBand(EchoEqFilterType.LowShelf, 105f, -1.4f, 0.7f, null))),
                updatedAtEpochMs = 2L,
            ),
        )
        val replaced = EchoEqualizerUserPresets.upsert(listOf(first), updated)
        assertEquals(1, replaced!!.size)
        assertEquals("a", replaced.single().id)
        assertEquals("New", replaced.single().name)
        assertEquals(EchoEqFilterType.LowShelf, replaced.single().filters.single().type)

        val many = (1..EchoEqualizerUserPresets.MaxCount).map { index ->
            requireNotNull(
                EchoEqualizerUserPresets.capture(
                    id = "id-$index",
                    name = "Preset $index",
                    state = EchoEqualizerState(bands = EchoEqualizerPresets.defaultBands()),
                    updatedAtEpochMs = index.toLong(),
                ),
            )
        }
        assertNull(
            EchoEqualizerUserPresets.upsert(
                many,
                requireNotNull(
                    EchoEqualizerUserPresets.capture(
                        id = "overflow",
                        name = "Too many",
                        state = EchoEqualizerState(bands = EchoEqualizerPresets.defaultBands()),
                        updatedAtEpochMs = 99L,
                    ),
                ),
            ),
        )
    }

    @Test
    fun blankNameAndBlankIdAreRejected() {
        assertNull(EchoEqualizerUserPresets.normalizeName("   "))
        assertNull(EchoEqualizerUserPresets.normalize(EchoEqualizerUserPreset(id = "", name = "Keep")))
        val renamed = EchoEqualizerUserPresets.rename(
            listOf(EchoEqualizerUserPreset(id = "1", name = "Keep")),
            "1",
            "   ",
            updatedAtEpochMs = 4L,
        )
        assertEquals("Keep", renamed.single().name)
    }

    private fun opraPreset(eqId: String, bands: List<OpraEqBand>) =
        OpraHeadphoneCorrectionPreset(
            eqId = eqId,
            productId = "p",
            productName = "HD 650",
            vendorName = "Sennheiser",
            author = "oratory1990",
            details = null,
            sourceUrl = null,
            preampDb = -6.1f,
            bands = bands,
        )
}

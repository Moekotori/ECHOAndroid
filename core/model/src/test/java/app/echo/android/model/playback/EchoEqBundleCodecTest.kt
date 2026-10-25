package app.echo.android.model.playback

import org.junit.Assert.*
import org.junit.Test

class EchoEqBundleCodecTest {
    private fun preset(id: String, name: String, db: Float = 0f) = EchoEqualizerUserPresets.normalize(
        EchoEqualizerUserPreset(id, name, graphicPresetId = EchoEqualizerPreset.Bass,
            gainsDb = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Bass), preampDb = db))!!
    @Test fun bundleRoundTripsAllCurvesAndRejectsPartialOrFutureInput() {
        val input = listOf(preset("a", "Headphones", -2f), preset("b", "Speakers"))
        val text = EchoEqBundleCodec.encode(input)
        val decoded = EchoEqBundleCodec.decode(text)
        assertEquals(input.map { it.name }, decoded.map { it.name })
        assertEquals(input[0].gainsDb, decoded[0].gainsDb)
        assertEquals(-2f, decoded[0].preampDb)
        assertTrue(runCatching { EchoEqBundleCodec.decode(text.replace("BUNDLE/1", "BUNDLE/2")) }.isFailure)
        assertTrue(runCatching { EchoEqBundleCodec.decode(text + "invalid\n") }.isFailure)
    }
    @Test fun conflictPoliciesPreserveIdsForReplaceAndNeverPartiallyExceedCapacity() {
        val existing = listOf(preset("old", "Headphones"))
        val incoming = listOf(preset("new", "headphones", -3f))
        val added = EchoEqBundleCodec.merge(existing, incoming, EchoEqImportConflict.Add, 10)
        assertEquals(2, added.size)
        assertEquals("headphones (2)", added[1].name)
        assertEquals(existing, EchoEqBundleCodec.merge(existing, incoming, EchoEqImportConflict.Skip, 10))
        val replaced = EchoEqBundleCodec.merge(existing, incoming, EchoEqImportConflict.Replace, 10)
        assertEquals("old", replaced.single().id)
        assertEquals(-3f, replaced.single().preampDb)
        val full = (1..EchoEqualizerUserPresets.MaxCount).map { preset("$it", "Preset $it") }
        assertTrue(runCatching { EchoEqBundleCodec.merge(full, incoming, EchoEqImportConflict.Add, 10) }.isFailure)
        assertEquals(EchoEqualizerUserPresets.MaxCount, full.size)
    }
}

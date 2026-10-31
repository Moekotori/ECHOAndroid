package app.echo.android.model.playback

import org.junit.Assert.*
import org.junit.Test

class EchoMsebPersonalPresetTest {
    @Test fun presetFilesPreserveNamesAndSoundAndImportCopiesWithoutReplacing() {
        val preset = EchoMsebUserPreset("original", "温暖 + 人声", List(10) { it * 10f })
        val parsed = EchoMsebUserPresets.decode(EchoMsebUserPresets.encode(listOf(preset)))
        assertEquals(preset.name, parsed.single().name)
        assertEquals(preset.values, parsed.single().values)
        val merged = EchoMsebUserPresets.merge(listOf(preset), parsed) { "copy" }
        assertEquals(preset, merged.first())
        assertEquals("温暖 + 人声 (2)", merged.last().name)
        assertEquals("copy", merged.last().id)
        val state = EchoMsebState(values = preset.values, adjustmentRange = EchoMsebAdjustmentRange.Fine)
        assertEquals(90f, state.normalized.values.last(), 0f)
    }

    @Test fun malformedOversizedAndNonFinitePresetsAreRejected() {
        listOf("ECHOMSEB1\nx\tNaN", "ECHOMSEB2\n", "x".repeat(32769)).forEach { text ->
            assertTrue(runCatching { EchoMsebUserPresets.decode(text) }.isFailure)
        }
        assertNull(EchoMsebUserPresets.normalize(EchoMsebUserPreset("x", "", List(10) { 0f })))
        val preset = EchoMsebUserPreset("x", "Preset", List(10) { 0f })
        assertTrue(runCatching { EchoMsebUserPresets.merge(List(24) { preset.copy(id = "$it") }, listOf(preset)) { "new" } }.isFailure)
    }
}

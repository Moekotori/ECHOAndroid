package app.echo.android.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoSavedColorThemesTest {
    private val colors = EchoCustomColors(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())

    @Test
    fun opaqueStripsAlphaAndFillsMissingChannelsFromEcho() {
        val normalized = EchoCustomColors(0x00112233, 0x80ABCDEF.toInt(), 0x00000000).normalized()
        assertEquals(0xFF112233.toInt(), normalized.accent)
        assertEquals(0xFFABCDEF.toInt(), normalized.secondary)
        assertEquals(0xFF000000.toInt(), normalized.background)
        assertEquals(EchoCustomColors.Default, EchoCustomColors.fromStored(null, null, null))
    }

    @Test
    fun blankNameIsRejectedAndUpdateKeepsTheExistingId() {
        assertNull(EchoSavedColorThemes.normalizeName("  \n"))
        val saved = EchoSavedColorTheme("id-1", "Night", colors)
        val updated = EchoSavedColorThemes.upsert(
            listOf(saved),
            saved.copy(id = "other", name = " night ", colors = colors.copy(accent = 0xFF010203.toInt())),
        )
        assertEquals("id-1", updated?.single()?.id)
        assertEquals("night", updated?.single()?.name)
        assertEquals(0xFF010203.toInt(), updated?.single()?.colors?.accent)
    }

    @Test
    fun newThemeStopsAtTheCapAndRemoveDropsOnlyThatId() {
        val themes = List(EchoSavedColorThemes.MaxCount) { index ->
            EchoSavedColorTheme("id-$index", "Theme $index", colors)
        }
        assertNull(EchoSavedColorThemes.upsert(themes, EchoSavedColorTheme("new", "Extra", colors)))
        assertEquals(
            EchoSavedColorThemes.MaxCount - 1,
            EchoSavedColorThemes.remove(themes, "id-3").size,
        )
        assertEquals(themes, EchoSavedColorThemes.normalizeAll(themes + themes.first().copy(name = "Dup")))
    }
}

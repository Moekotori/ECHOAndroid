package app.echo.android.feature.player

import org.junit.Assert.*
import org.junit.Test

class VerticalLyricGlyphsTest {
    @Test fun mixedScriptOffsetsStillMatchProviderWordShapes() {
        val text = "春A\u0301🎵"
        val glyphs = verticalLyricGlyphs(text)
        assertEquals(listOf("春", "A\u0301", "🎵"), glyphs.map { it.text })
        assertEquals(listOf(0, 1, 3), glyphs.map { it.offset })
        assertEquals(text, glyphs.joinToString("") { it.text })
    }
    @Test fun emptyLineHasNoGlyphs() { assertTrue(verticalLyricGlyphs("").isEmpty()) }
}

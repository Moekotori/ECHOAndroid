package app.echo.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressivePlayerStyleTest {
    @Test fun preservesNewStyleIdsAndFallsBackForUnknownVersions() {
        for (id in listOf("pixel_handheld", "type_poster", "classic", "record_sleeve")) {
            assertEquals(id, normalizedPlayerStyle(id))
        }
        assertEquals("classic", normalizedPlayerStyle("unknown"))
    }

    @Test fun posterRetainsEveryTitleWord() {
        assertEquals(listOf("21ST", "CENTURY", "SCHIZOID MAN"), posterTitleLines("21st Century Schizoid Man"))
        assertEquals(listOf("ONE", "TWO", "THREE FOUR FIVE"), posterTitleLines("one two three four five"))
    }

    @Test fun posterAcceptsShortAndUnspacedTitles() {
        assertEquals(listOf("无空格歌名"), posterTitleLines("无空格歌名"))
        assertEquals(listOf("UNTITLED"), posterTitleLines("  Untitled  "))
        assertEquals(listOf("A", "SONG"), posterTitleLines("A Song"))
    }
}

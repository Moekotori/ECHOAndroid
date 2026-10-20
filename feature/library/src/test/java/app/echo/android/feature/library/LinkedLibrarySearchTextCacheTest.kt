package app.echo.android.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedLibrarySearchTextCacheTest {
    @Test fun preservesChineseFullPinyinInitialsAndEnglishSearchText() {
        val cache = LinkedLibrarySearchTextCache()
        val text = cache.text("晴天", "周杰伦", "", "HELLO")
        assertTrue(text.contains("晴天 qingtian qt"))
        assertTrue(text.contains("周杰伦 zhoujielun zjl"))
        assertTrue(text.endsWith("hello hello"))
    }

    @Test fun reusesUnchangedMetadataAndRecomputesEditedText() {
        var conversions = 0
        val cache = LinkedLibrarySearchTextCache(transliterate = { conversions++; "py" })
        cache.text("title", "artist")
        cache.text("title", "artist")
        assertEquals(2, conversions)
        assertEquals("edited py artist py", cache.text("edited", "artist"))
        assertEquals(3, conversions)
    }

    @Test fun evictsByEntryCountAndDoesNotRetainOversizedText() {
        var conversions = 0
        val cache = LinkedLibrarySearchTextCache(maxEntries = 2, maxChars = 20,
            transliterate = { conversions++; "" })
        cache.text("a", "b")
        cache.text("a", "c") // a stays hot; b is evicted.
        cache.text("b")
        assertEquals(4, conversions)
        repeat(2) { cache.text("this text exceeds the character budget") }
        assertEquals(6, conversions)
    }

    @Test fun evictsWhenCharacterBudgetIsFullEvenBelowEntryLimit() {
        var conversions = 0
        val cache = LinkedLibrarySearchTextCache(maxEntries = 10, maxChars = 8,
            transliterate = { conversions++; "" })
        cache.text("aaa")
        cache.text("bb") // Retaining both would cost ten characters.
        cache.text("aaa")
        assertEquals(3, conversions)
    }
}

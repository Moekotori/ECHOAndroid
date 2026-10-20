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

    @Test fun saturatedCacheRetainsItsWorkingSubsetAndRejectsOversizedText() {
        var conversions = 0
        val cache = LinkedLibrarySearchTextCache(maxEntries = 2, maxChars = 20,
            transliterate = { conversions++; "" })
        cache.text("a", "b")
        cache.text("a", "c") // c is not admitted; the working subset remains cached.
        cache.text("b")
        assertEquals(3, conversions)
        repeat(2) { cache.text("this text exceeds the character budget") }
        assertEquals(5, conversions)
    }

    @Test fun characterBudgetBoundsAdmissionEvenBelowEntryLimit() {
        var conversions = 0
        val cache = LinkedLibrarySearchTextCache(maxEntries = 10, maxChars = 8,
            transliterate = { conversions++; "" })
        cache.text("aaa")
        cache.text("bb") // Retaining both would cost ten characters.
        cache.text("aaa")
        assertEquals(2, conversions)
    }
    @Test fun scanningBeyondCapacityDoesNotInvalidateTheWholeCache() {
        var conversions = 0
        val cache = LinkedLibrarySearchTextCache(transliterate = { conversions++; "" })
        repeat(4097) { cache.text("unique-title-$it") }
        val firstPass = conversions
        repeat(4097) { cache.text("unique-title-$it") }
        assertEquals(4097, firstPass)
        assertEquals(1, conversions - firstPass)
    }

}

package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySubstringIndexTest {
    @Test fun everyInternalAsciiSubstringRetainsItsIndexedCandidate() {
        for (word in listOf("Melody", "qingtian", "artist2026", "ACdc", "EG3ody")) {
            val document = LibrarySubstringIndex.tokens(listOf(word))
            for (start in word.indices) {
                for (end in start + 3..word.length) {
                    val query = LibrarySubstringIndex.tokens(listOf(word.substring(start, end)))
                    assertTrue("$word [$start, $end)", document.containsAll(query))
                }
            }
        }
    }

    @Test fun punctuationAndShortTermsKeepTheExistingFallback() {
        assertTrue(LibrarySubstringIndex.tokens(listOf("ab cd", "晴天", "%_")).isEmpty())
        assertEquals(setOf("eg3ody"), LibrarySubstringIndex.tokens(listOf("ODY", "ody", "ody!")))
        assertEquals("eg3ody AND 晴* AND 天*", substringFtsQuery("晴天 ody"))
    }

    @Test fun scanAndMigrationTextIncludeInternalEnglishAndPinyinTokens() {
        val text = buildFtsNormalizedText("Melody", "周杰伦", "qingtian qt")
        for (token in listOf("eg3ody", "eg3ing", "eg3gti")) assertTrue(text.split(' ').contains(token))
        assertTrue(text.contains("周 杰 伦"))
        assertTrue(text.contains("qingtian qt"))
    }
}

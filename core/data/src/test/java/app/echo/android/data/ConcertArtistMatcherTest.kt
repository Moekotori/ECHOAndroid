package app.echo.android.data

import app.echo.android.model.library.ArtistConcert
import app.echo.android.model.library.ArtistConcertDetails
import app.echo.android.model.library.ConcertPerformer
import org.junit.Assert.*
import org.junit.Test

class ConcertArtistMatcherTest {
    @Test fun traditionalLineupsMatchSimplifiedNamesAndAliases() {
        val matcher = ConcertArtistMatcher(listOf("张学友", "Jacky Cheung"))
        assertTrue(matcher.matches(record("Live", "張學友")))
        assertTrue(matcher.matchesName("Ｊａｃｋｙ　Ｃｈｅｕｎｇ"))
        assertTrue(matcher.matches(record("Live", "Jacky Cheung")))
        assertFalse(matcher.matches(record("张学友演唱会", "Other Artist")))
        assertFalse(matcher.matchesName("张学友致敬乐团"))
    }

    @Test fun dictionaryUsesPhrasesBeforeCharacterDefaultsAndHandlesSupplementaryCharacters() {
        assertEquals("乾隆与干杯", ConcertChineseNames.simplified("乾隆與乾杯"))
        assertEquals("𫝈", ConcertChineseNames.simplified("㑮"))
        assertEquals("陈绮贞音乐会", ConcertChineseNames.simplified("陳綺貞音樂會"))
    }

    @Test fun titleMatchingKeepsWordBoundariesAndAcceptsChineseConcertSuffixesAfterAYear() {
        val matcher = ConcertArtistMatcher(listOf("陈绮贞"))
        assertTrue(matcher.matchesTitle("2026陳綺貞演唱會"))
        assertTrue(matcher.matchesTitle("陳綺貞鋼琴音樂會"))
        assertFalse(matcher.matchesTitle("小陳綺貞演唱會"))
        assertFalse(matcher.matchesTitle("陳綺貞雯演唱會"))
        assertTrue(ConcertArtistMatcher(listOf("Air")).matchesTitle("2026 Air Live"))
        assertFalse(ConcertArtistMatcher(listOf("Air")).matchesTitle("Airbourne Live"))
        assertFalse(ConcertArtistMatcher(listOf("陈")).matchesTitle("陈演唱会"))
        assertTrue(ConcertArtistMatcher(listOf("陈")).matchesName("陳"))
    }

    @Test fun bilingualTributeCannotBypassExclusionThroughAnotherTitle() {
        val matcher = ConcertArtistMatcher(listOf("Artist", "歌手"))
        val tribute = record("致敬歌手音樂會").copy(titles = listOf("致敬歌手音樂會", "Artist Live"))
        assertFalse(matcher.matches(tribute))
        assertFalse(matcher.matchesTitle("A tribute to Artist"))
    }

    @Test fun foldsLatinAccentsAndPunctuationWithoutDroppingJapaneseVoicing() {
        assertTrue(ConcertArtistMatcher(listOf("Beyonce")).matchesName("BEYONCÉ"))
        assertTrue(ConcertArtistMatcher(listOf("AC/DC")).matchesName("ＡＣ／ＤＣ"))
        assertFalse(ConcertArtistMatcher(listOf("A B")).matchesName("AB"))
        assertFalse(ConcertArtistMatcher(listOf("がく")).matchesName("かく"))
    }

    @Test fun stripsTrailingDisambiguationAndRetainsScriptVariantsForProviderSearches() {
        val names = ConcertArtistMatcher.searchNames(listOf("Perfume (Japan)", "張學友", "张学友"))
        assertEquals(listOf("Perfume (Japan)", "Perfume", "張學友", "张学友"), names)
        assertTrue(ConcertArtistMatcher(names).matchesName("Perfume"))
        assertFalse(ConcertArtistMatcher(names).matchesName("Perfume Genius"))
    }

    private fun record(title: String, performer: String? = null): RegionalConcertParser.Record {
        val detail = ArtistConcertDetails(lineup = performer?.let { listOf(ConcertPerformer(it)) }.orEmpty())
        return RegionalConcertParser.Record(ArtistConcert("show", title, "2026-12-05", null, null, null, "source", "", details = detail), listOf(title))
    }
}

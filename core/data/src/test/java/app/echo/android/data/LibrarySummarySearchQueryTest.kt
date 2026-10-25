package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySummarySearchQueryTest {
    @Test fun fullResultsKeepPreviewPredicatesWithoutTheSixResultCap() {
        val preview = LibrarySummarySearchQuery.searchAllAlbums("song", 6)
        val pages = LibrarySummarySearchQuery.searchAllAlbums("song")
        assertFalse(pages.sql.contains("isRemote = 0"))
        assertFalse(pages.sql.contains("LIMIT"))
        assertEquals(preview.sql.substringBeforeLast("LIMIT").trim(), pages.sql.trim())
        assertTrue(pages.sql.contains("albumKey ASC"))
        val artists = LibrarySummarySearchQuery.searchArtists("song")
        assertFalse(artists.sql.contains("LIMIT"))
        assertEquals(artists.argCount, artists.sql.count { it == '?' })
    }
    @Test fun indexedCategoryLookupBindsCandidatesAndPreservesItsLimit() {
        for (query in listOf(
            LibrarySummarySearchQuery.searchAlbums("ody' OR 1=1 --", 6),
            LibrarySummarySearchQuery.searchArtists("ody' OR 1=1 --", 6),
        )) {
            assertTrue(query.sql.contains("library_tracks_fts MATCH ?"))
            assertFalse(query.sql.contains("ody'"))
            assertFalse(query.sql.contains(":candidate"))
            assertFalse(query.sql.contains(":query"))
            assertEquals(query.argCount, query.sql.count { it == '?' })
            assertTrue(query.sql.trimEnd().endsWith("LIMIT ?"))
        }
    }

    @Test fun shortWildcardQueriesRetainTheUnindexedCategoryPath() {
        val query = LibrarySummarySearchQuery.searchArtists("%_", 6)
        assertFalse(query.sql.contains("library_tracks_fts"))
        assertEquals(query.argCount, query.sql.count { it == '?' })
    }
}

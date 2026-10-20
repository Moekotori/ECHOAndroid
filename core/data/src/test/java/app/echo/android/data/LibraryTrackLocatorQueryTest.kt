package app.echo.android.data

import app.echo.android.model.library.LibraryTrackSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTrackLocatorQueryTest {
    @Test fun locatorUsesExactlyThePagingFiltersAndOrderForEverySortAndSearchMode() {
        for (sort in LibraryTrackSortMode.entries) {
            for ((query, fts) in listOf("" to false, "artist" to false, "artist" to true)) {
                val paging = LibraryTrackQueryBuilder.buildTrackPagingSql(query, fts, sort)
                val locating = LibraryTrackQueryBuilder.buildTrackPagingSql(query, fts, sort, idsOnly = true)
                assertEquals(paging.replace("SELECT library_tracks.*", "SELECT library_tracks.id"), locating)
                assertTrue(locating.endsWith("library_tracks.id ASC"))
                assertFalse(locating.contains("LIMIT"))
            }
        }
    }

    @Test fun locatorRetainsLocalSourceBoundaryAndParameterizedSearch() {
        val sql = LibraryTrackQueryBuilder.buildTrackPagingSql("x' OR 1=1 --", false,
            LibraryTrackSortMode.Title, idsOnly = true)
        assertFalse(sql.contains("x'"))
        assertTrue(sql.contains(LibraryScanPolicy.LocalSourceSql))
        assertEquals(31, sql.count { it == '?' })
    }
    @Test fun queuesAndPagesShareEverySortIncludingSearchRankingAndTies() {
        for (sort in LibraryTrackSortMode.entries) {
            for ((query, fts) in listOf("" to false, "晴天" to true, "ody" to false)) {
                val page = LibraryTrackQueryBuilder.buildTrackPagingSql(query, fts, sort)
                val queue = LibraryTrackQueryBuilder.buildTrackQueueSql(query, fts, true, 60, sort)
                assertEquals(page + " LIMIT 60", queue)
            }
        }
    }

}

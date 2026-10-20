package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySummaryPagingQueryTest {
    @Test fun searchTextCannotBecomeSqlOrChangeTheSelectedSort() {
        val text = "x' OR 1=1 -- %_"
        val query = LibrarySummaryPagingQuery.albums(text, "RecentlyAdded", remote = false)
        assertFalse(query.sql.contains(text))
        assertEquals(5, query.argCount)
        assertTrue(query.sql.contains("WHERE isRemote = 0 AND ("))
        assertTrue(query.sql.endsWith("ORDER BY addedAtSeconds DESC, title COLLATE NOCASE ASC"))
    }

    @Test fun absentSearchRemovesOnlyTheSearchPredicateAndKeepsTheSourceBoundary() {
        val query = LibrarySummaryPagingQuery.albums(null, "Title", remote = true)
        assertEquals(0, query.argCount)
        assertTrue(query.sql.contains("WHERE isRemote = 1 ORDER BY"))
        assertFalse(query.sql.contains("LIKE"))
        assertFalse(query.sql.contains("CASE WHEN"))
        // Unknown sort strings fall back to the existing default, never become SQL.
        assertEquals(query.sql, LibrarySummaryPagingQuery.albums(null, "DROP TABLE x", true).sql)
    }
}

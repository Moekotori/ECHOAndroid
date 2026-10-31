package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test

class LibraryDocumentsQueriesTest {
    @Test fun documentIdsRemainDataAndCueOriginsUseAnIndexedRange() {
        val plan = LibraryDocumentsQueries.source("track:local:a' OR 1=1 --")!!
        assertFalse(plan.sql.contains("a' OR"))
        assertEquals(listOf("local:a' OR 1=1 --", "local:a' OR 1=1 --#cue:", "local:a' OR 1=1 --#cue;"), plan.args)
        assertTrue(plan.sql.contains("visible_library_tracks"))
        assertNull(LibraryDocumentsQueries.source("album:any"))
        assertNull(LibraryDocumentsQueries.source("track:base#cue:1"))
        assertNull(LibraryDocumentsQueries.source("track:"))
    }

    @Test fun projectionAndSortingCannotExposeSourceUrisOrInjectSql() {
        val plan = LibraryDocumentsQueries.children("all", "Unknown", "Original", 0)!!
        val projected = LibraryDocumentsQueries.project(plan, arrayOf("document_id", "contentUri", "_data", "\"; DROP TABLE library_tracks;--"))
        assertTrue(projected.sql.contains("NULL AS \"contentUri\""))
        assertTrue(projected.sql.contains("NULL AS \"_data\""))
        assertTrue(projected.sql.contains("NULL AS \"\"\"; DROP"))
        assertNull(LibraryDocumentsQueries.sort("_display_name DESC; DROP TABLE library_tracks"))
        assertEquals("_display_name COLLATE NOCASE DESC, document_id", LibraryDocumentsQueries.sort("_display_name DESC"))
    }

    @Test fun searchTreatsWildcardsAsLiteralTextAndPagingDoesNotTruncateLegacyCalls() {
        val search = LibraryDocumentsQueries.search("100%_\\", "Original")
        assertEquals(List(4) { "%100\\%\\_\\\\%" }, search.args.drop(1))
        val all = LibraryDocumentsQueries.project(search, null)
        assertFalse(all.sql.contains("LIMIT"))
        val paged = LibraryDocumentsQueries.project(search, null, limit = 2, offset = 4)
        assertTrue(paged.sql.endsWith("LIMIT 2 OFFSET 4"))
    }

    @Test fun zeroPageReturnsNoRowsAndAnEmptyProjectionUsesTheDocumentContract() {
        val plan = LibraryDocumentsQueries.search("", "Original")
        assertTrue(LibraryDocumentsQueries.project(plan, emptyArray(), limit = 0).sql.endsWith("LIMIT 0 OFFSET 0"))
        assertArrayEquals(LibraryDocumentsQueries.Columns, LibraryDocumentsQueries.columns(emptyArray()))
    }

    @Test fun mimeFiltersAreBoundAndInvalidPatternsNeverTurnIntoAnUnrestrictedSearch() {
        val plan = LibraryDocumentsQueries.search("", "Original")
        val filtered = LibraryDocumentsQueries.searchFilters(plan, listOf("AUDIO/FLAC", "audio/*"), 1024, 1700000000000)
        assertEquals(listOf("audio/flac", "audio/%", "1024", "1700000000000"), filtered.args.takeLast(4))
        assertTrue(LibraryDocumentsQueries.searchFilters(plan, listOf("audio/%' OR 1=1 --")).sql.endsWith("WHERE 0"))
        assertTrue(LibraryDocumentsQueries.searchFilters(plan, emptyList()).sql.endsWith("WHERE 0"))
        assertThrows(IllegalArgumentException::class.java) { LibraryDocumentsQueries.searchFilters(plan, List(65) { "audio/wav" }) }
    }
}

package app.echo.android.data

import androidx.sqlite.db.SupportSQLiteProgram
import app.echo.android.model.library.*
import org.junit.Assert.*
import org.junit.Test

class LibraryTrackFilterQueryTest {
    @Test fun lyricsFiltersUseVerifiedFreshEvidenceAndNeverTreatAnUnknownRowAsMissing() {
        val present = LibraryTrackFilter(lyrics = LibraryPresenceFilter.Present).toSql().clause
        val missing = LibraryTrackFilter(lyrics = LibraryPresenceFilter.Missing).toSql().clause
        assertTrue(present.contains("i.status = 1"))
        assertTrue(missing.contains("i.status = 0"))
        assertFalse(missing.contains("NOT EXISTS"))
        assertFalse(missing.contains("NOT IN"))
        assertTrue(missing.contains("i.trackFingerprint IS indexedTrack.fingerprint"))
        assertTrue(missing.contains("i.contentUri = indexedTrack.contentUri"))
        assertTrue(missing.contains("i.sizeBytes = indexedTrack.sizeBytes"))
        assertFalse(LibraryTrackFilter().toSql().clause.contains("library_lyrics_index"))
        assertEquals(1, LibraryTrackFilter(lyrics = LibraryPresenceFilter.Missing).activeCount)
    }

    @Test fun reverseOrdersReuseExistingIndexesWithoutForcingThemForSelectiveFilters() {
        val cases = listOf(
            LibraryTrackSortMode.TitleDescending to "index_library_tracks_title_id",
            LibraryTrackSortMode.DurationAscending to "index_library_tracks_durationMs_title_id",
            LibraryTrackSortMode.ModifiedOldest to "index_library_tracks_dateModifiedSeconds_title_id",
        )
        for ((sort, index) in cases) {
            assertTrue(LibraryTrackQueryBuilder.buildTrackPagingSql("", false, sort).contains("INDEXED BY $index"))
            assertFalse(LibraryTrackQueryBuilder.buildTrackPagingSql("", false, sort,
                filter = LibraryTrackFilter(favoritesOnly = true)).contains("INDEXED BY"))
        }
    }

    @Test fun combinedFiltersBindValuesBeforeSearchRankingAndNeverInterpolateInput() {
        val filter = LibraryTrackFilter(artist = " O'Brien ", folder = "Music/100%_hits", minYear = 2000,
            maxYear = 2026, minDurationMs = 60_000, formats = setOf(LibraryFileFormat.Mp3, LibraryFileFormat.Flac),
            favoritesOnly = true, played = LibraryPlayedFilter.NeverPlayed, artwork = LibraryPresenceFilter.Missing)
        val query = LibraryTrackQueryBuilder.boundQuery("needle", "needle*", LibraryTrackSortMode.Title, filter = filter)
        val values = Bindings().also(query::bindTo).values.toSortedMap().values.toList()
        assertFalse(query.sql.contains("O'Brien"))
        assertFalse(query.sql.contains("100%_hits"))
        assertEquals(query.sql.count { it == '?' }, values.size)
        assertEquals(listOf("o'brien", "Music/100%_hits", 2000L, 2026L, 60_000L, "Flac", "Mp3"), values.subList(8, 15))
        assertEquals(listOf("needle", "needle", "needle"), values.takeLast(3))
        assertTrue(query.sql.contains("library_favorites"))
        assertTrue(query.sql.contains("NOT IN (SELECT trackId FROM library_playback_stats WHERE playCount > 0)"))
        assertFalse(query.sql.contains("INDEXED BY"))
        val filterClause = filter.toSql().clause
        assertTrue(filterClause.indexOf("library_favorites") < filterClause.indexOf("instr("))
    }

    @Test fun unicodeTagsUseStoredNormalizedKeysAndEmptyNamesFallBackToTheUri() {
        val sql = LibraryTrackFilter(artist = "BJÖRK", genre = "ÉLECTRONIQUE", composer = "坂本", album = "ＡＬＢＵＭ").toSql()
        assertEquals(listOf("björk", "album", "électronique", "坂本"), sql.args)
        for (key in listOf("normalizedArtist", "normalizedAlbum", "genreKey", "composerKey")) {
            assertTrue(sql.clause.contains("NULLIF(library_tracks.$key, '')"))
        }
        assertTrue(LibraryFileFormatSql.contains("NULLIF(trim(library_tracks.fileName), '')"))
        assertTrue(LibraryFileFormatSql.contains("audio/x-wavpack"))
    }

    @Test fun queueWindowsKeepTheSameFilteredOrderFarBeyondTheFirstPage() {
        val filter = LibraryTrackFilter(favoritesOnly = true)
        for (sort in LibraryTrackSortMode.entries) {
            val page = LibraryTrackQueryBuilder.buildTrackPagingSql("", false, sort, filter = filter)
            val offset = LibraryPlaybackQueuePolicy.windowOffset(2_500, 200)
            assertEquals(2_400, offset)
            assertEquals("$page LIMIT 200 OFFSET 2400",
                LibraryTrackQueryBuilder.buildTrackQueueSql("", false, true, 200, sort, filter, offset))
        }
        assertEquals(0, LibraryPlaybackQueuePolicy.windowOffset(0, 200))
        assertEquals(0, LibraryPlaybackQueuePolicy.windowOffset(50, 200))
    }

    @Test fun filteredPagesLocatingAndPlaybackQueuesHaveExactlyTheSameConditionsAndOrder() {
        val filter = LibraryTrackFilter(genre = "Rock", minSampleRateHz = 48_000, maxSizeBytes = 50_000_000,
            duplicatesOnly = true)
        for (sort in LibraryTrackSortMode.entries) {
            val page = LibraryTrackQueryBuilder.buildTrackPagingSql("", false, sort, filter = filter)
            val locator = LibraryTrackQueryBuilder.buildTrackPagingSql("", false, sort, idsOnly = true, filter = filter)
            val queue = LibraryTrackQueryBuilder.buildTrackQueueSql("", false, true, 80, sort, filter)
            assertEquals(page.replace("SELECT library_tracks.*", "SELECT library_tracks.id"), locator)
            assertEquals("$page LIMIT 80", queue)
            assertTrue(page.contains("HAVING count(*) > 1"))
            assertTrue(page.contains(LibraryScanPolicy.LocalSourceSql))
            assertTrue(page.endsWith("library_tracks.id ASC"))
        }
    }

    @Test fun missingMetadataStaysLastInYearAndSampleRateSortsAndNoHistoryCountsAsLeastPlayed() {
        for (sort in listOf(LibraryTrackSortMode.YearAscending, LibraryTrackSortMode.YearDescending,
            LibraryTrackSortMode.SampleRateAscending, LibraryTrackSortMode.SampleRateDescending)) {
            val sql = LibraryTrackQueryBuilder.buildTrackPagingSql("", false, sort)
            assertTrue(sql.contains("THEN 1 ELSE 0 END"))
        }
        val sql = LibraryTrackQueryBuilder.buildTrackPagingSql("", false, LibraryTrackSortMode.LeastPlayed)
        assertTrue(sql.contains("LEFT JOIN library_playback_stats"))
        assertTrue(sql.contains("COALESCE(library_playback_stats.playCount, 0) ASC"))
    }

    private class Bindings : SupportSQLiteProgram {
        val values = linkedMapOf<Int, Any?>()
        override fun bindNull(index: Int) { values[index] = null }
        override fun bindLong(index: Int, value: Long) { values[index] = value }
        override fun bindDouble(index: Int, value: Double) { values[index] = value }
        override fun bindString(index: Int, value: String) { values[index] = value }
        override fun bindBlob(index: Int, value: ByteArray) { values[index] = value }
        override fun clearBindings() { values.clear() }
        override fun close() {}
    }
}

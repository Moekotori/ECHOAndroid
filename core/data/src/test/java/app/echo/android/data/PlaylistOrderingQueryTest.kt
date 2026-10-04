package app.echo.android.data

import androidx.sqlite.db.SupportSQLiteProgram
import app.echo.android.model.library.*
import org.junit.Assert.*
import org.junit.Test

class PlaylistOrderingQueryTest {
    @Test fun descendingAnchorsUseTheSameKeysAndBindingsAsPaging() {
        val id = "local:a'; DROP TABLE library_tracks; --"
        val query = LibraryCollectionPlaybackQuery.build(LibraryPlaybackOrigin.Playlist(id), "anchor",
            80, PlaylistTrackOrder(PlaylistTrackSort.Artist, true))
        assertFalse(query.sql.contains(id))
        assertTrue(query.sql.contains("t.artist COLLATE NOCASE < anchor.k0"))
        assertTrue(query.sql.contains("t.title COLLATE NOCASE > anchor.k1"))
        assertTrue(query.sql.endsWith("t.artist COLLATE NOCASE DESC, t.title COLLATE NOCASE ASC, t.id ASC LIMIT ?"))
        val recorder = Bindings()
        query.bindTo(recorder)
        assertEquals(listOf(id, "anchor", id, 80L), recorder.values.toSortedMap().values.toList())
    }

    @Test fun manualPagingKeepsStoredPositionsAndHasNoArtificialLimit() {
        val query = playlistTrackQuery("local:custom", PlaylistTrackOrder())
        assertTrue(query.sql.contains("ORDER BY p.position, t.id"))
        assertFalse(query.sql.contains("LIMIT"))
        val recorder = Bindings()
        query.bindTo(recorder)
        assertEquals(listOf("local:custom"), recorder.values.values.toList())
    }

    @Test fun customSmartPlaylistCopyAndExportCanDetectOverflow() {
        val query = playlistTrackQuery(EchoSmartPlaylistRule.IdPrefix + "rule", PlaylistTrackOrder(), 10_001)
        assertTrue(query.sql.contains("LIMIT 10001 OFFSET 0"))
    }

    @Test fun totalsDoNotSortOrLimitThePlaylist() {
        val query = playlistStatsQuery("local:custom")
        assertTrue(query.sql.contains("COUNT(*) AS trackCount"))
        assertTrue(query.sql.contains("SUM(MAX(durationMs, 0))"))
        assertFalse(query.sql.contains("ORDER BY"))
        assertFalse(query.sql.contains("LIMIT"))
        val recorder = Bindings()
        query.bindTo(recorder)
        assertEquals(listOf("local:custom"), recorder.values.values.toList())
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

package app.echo.android.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class LibraryAggregationBackfillTest {
    @Test fun editedLegacyCompilationMigratesWithoutSplittingAndSingletonResetsItsKey() = runBlocking {
        val tracks = listOf("Artist A", "Artist B").mapIndexed { index, artist ->
            track("$index", "Compilation", artist, "Music/Compilation/")
                .copy(albumArtist = artist, metadataEditedAtEpochMs = if (index == 0) 99 else null)
                .withComputedSearchMetadata().copy(albumKey = "compilation::various artists")
        }
        val store = BackfillDao(tracks)
        assertTrue(backfillLibraryAggregationKeys(store.dao, 2) { it.withComputedSearchMetadata() })
        assertEquals(
            setOf(LibraryAlbumGrouping.inferredCompilationKey("compilation", "music/compilation")),
            store.rows.values.map { it.albumKey }.toSet(),
        )
        assertTrue(!backfillLibraryAggregationKeys(store.dao, 2) { it.withComputedSearchMetadata() })
        store.rows.remove("1")
        assertTrue(backfillLibraryAggregationKeys(store.dao, 2) { it.withComputedSearchMetadata() })
        assertEquals("compilation::artist a", store.rows.getValue("0").albumKey)
    }

    @Test fun existingCompilationWithPerSongAlbumArtistsRegroupsAcrossBatches() = runBlocking {
        val tracks = listOf("Aeron Bergman", "Jasper Leyland", "Thuja", "Geir Jenssen").mapIndexed { index, artist ->
            track("$index", "14 Tracks Documenting", artist, "Music/")
                .copy(albumArtist = artist, artworkUri = "file:///artwork/compilation.jpg")
                .withComputedSearchMetadata()
        }
        val store = BackfillDao(tracks)

        assertTrue(backfillLibraryAggregationKeys(store.dao, 2) { it.withComputedSearchMetadata() })
        assertEquals(
            setOf(LibraryAlbumGrouping.inferredCompilationKey("14 tracks documenting", "music", tracks.first().artworkUri)),
            store.rows.values.map { it.albumKey }.toSet(),
        )
        assertEquals(tracks.map { it.artist }, store.rows.values.map { it.artist })
        assertEquals(tracks.map { it.albumArtist }, store.rows.values.map { it.albumArtist })
        assertTrue(store.fullReadSizes.all { it <= 2 })
        assertTrue(store.writeSizes.all { it <= 2 })
        assertTrue(!backfillLibraryAggregationKeys(store.dao, 2) { it.withComputedSearchMetadata() })
    }

    @Test fun pagedMigrationPreservesWholeLibraryGroupingAcrossDiscFoldersAndSharedCredits() = runBlocking {
        val tracks = listOf(
            track("9", "Live (Disc 1)", "Band/Guest A", "Music/Live/CD1"),
            track("2", "Live (CD2)", "Band/Guest B", "music/live/CD2"),
            track("7", "Compilation", "Artist A", "Music/Compilation"),
            track("4", "Compilation", "Artist B", "Music/Compilation"),
            track("6", "Greatest Hits", "Queen", "Download"),
            track("3", "Greatest Hits", "ABBA", "Download"),
            track("", "No path", "Other", null),
            track("8", "Live", "Remote artist", "Music/Live").copy(source = "subsonic:test"),
        )
        val prepared = tracks.map { it.withComputedSearchMetadata() }
        val changes = LibraryAlbumGrouping.reconcile(prepared).associateBy { it.id }
        val expected = prepared.associate { it.id to (changes[it.id] ?: it) }
        val store = BackfillDao(tracks)
        assertTrue(backfillLibraryAggregationKeys(store.dao, 2) { it.withComputedSearchMetadata() })
        assertEquals(expected, store.rows)
        assertTrue(store.fullReadSizes.all { it <= 2 })
        assertTrue(store.writeSizes.all { it <= 2 })
    }

    private fun track(id: String, album: String, artist: String, path: String?) = LibraryTrackEntity(
        id = id, contentUri = "content://audio/$id", title = "Track $id", artist = artist,
        album = album, albumArtist = null, artworkUri = null, durationMs = 60_000,
        trackNumber = null, discNumber = null, year = null, mimeType = "audio/flac",
        sizeBytes = 1_000, dateModifiedSeconds = 1, relativePath = path,
    )

    private class BackfillDao(tracks: List<LibraryTrackEntity>) {
        val rows = tracks.associateByTo(linkedMapOf()) { it.id }
        val fullReadSizes = mutableListOf<Int>()
        val writeSizes = mutableListOf<Int>()

        @Suppress("UNCHECKED_CAST")
        val dao: LibraryTrackDao = Proxy.newProxyInstance(
            LibraryTrackDao::class.java.classLoader, arrayOf(LibraryTrackDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getAggregationBackfillFirstTracks" -> page(null, args[0] as Int).also { fullReadSizes += it.size }
                "getAggregationBackfillTracksAfter" -> page(args[0] as String, args[1] as Int).also { fullReadSizes += it.size }
                "getLocalAlbumGroupingFirstRows" -> localPage(null, args[0] as Int)
                "getLocalAlbumGroupingRowsAfter" -> localPage(args[0] as String, args[1] as Int)
                "getTracksByIds" -> (args[0] as List<String>).mapNotNull(rows::get).also { fullReadSizes += it.size }
                "upsertBatchWithFts" -> {
                    val updated = args[0] as List<LibraryTrackEntity>
                    writeSizes += updated.size
                    updated.forEach { rows[it.id] = it }
                    Unit
                }
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as LibraryTrackDao

        private fun page(after: String?, limit: Int) = rows.values
            .filter { after == null || it.id > after }.sortedBy { it.id }.take(limit)

        private fun localPage(after: String?, limit: Int) = rows.values
            .filter { LibraryScanPolicy.isLocalLibrarySource(it.source) && (after == null || it.id > after) }
            .sortedBy { it.id }.take(limit)
            .map { LibraryAlbumGroupingRow(it.id, it.normalizedAlbum, it.album, it.relativePath) }
    }
}

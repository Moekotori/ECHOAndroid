package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class LibraryAlbumGroupingMaintenanceTest {
    @Test
    fun albumEditsIncludeDiscSiblingsAndExcludeOtherTitlesAndFolders() = runBlocking {
        val tracks = listOf(
            track("1", "Live (CD1)", "Band/Guest A", "Music/Live/CD1"),
            track("2", "Live (Disc 2)", "Band/Guest B", "Music/Live/CD2"),
            track("3", "Live at Home", "Other", "Music/Live"),
            track("4", "Live (CD2)", "Other", "Music/Other/Live/CD2"),
        )
        val store = tracks.associateByTo(linkedMapOf()) { it.id }
        val queries = mutableListOf<String>()
        var changedSummaryKeys = LibrarySummaryKeySet()
        @Suppress("UNCHECKED_CAST")
        val dao = Proxy.newProxyInstance(
            LibraryTrackDao::class.java.classLoader, arrayOf(LibraryTrackDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getLocalAlbumGroupingCandidates" -> {
                    val prefix = args[0] as String
                    val end = args[1] as String
                    queries += prefix
                    store.values.filter { it.normalizedAlbum.orEmpty() >= prefix && it.normalizedAlbum.orEmpty() < end }
                }
                "upsertBatchWithFts" -> {
                    (args[0] as List<LibraryTrackEntity>).forEach { store[it.id] = it }
                    Unit
                }
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as LibraryTrackDao
        val maintenance = LibraryAlbumGroupingMaintenance(dao, 2, Dispatchers.Unconfined) { changedSummaryKeys = it }
        val changes = maintenance.reconcileAlbums(setOf("live"), setOf("music/live"))

        assertEquals(listOf("live"), queries)
        assertEquals(setOf("mediastore:1", "mediastore:2"), changes.map { it.id }.toSet())
        assertEquals(setOf("live::band"), changes.map { it.albumKey }.toSet())
        assertEquals(tracks[2], store.getValue("mediastore:3"))
        assertEquals(tracks[3], store.getValue("mediastore:4"))
        assertEquals(tracks.take(2).map { it.albumKey }.toSet() + "live::band", changedSummaryKeys.albumKeys)
        assertTrue(maintenance.reconcileAlbums(setOf("live"), setOf("music/live")).isEmpty())
    }

    private fun track(id: String, album: String, artist: String, path: String): LibraryTrackEntity =
        EchoTrack("mediastore:$id", "content://media/$id", "Track $id", artist,
            album = album, durationMs = 1000).toLibraryTrackEntity()
            .copy(relativePath = path).withComputedSearchMetadata()
}

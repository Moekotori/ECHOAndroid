package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import org.junit.Assert.*
import org.junit.Test

class LibraryScanFilterTest {
    private val defaults = LibraryScanOptions()

    @Test fun durationAndSizeBoundaries() {
        assertTrue(defaults.accepts(30_000, 102_400, "Music/"))
        assertFalse(defaults.accepts(29_999, 102_400, "Music/"))
        assertFalse(defaults.accepts(30_000, 102_399, "Music/"))
        assertTrue(defaults.copy(minDurationMs = 0, minSizeBytes = 0).accepts(100, 100, "Music/"))
    }

    @Test fun unknownMetadataRemainsImportable() {
        assertTrue(defaults.accepts(0, 0, "Music/DSD/"))
        assertFalse(defaults.accepts(0, 500, "Music/"))
    }

    @Test fun folderRulesMatchComponentsNotSubstrings() {
        assertFalse(defaults.accepts(60_000, 200_000, "Removable/abcd/Recordings/2026/"))
        assertFalse(defaults.accepts(60_000, 200_000, "Notifications/"))
        assertFalse(defaults.accepts(60_000, 200_000, "Music/.cache/"))
        assertTrue(defaults.accepts(60_000, 200_000, "Music/Live Recordings/"))
        assertTrue(defaults.copy(excludeNonMusicFolders = false, excludeHiddenFolders = false)
            .accepts(60_000, 200_000, ".hidden/Recordings/"))
    }

    @Test fun filteredRescanPreservesExistingSongsWhileRejectingNewNoise() {
        val existing = track("existing", 1000)
        val noise = track("new-noise", 1000)
        val song = track("new-song", 60_000)
        assertEquals(listOf(existing, song), filterLocalScanBatch(listOf(existing, noise, song), setOf(existing.id), defaults))
    }

    private fun track(id: String, duration: Long) = LibraryTrackEntity(
        id = id, contentUri = "content://test/$id", title = id, artist = "Artist", album = null,
        albumArtist = null, artworkUri = null, durationMs = duration, trackNumber = null,
        discNumber = null, year = null, mimeType = "audio/wav", sizeBytes = 200_000,
        dateModifiedSeconds = 1000, relativePath = "Music/",
    )
}

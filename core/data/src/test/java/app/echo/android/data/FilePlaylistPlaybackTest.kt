package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test

class FilePlaylistPlaybackTest {
    @Test fun appendKeepsPickerOrderSkipsExistingAndChecksCapacityBeforeReadingFiles() {
        assertEquals(listOf("new-b", "new-a"), newFilePlaylistUris(listOf("old"), listOf("old", "new-b", "new-a", "new-b")))
        val full = List(FilePlaylistStore.MaxEntries) { "file:$it" }
        assertTrue(newFilePlaylistUris(full, listOf("file:1")).isEmpty())
        try {
            newFilePlaylistUris(full, listOf("new"))
            fail("Overflow must fail before metadata reads or grants")
        } catch (_: IllegalArgumentException) { }
    }

    private fun row(uri: String) = LibraryTrackEntity("file:test", uri, "Course", "",
        null, null, null, 1000, null, null, null, "audio/mpeg", 1024,
        dateModifiedSeconds = 0, source = "file-playlist")

    @Test fun privatePlaylistRejectsCloudEntriesBeforeChangingAnyRows() {
        validateFilePlaylistTracks(listOf(row("content://course/1"), row("file:///music/1.mp3")))
        for (uri in listOf("https://server/song", "smb://server/song", "content:" + "a".repeat(8200))) {
            try {
                validateFilePlaylistTracks(listOf(row("content://course/1"), row(uri)))
                fail("Non-local or oversized reference accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun clickingALaterFileStartsAWindowContainingItWithoutLibraryLookups() {
        val rows = List(20) { index -> LibraryTrackEntity("file:$index", "content://course/$index", "$index", "",
            null, null, null, 1000, null, null, null, "audio/mpeg", 1024,
            dateModifiedSeconds = 0, source = "file-playlist") }
        val queue = filePlaylistPlaybackWindow(rows, "file:18", 5)
        assertTrue(queue.any { it.id == "file:18" })
        assertTrue(queue.size <= 5)
        assertEquals(queue.sortedBy { it.title.toInt() }, queue)
        assertTrue(filePlaylistPlaybackWindow(rows, "removed-file", 5).isEmpty())
        assertEquals(rows, filePlaylistPlaybackWindow(rows, "file:18", 100))
    }
}

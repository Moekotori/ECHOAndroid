package app.echo.android.model.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCollectionSortTest {
    @Test
    fun albumsSortByArtistThenTitle() {
        val sorted = listOf(
            album(title = "Zebra", artist = "Beta"),
            album(title = "Alpha", artist = "Beta"),
            album(title = "Moon", artist = "Alpha"),
        ).sortedForLibrary(AlbumSortMode.Artist)
        assertEquals(listOf("Moon", "Alpha", "Zebra"), sorted.map(AlbumSummary::title))
    }

    @Test
    fun albumsSortByRecentlyAdded() {
        val sorted = listOf(
            album(title = "Old", addedAtSeconds = 10L),
            album(title = "New", addedAtSeconds = 30L),
            album(title = "Mid", addedAtSeconds = 20L),
        ).sortedForLibrary(AlbumSortMode.RecentlyAdded)
        assertEquals(listOf("New", "Mid", "Old"), sorted.map(AlbumSummary::title))
    }

    @Test
    fun artistsSortByAlbumCount() {
        val sorted = listOf(
            artist(name = "Few", albumCount = 1, trackCount = 8),
            artist(name = "Many", albumCount = 4, trackCount = 2),
            artist(name = "Also many", albumCount = 4, trackCount = 9),
        ).sortedForLibrary(ArtistSortMode.AlbumCount)
        assertEquals(listOf("Also many", "Many", "Few"), sorted.map(ArtistSummary::name))
    }

    @Test
    fun foldersKeepEmptyPathLastWhenSortingByName() {
        val sorted = listOf(
            folder(folderKey = "", path = null, trackCount = 9),
            folder(folderKey = "b", path = "Music/B", trackCount = 1),
            folder(folderKey = "a", path = "Music/A", trackCount = 3),
        ).sortedForLibrary(FolderSortMode.Path)
        assertEquals(listOf("a", "b", ""), sorted.map(FolderSummary::folderKey))
    }

    @Test
    fun foldersSortByTrackCountThenPath() {
        val sorted = listOf(
            folder(folderKey = "b", path = "Music/B", trackCount = 4),
            folder(folderKey = "a", path = "Music/A", trackCount = 4),
            folder(folderKey = "c", path = "Music/C", trackCount = 1),
        ).sortedForLibrary(FolderSortMode.TrackCount)
        assertEquals(listOf("a", "b", "c"), sorted.map(FolderSummary::folderKey))
    }

    private fun album(
        title: String,
        artist: String = "Artist",
        addedAtSeconds: Long = 0L,
    ) = AlbumSummary(
        albumKey = title,
        title = title,
        albumArtist = artist,
        artist = artist,
        artworkUri = null,
        trackCount = 1,
        durationMs = 1L,
        year = 2000,
        addedAtSeconds = addedAtSeconds,
    )

    private fun artist(
        name: String,
        albumCount: Int,
        trackCount: Int,
    ) = ArtistSummary(
        artistKey = name,
        name = name,
        artworkUri = null,
        albumCount = albumCount,
        trackCount = trackCount,
        durationMs = 1L,
    )

    private fun folder(
        folderKey: String,
        path: String?,
        trackCount: Int,
    ) = FolderSummary(
        folderKey = folderKey,
        path = path,
        artworkUri = null,
        trackCount = trackCount,
        albumCount = 1,
        artistCount = 1,
        durationMs = 1L,
        totalSizeBytes = 1L,
        latestModifiedSeconds = 1L,
    )
}

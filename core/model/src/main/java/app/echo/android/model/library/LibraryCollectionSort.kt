package app.echo.android.model.library

fun List<AlbumSummary>.sortedForLibrary(mode: AlbumSortMode): List<AlbumSummary> =
    when (mode) {
        AlbumSortMode.Title -> sortedWith(
            compareBy<AlbumSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.albumArtist.orEmpty() },
        )
        AlbumSortMode.Artist -> sortedWith(
            compareBy<AlbumSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.albumArtist ?: it.artist.orEmpty() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        AlbumSortMode.Year -> sortedWith(
            compareByDescending<AlbumSummary> { it.year ?: Int.MIN_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        AlbumSortMode.TrackCount -> sortedWith(
            compareByDescending<AlbumSummary> { it.trackCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        AlbumSortMode.Duration -> sortedWith(
            compareByDescending<AlbumSummary> { it.durationMs }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        AlbumSortMode.RecentlyAdded -> sortedWith(
            compareByDescending<AlbumSummary> { it.addedAtSeconds }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }

fun List<ArtistSummary>.sortedForLibrary(mode: ArtistSortMode): List<ArtistSummary> =
    when (mode) {
        ArtistSortMode.Name -> sortedWith(
            compareBy<ArtistSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenByDescending { it.trackCount },
        )
        ArtistSortMode.AlbumCount -> sortedWith(
            compareByDescending<ArtistSummary> { it.albumCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
        ArtistSortMode.TrackCount -> sortedWith(
            compareByDescending<ArtistSummary> { it.trackCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
        ArtistSortMode.Duration -> sortedWith(
            compareByDescending<ArtistSummary> { it.durationMs }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
    }

fun List<FolderSummary>.sortedForLibrary(mode: FolderSortMode): List<FolderSummary> {
    val byPath = compareBy<FolderSummary> { if (it.folderKey.isEmpty()) 1 else 0 }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.path.orEmpty() }
    return when (mode) {
        FolderSortMode.Path -> sortedWith(byPath)
        FolderSortMode.TrackCount -> sortedWith(compareByDescending<FolderSummary> { it.trackCount }.then(byPath))
        FolderSortMode.AlbumCount -> sortedWith(compareByDescending<FolderSummary> { it.albumCount }.then(byPath))
        FolderSortMode.Duration -> sortedWith(compareByDescending<FolderSummary> { it.durationMs }.then(byPath))
        FolderSortMode.Size -> sortedWith(compareByDescending<FolderSummary> { it.totalSizeBytes }.then(byPath))
        FolderSortMode.RecentlyModified -> sortedWith(
            compareByDescending<FolderSummary> { it.latestModifiedSeconds }.then(byPath),
        )
    }
}

package app.echo.android.data

/** Collects scan keys in place; snapshots never share the mutable working sets. */
internal class LibrarySummaryKeyAccumulator {
    private val albums = linkedSetOf<String>()
    private val artists = linkedSetOf<String>()
    private val folders = linkedSetOf<String>()
    private val genres = linkedSetOf<String>()

    fun add(keys: LibrarySummaryKeySet) {
        albums.addAll(keys.albumKeys)
        artists.addAll(keys.artistKeys)
        folders.addAll(keys.folderKeys)
        genres.addAll(keys.genreKeys)
    }

    fun build(): LibrarySummaryKeySet = LibrarySummaryKeySet(
        albums.toSet(), artists.toSet(), folders.toSet(), genres.toSet(),
    )
}

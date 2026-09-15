package app.echo.android.playback

object EchoPlaybackLibraryIds {
    const val ROOT = "echo.root"
    const val ALBUMS = "echo.albums"
    const val ARTISTS = "echo.artists"
    const val PLAYLISTS = "echo.playlists"
    const val FAVORITES = "echo.favorites"
    const val TRACKS = "echo.tracks"
    const val FOLDERS = "echo.folders"
    const val GENRES = "echo.genres"
    const val RADIO = "echo.radio"

    const val MAX_PAGE_SIZE = 100
    const val PLAYABLE_QUEUE_LIMIT = 200

    private const val ALBUM_PREFIX = "echo.album."
    private const val ARTIST_PREFIX = "echo.artist."
    private const val PLAYLIST_PREFIX = "echo.playlist."
    private const val FOLDER_PREFIX = "echo.folder."
    private const val GENRE_PREFIX = "echo.genre."

    fun album(albumKey: String): String = ALBUM_PREFIX + albumKey

    fun artist(artistKey: String): String = ARTIST_PREFIX + artistKey

    fun playlist(playlistId: String): String = PLAYLIST_PREFIX + playlistId

    fun folder(folderKey: String): String = FOLDER_PREFIX + folderKey

    fun genre(genreKey: String): String = GENRE_PREFIX + genreKey

    fun albumKey(mediaId: String): String? = prefixedValue(mediaId, ALBUM_PREFIX)

    fun artistKey(mediaId: String): String? = prefixedValue(mediaId, ARTIST_PREFIX)

    fun playlistId(mediaId: String): String? = prefixedValue(mediaId, PLAYLIST_PREFIX)

    fun folderKey(mediaId: String): String? = prefixedValue(mediaId, FOLDER_PREFIX, allowEmpty = true)

    fun genreKey(mediaId: String): String? = prefixedValue(mediaId, GENRE_PREFIX)

    fun isCategory(mediaId: String): Boolean =
        mediaId == ROOT ||
            mediaId == ALBUMS ||
            mediaId == ARTISTS ||
            mediaId == PLAYLISTS ||
            mediaId == FAVORITES ||
            mediaId == TRACKS ||
            mediaId == FOLDERS ||
            mediaId == GENRES ||
            mediaId == RADIO

    fun isBrowsableCollection(mediaId: String): Boolean =
        isCategory(mediaId) ||
            albumKey(mediaId) != null ||
            artistKey(mediaId) != null ||
            playlistId(mediaId) != null ||
            folderKey(mediaId) != null ||
            genreKey(mediaId) != null

    fun isTrackMediaId(mediaId: String): Boolean =
        mediaId.isNotBlank() && !isBrowsableCollection(mediaId)

    fun browseRange(page: Int, pageSize: Int, maxPageSize: Int = MAX_PAGE_SIZE): Pair<Int, Int> {
        val size = pageSize.coerceIn(1, maxPageSize)
        val safePage = page.coerceAtLeast(0)
        return size to (safePage * size)
    }

    private fun prefixedValue(
        mediaId: String,
        prefix: String,
        allowEmpty: Boolean = false,
    ): String? {
        if (!mediaId.startsWith(prefix)) return null
        val value = mediaId.removePrefix(prefix)
        return if (allowEmpty) value else value.takeIf { it.isNotBlank() }
    }
}

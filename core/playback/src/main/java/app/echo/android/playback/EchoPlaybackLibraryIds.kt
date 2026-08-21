package app.echo.android.playback

object EchoPlaybackLibraryIds {
    const val ROOT = "echo.root"
    const val ALBUMS = "echo.albums"
    const val ARTISTS = "echo.artists"
    const val PLAYLISTS = "echo.playlists"
    const val FAVORITES = "echo.favorites"
    const val TRACKS = "echo.tracks"

    const val MAX_PAGE_SIZE = 100
    const val PLAYABLE_QUEUE_LIMIT = 200

    private const val ALBUM_PREFIX = "echo.album."
    private const val ARTIST_PREFIX = "echo.artist."
    private const val PLAYLIST_PREFIX = "echo.playlist."

    fun album(albumKey: String): String = ALBUM_PREFIX + albumKey

    fun artist(artistKey: String): String = ARTIST_PREFIX + artistKey

    fun playlist(playlistId: String): String = PLAYLIST_PREFIX + playlistId

    fun albumKey(mediaId: String): String? = prefixedValue(mediaId, ALBUM_PREFIX)

    fun artistKey(mediaId: String): String? = prefixedValue(mediaId, ARTIST_PREFIX)

    fun playlistId(mediaId: String): String? = prefixedValue(mediaId, PLAYLIST_PREFIX)

    fun isCategory(mediaId: String): Boolean =
        mediaId == ROOT ||
            mediaId == ALBUMS ||
            mediaId == ARTISTS ||
            mediaId == PLAYLISTS ||
            mediaId == FAVORITES ||
            mediaId == TRACKS

    fun isBrowsableCollection(mediaId: String): Boolean =
        isCategory(mediaId) ||
            albumKey(mediaId) != null ||
            artistKey(mediaId) != null ||
            playlistId(mediaId) != null

    fun isTrackMediaId(mediaId: String): Boolean =
        mediaId.isNotBlank() && !isBrowsableCollection(mediaId)

    fun browseRange(page: Int, pageSize: Int, maxPageSize: Int = MAX_PAGE_SIZE): Pair<Int, Int> {
        val size = pageSize.coerceIn(1, maxPageSize)
        val safePage = page.coerceAtLeast(0)
        return size to (safePage * size)
    }

    private fun prefixedValue(mediaId: String, prefix: String): String? {
        if (!mediaId.startsWith(prefix)) return null
        return mediaId.removePrefix(prefix).takeIf { it.isNotBlank() }
    }
}

package app.echo.android.model.playback

/** Platform-independent voice search hints; an empty query means resume/recent music. */
data class EchoMediaSearchRequest(
    val query: String,
    val focus: String? = null,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val playlist: String? = null,
    val genre: String? = null,
) {
    val kind: EchoMediaSearchKind get() = when (focus) {
        "vnd.android.cursor.item/playlist" -> EchoMediaSearchKind.Playlist
        "vnd.android.cursor.item/audio" -> EchoMediaSearchKind.Song
        "vnd.android.cursor.item/album" -> EchoMediaSearchKind.Album
        "vnd.android.cursor.item/artist" -> EchoMediaSearchKind.Artist
        "vnd.android.cursor.item/genre" -> EchoMediaSearchKind.Genre
        else -> when {
            playlist != null -> EchoMediaSearchKind.Playlist
            title != null -> EchoMediaSearchKind.Song
            album != null -> EchoMediaSearchKind.Album
            artist != null -> EchoMediaSearchKind.Artist
            genre != null -> EchoMediaSearchKind.Genre
            else -> EchoMediaSearchKind.Any
        }
    }
    val term: String get() = (when (kind) {
        EchoMediaSearchKind.Playlist -> playlist
        EchoMediaSearchKind.Song -> title
        EchoMediaSearchKind.Album -> album
        EchoMediaSearchKind.Artist -> artist
        EchoMediaSearchKind.Genre -> genre
        EchoMediaSearchKind.Any -> null
    } ?: query).trim().take(256)
}

enum class EchoMediaSearchKind { Any, Song, Album, Artist, Playlist, Genre }

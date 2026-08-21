package app.echo.android.model.library

sealed class LibraryPlaybackOrigin {
    data object Songs : LibraryPlaybackOrigin()
    data class Album(val albumKey: String) : LibraryPlaybackOrigin()
    data class Artist(val artistKey: String) : LibraryPlaybackOrigin()
    data class Folder(val folderKey: String) : LibraryPlaybackOrigin()
    data class Playlist(val playlistId: String) : LibraryPlaybackOrigin()
}

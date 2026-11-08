package app.echo.android.model.library

data class LibrarySource(
    val id: String,
) {
    val isLocalAudioFile: Boolean
        get() = id == MediaStore.id || id == Saf.id || id == FilePlaylist.id

    companion object {
        val MediaStore = LibrarySource("mediastore")
        val Saf = LibrarySource("saf")
        val FilePlaylist = LibrarySource("file-playlist")
        val Subsonic = LibrarySource("subsonic")
        val WebDav = LibrarySource("webdav")
        val Smb = LibrarySource("smb")
        val Jellyfin = LibrarySource("jellyfin")
        val Netease = LibrarySource("netease")
        val EchoLink = LibrarySource("echo-link")
        val Unknown = LibrarySource("unknown")
    }
}

package app.echo.android.model.library

data class LibrarySource(
    val id: String,
) {
    val isLocalAudioFile: Boolean
        get() = id == MediaStore.id || id == Saf.id

    companion object {
        val MediaStore = LibrarySource("mediastore")
        val Saf = LibrarySource("saf")
        val Subsonic = LibrarySource("subsonic")
        val WebDav = LibrarySource("webdav")
        val Jellyfin = LibrarySource("jellyfin")
        val Netease = LibrarySource("netease")
        val Unknown = LibrarySource("unknown")
    }
}

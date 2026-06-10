package app.echo.android.model.library

data class LibrarySource(
    val id: String,
) {
    companion object {
        val MediaStore = LibrarySource("mediastore")
        val Subsonic = LibrarySource("subsonic")
        val WebDav = LibrarySource("webdav")
        val Netease = LibrarySource("netease")
        val Unknown = LibrarySource("unknown")
    }
}

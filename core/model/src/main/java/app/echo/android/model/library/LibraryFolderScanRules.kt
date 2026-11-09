package app.echo.android.model.library

data class LibraryFolderScanRules(
    val watch: Boolean = true,
    val importITunes: Boolean = false,
    val importOtherPlaylists: Boolean = false,
    val ignoredPaths: String = "",
) : java.io.Serializable {
    companion object {
        const val MaxIgnoredPathsLength = 2048
    }
}

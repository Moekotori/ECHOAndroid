package app.echo.android.model.library

/** User-selected import rules, shared by the scan UI and local data layer. */
data class LibraryScanOptions(
    val minDurationMs: Long = 30_000L,
    val minSizeBytes: Long = 100L * 1024L,
    val excludeNonMusicFolders: Boolean = true,
    val excludeHiddenFolders: Boolean = true,
) : java.io.Serializable

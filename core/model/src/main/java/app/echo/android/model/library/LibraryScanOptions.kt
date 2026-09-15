package app.echo.android.model.library

/** User-selected import rules, shared by the scan UI and local data layer. */
data class LibraryScanOptions(
    val minDurationMs: Long = 30_000L,
    val minSizeBytes: Long = 100L * 1024L,
    val excludeNonMusicFolders: Boolean = true,
    val excludeHiddenFolders: Boolean = true,
    /** Storage-relative directory paths; includes descendants. Next complete scan removes matching library tracks. */
    val excludedRelativePaths: Set<String> = emptySet(),
    /** Allowed filename extensions without a dot; empty means all supported formats. */
    val allowedExtensions: Set<String> = emptySet(),
) : java.io.Serializable

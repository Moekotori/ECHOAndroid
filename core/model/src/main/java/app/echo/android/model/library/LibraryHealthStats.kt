package app.echo.android.model.library

/** A snapshot of local metadata, not a file/cover decoding audit. */
data class LibraryHealthStats(
    val trackCount: Int = 0,
    val missingCoverCount: Int = 0,
    val missingAlbumCount: Int = 0,
    val missingArtistCount: Int = 0,
    val missingGenreCount: Int = 0,
    val missingYearCount: Int = 0,
    val unknownDurationCount: Int = 0,
    val unknownSizeCount: Int = 0,
    val folderCount: Int = 0,
)

data class LibraryLyricsInspection(
    val totalCount: Int = 0,
    val foundCount: Int = 0,
    val missingCount: Int = 0,
    val unverifiedCount: Int = 0,
    val checkedAtEpochMs: Long = 0L,
    val waitingForPlayback: Boolean = false,
) {
    val checkedCount: Int get() = foundCount + missingCount + unverifiedCount
    val pendingCount: Int get() = (totalCount - checkedCount).coerceAtLeast(0)
}

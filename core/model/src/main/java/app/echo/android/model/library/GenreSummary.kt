package app.echo.android.model.library

data class GenreSummary(
    val genreKey: String,
    val name: String,
    val artworkUri: String?,
    val albumCount: Int,
    val trackCount: Int,
    val durationMs: Long,
)

enum class GenreSortMode {
    Name,
    AlbumCount,
    TrackCount,
    Duration,
}

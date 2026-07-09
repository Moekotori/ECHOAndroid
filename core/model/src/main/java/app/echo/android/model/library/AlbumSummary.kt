package app.echo.android.model.library

data class AlbumSummary(
    val albumKey: String,
    val title: String,
    val albumArtist: String?,
    val artist: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val durationMs: Long,
    val year: Int?,
    val addedAtSeconds: Long = 0L,
)

enum class AlbumSortMode {
    Title,
    Artist,
    Duration,
}

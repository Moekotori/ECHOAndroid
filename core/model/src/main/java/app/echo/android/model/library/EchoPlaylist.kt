package app.echo.android.model.library

data class EchoPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val trackCount: Int = trackIds.size,
    val artworkUri: String? = null,
    val updatedAtEpochMs: Long = 0L,
)

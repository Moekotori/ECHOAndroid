package app.echo.android.model.connect

data class EchoRemoteTrack(
    val id: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val sourceLabel: String? = null,
    val canPlayOnPhone: Boolean = true,
)

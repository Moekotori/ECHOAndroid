package app.echo.android.model.connect

data class EchoRemotePlaylist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val sourceLabel: String? = null,
    val tracks: List<EchoRemoteTrack> = emptyList(),
)

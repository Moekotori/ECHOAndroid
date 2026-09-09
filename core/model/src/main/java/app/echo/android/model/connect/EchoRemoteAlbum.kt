package app.echo.android.model.connect

data class EchoRemoteAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtist: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val durationMs: Long = 0,
    val year: Int? = null,
    val tracks: List<EchoRemoteTrack> = emptyList(),
)

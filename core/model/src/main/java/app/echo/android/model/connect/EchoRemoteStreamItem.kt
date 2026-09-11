package app.echo.android.model.connect

data class EchoRemoteStreamItem(
    val id: String,
    val streamUrl: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val audio: EchoRemoteAudioFormat? = null,
) {
    fun toRemoteTrack(): EchoRemoteTrack = EchoRemoteTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        sourceLabel = "Phone",
        canPlayOnPhone = false,
    )
}

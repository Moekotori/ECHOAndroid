package app.echo.android.model.connect

data class EchoRemotePlaybackQueue(
    val currentTrackId: String? = null,
    val items: List<EchoRemoteTrack> = emptyList(),
)

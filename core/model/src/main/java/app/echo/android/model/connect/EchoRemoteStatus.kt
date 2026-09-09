package app.echo.android.model.connect

data class EchoRemoteStatus(
    val connectionState: EchoRemoteConnectionState = EchoRemoteConnectionState.Disconnected,
    val endpoint: EchoRemoteEndpoint? = null,
    val playback: EchoRemotePlaybackSnapshot = EchoRemotePlaybackSnapshot(),
    val error: String? = null,
)

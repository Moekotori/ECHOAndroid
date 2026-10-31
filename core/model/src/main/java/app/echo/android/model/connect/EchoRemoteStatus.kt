package app.echo.android.model.connect

data class EchoRemoteStatus(
    val connectionState: EchoRemoteConnectionState = EchoRemoteConnectionState.Disconnected,
    val endpoint: EchoRemoteEndpoint? = null,
    /** UI projection while controlPending is true; the connection session retains confirmed state separately. */
    val playback: EchoRemotePlaybackSnapshot = EchoRemotePlaybackSnapshot(),
    val error: String? = null,
    val controlPending: Boolean = false,
    /** Distinguishes reconnects to the same PC so file-transfer sessions cannot outlive a connection. */
    val connectionId: Long = 0L,
)

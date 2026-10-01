package app.echo.android.model.connect

sealed interface EchoRemoteMessage {
    data class StatusSnapshot(
        val payload: EchoRemotePlaybackSnapshot,
        val queueIncluded: Boolean = true,
        val trackArtworkIncluded: Boolean = true,
        val volumeControlIncluded: Boolean = true,
        val outputIncluded: Boolean = true,
        val playbackOrderIncluded: Boolean = true,
    ) : EchoRemoteMessage
    data class Command(val payload: EchoRemoteCommand) : EchoRemoteMessage
    data class Error(val code: String, val message: String) : EchoRemoteMessage
    data object Ping : EchoRemoteMessage
    data object Pong : EchoRemoteMessage
}

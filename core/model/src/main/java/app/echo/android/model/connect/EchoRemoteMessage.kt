package app.echo.android.model.connect

sealed interface EchoRemoteMessage {
    data class StatusSnapshot(val payload: EchoRemotePlaybackSnapshot) : EchoRemoteMessage
    data class Command(val payload: EchoRemoteCommand) : EchoRemoteMessage
    data class Error(val code: String, val message: String) : EchoRemoteMessage
    data object Ping : EchoRemoteMessage
    data object Pong : EchoRemoteMessage
}

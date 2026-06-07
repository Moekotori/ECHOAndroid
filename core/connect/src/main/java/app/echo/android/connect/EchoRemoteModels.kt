package app.echo.android.connect

enum class EchoRemoteConnectionState {
    Disconnected,
    Pairing,
    Connecting,
    Connected,
    Reconnecting,
    Error,
}

enum class EchoRemotePlaybackState {
    Idle,
    Loading,
    Playing,
    Paused,
    Stopped,
    Error,
}

data class EchoRemoteEndpoint(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
)

data class EchoRemoteTrack(
    val id: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
)

data class EchoRemotePlaybackSnapshot(
    val state: EchoRemotePlaybackState = EchoRemotePlaybackState.Idle,
    val track: EchoRemoteTrack? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val outputMode: String = "system",
    val updatedAtEpochMs: Long = 0L,
)

data class EchoRemoteStatus(
    val connectionState: EchoRemoteConnectionState = EchoRemoteConnectionState.Disconnected,
    val endpoint: EchoRemoteEndpoint? = null,
    val playback: EchoRemotePlaybackSnapshot = EchoRemotePlaybackSnapshot(),
    val error: String? = null,
)

sealed interface EchoRemoteCommand {
    data object PlayPause : EchoRemoteCommand
    data object Next : EchoRemoteCommand
    data object Previous : EchoRemoteCommand
    data object Stop : EchoRemoteCommand
    data class SeekTo(val positionMs: Long) : EchoRemoteCommand
    data class SetVolume(val volume: Float) : EchoRemoteCommand
}

sealed interface EchoRemoteMessage {
    data class StatusSnapshot(val payload: EchoRemotePlaybackSnapshot) : EchoRemoteMessage
    data class Command(val payload: EchoRemoteCommand) : EchoRemoteMessage
    data class Error(val code: String, val message: String) : EchoRemoteMessage
    data object Ping : EchoRemoteMessage
    data object Pong : EchoRemoteMessage
}

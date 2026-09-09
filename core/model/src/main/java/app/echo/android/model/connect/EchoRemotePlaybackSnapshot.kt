package app.echo.android.model.connect

data class EchoRemotePlaybackSnapshot(
    val state: EchoRemotePlaybackState = EchoRemotePlaybackState.Idle,
    val track: EchoRemoteTrack? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val outputMode: String = "system",
    val updatedAtEpochMs: Long = 0L,
    val queue: EchoRemotePlaybackQueue = EchoRemotePlaybackQueue(),
)

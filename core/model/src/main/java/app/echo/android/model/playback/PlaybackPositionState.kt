package app.echo.android.model.playback

data class PlaybackPositionState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val updateTimeEpochMs: Long = 0L,
)

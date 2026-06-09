package app.echo.android.model.playback

data class PlaybackMetadataState(
    val track: EchoTrackRef? = null,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val mediaId: String? = null,
)

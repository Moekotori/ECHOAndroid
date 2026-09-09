package app.echo.android.model.playback

data class EchoTrackRef(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val sampleRateHz: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val sourceId: String? = null,
)

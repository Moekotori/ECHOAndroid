package app.echo.android.model.playback

/** One listening event. Metadata survives removal of the original library track. */
data class PlaybackHistoryEntry(
    val id: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUri: String?,
    val playedAtEpochMs: Long,
    val listenedMs: Long,
    val canReplay: Boolean,
)

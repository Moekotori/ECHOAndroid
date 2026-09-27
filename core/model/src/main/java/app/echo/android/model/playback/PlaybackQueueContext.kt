package app.echo.android.model.playback

/** Identity belongs to this occurrence in a queue, not to the underlying song. */
data class PlaybackQueueContext(
    val entryId: String,
    val nextUp: Boolean = false,
    val source: String? = null,
)

package app.echo.android.model.playback

/**
 * One listening event, or several of them when the list is grouped.
 * Metadata survives removal of the original library track.
 *
 * For grouped rows [id] is the most recent event of the group, [playedAtEpochMs] its time,
 * [listenedMs] the sum, and [plays] the number of events. For albums and artists [title]
 * carries the album or artist name.
 */
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
    val plays: Int = 1,
)

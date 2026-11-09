package app.echo.android.model.playback

/** Scheduler state belongs to the current queue occurrence, including duplicate songs. */
data class PlaybackShuffleResume(
    val entryId: String,
    val pureRemaining: Int? = null,
    val recentTrackIds: List<String> = emptyList(),
    val previousEntryIds: List<String> = emptyList(),
) {
    companion object { const val HistoryLimit = 100 }
}

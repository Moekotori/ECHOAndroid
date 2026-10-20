package app.echo.android.model.connect

/** Portable listening metadata. Paths, audio, tokens and lyric documents never cross this boundary. */
data class EchoPersonalListen(
    val track: EchoSyncTrackRef,
    val listenedMs: Long,
    val playedAtEpochMs: Long,
    val localEpochDay: Long,
    val localHour: Int,
)
data class EchoPersonalBookmark(val track: EchoSyncTrackRef, val positionMs: Long, val label: String, val endPositionMs: Long? = null)
data class EchoPersonalLyricsOffset(val track: EchoSyncTrackRef, val offsetMs: Long)
data class EchoPersonalResume(val track: EchoSyncTrackRef, val positionMs: Long)
data class EchoPersonalState(
    val history: List<EchoPersonalListen> = emptyList(),
    val bookmarks: List<EchoPersonalBookmark> = emptyList(),
    val lyricsOffsets: List<EchoPersonalLyricsOffset> = emptyList(),
    val resume: EchoPersonalResume? = null,
    val revision: String = "",
)
data class EchoPersonalStatePreview(val phone: EchoPersonalState, val pc: EchoPersonalState)
data class EchoPersonalStateResult(val historyAdded: Int, val bookmarksApplied: Int, val offsetsApplied: Int, val missing: Int, val unsupportedBookmarks: Int = 0)
object EchoPersonalStateLimits {
    const val History = 1000
    const val Bookmarks = 500
    const val Offsets = 500
    const val MaxBytes = 1024 * 1024
    const val MaxDurationMs = 24L * 60 * 60 * 1000
}

object EchoPersonalStatePolicy {
    fun validTrack(track: EchoSyncTrackRef): Boolean = track.title.isNotBlank() && track.title.length <= 500 &&
        track.artist.length <= 500 && (track.album?.length ?: 0) <= 500 && track.durationMs in 0..EchoPersonalStateLimits.MaxDurationMs

    /** Invalid metadata is skipped rather than changing a song's identity by truncation. */
    fun portable(state: EchoPersonalState): EchoPersonalState = state.copy(
        history = state.history.filter { validTrack(it.track) && it.listenedMs in 0..EchoPersonalStateLimits.MaxDurationMs &&
            it.playedAtEpochMs in 0..4102444800000L && it.localEpochDay in -365000L..365000L && it.localHour in 0..23 }.take(EchoPersonalStateLimits.History),
        bookmarks = state.bookmarks.filter { validTrack(it.track) && it.label.length <= 500 &&
            it.positionMs in 0..EchoPersonalStateLimits.MaxDurationMs && (it.endPositionMs == null ||
            it.endPositionMs > it.positionMs && it.endPositionMs <= EchoPersonalStateLimits.MaxDurationMs) }.take(EchoPersonalStateLimits.Bookmarks),
        lyricsOffsets = state.lyricsOffsets.filter { validTrack(it.track) && it.offsetMs in -30000L..30000L }.take(EchoPersonalStateLimits.Offsets),
        resume = state.resume?.takeIf { validTrack(it.track) && it.positionMs in 0..EchoPersonalStateLimits.MaxDurationMs },
    )
}

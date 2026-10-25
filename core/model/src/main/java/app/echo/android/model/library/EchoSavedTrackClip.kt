package app.echo.android.model.library

/** Positions are relative to this library track, including CUE tracks. No audio is copied. */
data class EchoSavedTrackClip(val id: String, val trackId: String, val label: String, val startMs: Long, val endMs: Long) {
    fun isPlayable(durationMs: Long) = durationMs > 0 && startMs >= 0 && endMs >= startMs &&
        endMs - startMs >= 500 && endMs <= durationMs
}

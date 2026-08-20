package app.echo.android.lyrics

object LyricsApplyPolicy {
    fun shouldApplyLyricsResult(loadedTrackId: String?, currentTrackId: String?): Boolean =
        loadedTrackId != null && loadedTrackId == currentTrackId
}

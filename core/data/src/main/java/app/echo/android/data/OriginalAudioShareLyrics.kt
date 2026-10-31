package app.echo.android.data

/** A universally readable sidecar plus an optional complete ECHO document. */
data class OriginalAudioShareLyrics(val text: String, val extension: String, val completeJson: String? = null)

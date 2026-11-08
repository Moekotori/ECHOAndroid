package app.echo.android.plugin

/** Metadata only. No library IDs, file paths, URLs, tokens, or playback state. */
data class PluginLyricsRequest(val title: String, val artist: String, val album: String, val durationMs: Long)

data class PluginLyricsCandidate(
    val id: String, val providerName: String, val title: String, val artist: String,
    val album: String, val durationMs: Long, val text: String,
)

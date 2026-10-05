package app.echo.android.model.settings

data class EchoWidgetOptions(
    val showArtwork: Boolean = true,
    val showLyrics: Boolean = true,
    val fullControls: Boolean = true,
    val backgroundOpacity: Float = 1f,
    val playlistMediaId: String? = null,
    val playlistName: String? = null,
) {
    fun normalized(): EchoWidgetOptions {
        val id = playlistMediaId?.takeIf { it.startsWith("echo.playlist.") && it.length in 15..2048 }
        return copy(
        backgroundOpacity = if (backgroundOpacity.isFinite()) backgroundOpacity.coerceIn(0f, 1f) else 1f,
        playlistMediaId = id,
        playlistName = playlistName?.trim()?.take(100)?.takeIf { id != null },
    )
    }
}

data class EchoWidgetPlaylistOption(val mediaId: String, val name: String)

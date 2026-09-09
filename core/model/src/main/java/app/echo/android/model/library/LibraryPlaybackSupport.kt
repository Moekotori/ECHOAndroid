package app.echo.android.model.library

object LibraryPlaybackSupport {
    fun isPlayableOnPhone(mimeType: String?, uriOrFileName: String? = null): Boolean {
        val haystack = listOfNotNull(mimeType, uriOrFileName)
            .joinToString(" ")
            .lowercase()
        if (haystack.isBlank()) return true
        return DsdTokens.none { it in haystack }
    }

    private val DsdTokens = listOf(
        "audio/dsf",
        "audio/dff",
        "audio/dsd",
        "audio/x-dsf",
        "audio/x-dff",
        "audio/x-dsd",
        ".dsf",
        ".dff",
        ".dsd",
    )
}

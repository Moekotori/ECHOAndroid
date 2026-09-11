package app.echo.android.model.library

object LibraryPlaybackSupport {
    fun isPlayableOnPhone(mimeType: String?, uriOrFileName: String? = null): Boolean = true

    fun isDsd(mimeType: String?, uriOrFileName: String? = null): Boolean {
        val haystack = listOfNotNull(mimeType, uriOrFileName)
            .joinToString(" ")
            .lowercase()
        if (haystack.isBlank()) return false
        return DsdTokens.any { it in haystack }
    }

    private val DsdTokens = listOf(
        "audio/dsf",
        "audio/dff",
        "audio/dsd",
        "audio/x-dsf",
        "audio/x-dff",
        "audio/x-dsd",
        "audio/x-dsd-lsbf",
        "audio/x-dsd-msbf",
        ".dsf",
        ".dff",
        ".dsd",
    )
}

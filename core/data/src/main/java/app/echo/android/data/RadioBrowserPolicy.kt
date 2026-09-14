package app.echo.android.data

object RadioBrowserPolicy {
    val Servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )
    const val MaxServerAttempts = 2
    const val MaxResults = 25
    const val MaxQueryLength = 80
    const val MaxBodyBytes = 512_000L
    const val DebounceMs = 350L
    const val SearchPath = "json/stations/search"

    fun normalizedQuery(raw: String): String? {
        val trimmed = raw.trim().replace(Whitespace, " ")
        if (trimmed.isEmpty()) return null
        val clipped = trimmed.take(MaxQueryLength)
        if (clipped.length >= 2) return clipped
        return clipped.takeIf { it.any(::isCjk) }
    }

    private fun isCjk(ch: Char): Boolean =
        ch in '\u3400'..'\u4DBF' ||
            ch in '\u4E00'..'\u9FFF' ||
            ch in '\uF900'..'\uFAFF'

    private val Whitespace = Regex("\\s+")
}

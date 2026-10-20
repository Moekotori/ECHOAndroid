package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics

object EchoLrcFormatter {
    /** Do not overwrite audio tags with a representation that loses imported lyric information. */
    fun formatForEmbeddedWriteOrNull(lyrics: EchoLyrics): String? {
        if (lyrics.lines.none { it.text.isNotBlank() }) return null
        val text = format(lyrics)
        val restored = runCatching { EchoLyricsParser.parse(text) }.getOrNull() ?: return null
        if (restored.lines.size != lyrics.lines.size) return null
        val lossless = lyrics.lines.zip(restored.lines).all { (original, parsed) ->
            // Missing ends are inferred on read; explicit ends must survive unchanged.
            original.copy(endMs = null) == parsed.copy(endMs = null) &&
                (original.endMs == null || original.endMs == parsed.endMs)
        }
        return text.takeIf { lossless }
    }

    fun format(lyrics: EchoLyrics): String {
        val lines = lyrics.lines.filter { it.text.isNotBlank() || it.startMs >= 0L }
        if (lines.isEmpty()) return ""
        val synced = lines.any { it.startMs >= 0L }
        return lines.flatMap { line ->
            val body = line.text.asSingleLyricLine().trim()
            val prefix = if (synced && line.startMs >= 0L) "[${formatTimestamp(line.startMs)}]" else ""
            listOfNotNull(prefix + body, line.translation?.asSingleLyricLine()?.trim()?.takeIf { it.isNotEmpty() }?.let { prefix + it })
        }.joinToString("\n")
    }

    private fun formatTimestamp(timeMs: Long): String {
        val safeMs = timeMs.coerceAtLeast(0L)
        val minutes = safeMs / 60_000L
        val seconds = (safeMs % 60_000L) / 1_000L
        val hundredths = (safeMs % 1_000L) / 10L
        return "%02d:%02d.%02d".format(java.util.Locale.ROOT, minutes, seconds, hundredths)
    }
}

internal fun String.asSingleLyricLine(): String =
    replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ')

package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics

object EchoLrcFormatter {
    fun format(lyrics: EchoLyrics): String {
        val lines = lyrics.lines.filter { it.text.isNotBlank() || it.startMs >= 0L }
        if (lines.isEmpty()) return ""
        val synced = lines.any { it.startMs >= 0L }
        return lines.joinToString("\n") { line ->
            val body = line.text.trim()
            if (synced && line.startMs >= 0L) "[${formatTimestamp(line.startMs)}]$body" else body
        }
    }

    private fun formatTimestamp(timeMs: Long): String {
        val safeMs = timeMs.coerceAtLeast(0L)
        val minutes = safeMs / 60_000L
        val seconds = (safeMs % 60_000L) / 1_000L
        val hundredths = (safeMs % 1_000L) / 10L
        return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}

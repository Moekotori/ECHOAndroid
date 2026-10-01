package app.echo.android.lyrics

import app.echo.android.model.lyrics.*

object EchoLyricsEditing {
    fun timestamp(ms: Long): String = if (ms < 0) "" else "%02d:%02d.%03d".format(java.util.Locale.ROOT, ms / 60000, ms / 1000 % 60, ms % 1000)
    fun parseTimestamp(text: String): Long? {
        if (text.isBlank()) return -1
        val match = Regex("^(\\d{1,4}):([0-5]\\d)(?:[.,](\\d{1,3}))?$").matchEntire(text.trim()) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val fraction = match.groupValues[3].padEnd(3, '0').ifEmpty { "000" }.toLong()
        return (minutes * 60000 + match.groupValues[2].toLong() * 1000 + fraction).takeIf { it <= 86400000 }
    }
    fun finish(original: EchoLyrics, lines: List<EchoLyricLine>): EchoLyrics {
        require(lines.size in 1..2000 && lines.sumOf { it.text.length + it.translation.orEmpty().length } <= 100000)
        var previous = -1L
        lines.forEach { line -> if (line.startMs >= 0) { require(line.startMs >= previous); previous = line.startMs } }
        val fixed = lines.mapIndexed { index, line ->
            val next = lines.getOrNull(index + 1)?.startMs?.takeIf { it >= line.startMs && line.startMs >= 0 }
            line.copy(endMs = next, words = line.words.takeIf { line.startMs >= 0 }.orEmpty())
        }
        return original.copy(lines = fixed, offsetMs = 0, sourceLabel = "ECHO", metadata = original.metadata - "user_offset_ms")
    }
    fun toLrc(lyrics: EchoLyrics): String {
        require(lyrics.lines.isNotEmpty() && lyrics.lines.all { it.startMs >= 0 })
        return buildString {
            append("[re:ECHO]\n")
            for (line in lyrics.lines) {
                append('[').append(timestamp(line.startMs)).append(']').append(line.text.replace('\n', ' ')).append('\n')
                line.translation?.takeIf { it.isNotBlank() }?.let { append('[').append(timestamp(line.startMs)).append(']').append(it.replace('\n',' ')).append('\n') }
            }
        }
    }
}

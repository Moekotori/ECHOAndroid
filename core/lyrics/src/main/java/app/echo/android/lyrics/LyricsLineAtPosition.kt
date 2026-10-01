package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics

/** Line lookup for synced lyrics. Overlapping duet sets stay in the player timeline. */
object LyricsLineAtPosition {
    fun lastStartedIndex(lines: List<EchoLyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun primaryLine(lines: List<EchoLyricLine>, positionMs: Long): EchoLyricLine? {
        val index = lastStartedIndex(lines, positionMs)
        if (index < 0) return null
        val line = lines[index]
        if (line.startMs < 0L || line.text.isBlank() || line.endMs?.let { positionMs >= it } == true) return null
        return line
    }

    fun primaryText(lyrics: EchoLyrics, positionMs: Long): String? =
        primaryLine(lyrics.lines, positionMs)?.text?.trim()?.takeIf { it.isNotEmpty() }

    fun nextStartMs(lines: List<EchoLyricLine>, positionMs: Long): Long? {
        val index = lastStartedIndex(lines, positionMs)
        val next = index + 1
        if (next in lines.indices) return lines[next].startMs.takeIf { it >= 0L }
        return null
    }

    fun notificationLines(lyrics: EchoLyrics): List<Pair<Long, String>> {
        if (!lyrics.isSynced) return emptyList()
        return buildList {
            lyrics.lines.forEachIndexed { index, line ->
                if (line.startMs < 0L) return@forEachIndexed
                add(line.startMs to line.text.trim())
                val end = line.endMs
                val nextStart = lyrics.lines.getOrNull(index + 1)?.startMs
                // Keep silence boundaries so service scheduling clears the last sung line.
                if (end != null && end > line.startMs && (nextStart == null || end < nextStart)) {
                    add(end to "")
                }
            }
        }
    }
}

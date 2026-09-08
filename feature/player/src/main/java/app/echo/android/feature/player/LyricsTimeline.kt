package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine

/** Built once per document; prefix ends bound overlap lookup for duet/backing-vocal lines. */
internal class LyricsTimeline(private val lines: List<EchoLyricLine>) {
    private val ends = LongArray(lines.size).also { values ->
        var nextStart = Long.MAX_VALUE
        for (i in lines.indices.reversed()) {
            if (i < lines.lastIndex && lines[i + 1].startMs > lines[i].startMs) nextStart = lines[i + 1].startMs
            values[i] = lines[i].endMs ?: nextStart
        }
    }
    private val prefixEnds = LongArray(lines.size).also { values ->
        var maximum = Long.MIN_VALUE
        ends.forEachIndexed { index, end -> maximum = maxOf(maximum, end); values[index] = maximum }
    }
    fun lastStarted(positionMs: Long): Int {
        var low = 0; var high = lines.lastIndex; var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMs <= positionMs) { result = mid; low = mid + 1 } else high = mid - 1
        }
        return result
    }
    fun activeAt(positionMs: Long): Set<Int> {
        val last = lastStarted(positionMs)
        if (last < 0) return emptySet()
        val active = mutableSetOf<Int>()
        var i = last
        while (i >= 0 && prefixEnds[i] > positionMs) {
            if (lines[i].startMs >= 0 && ends[i] > positionMs && lines[i].text.isNotBlank()) active += i
            i--
        }
        return active
    }
    fun nextStart(positionMs: Long): Long? = lines.getOrNull(lastStarted(positionMs) + 1)?.startMs
}

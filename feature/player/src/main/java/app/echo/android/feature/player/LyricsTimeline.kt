package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine

/** Built once per document; prefix ends bound overlap lookup for duet/backing-vocal lines. */
internal class LyricsTimeline(private val lines: List<EchoLyricLine>) {
    private var activeFrom = Long.MAX_VALUE
    private var activeUntil = Long.MIN_VALUE
    private var cachedActive: Set<Int> = emptySet()
    private val previousContent = IntArray(lines.size).also { values ->
        var previous = -1
        lines.forEachIndexed { index, line ->
            if (line.startMs >= 0 && line.text.isNotBlank()) previous = index
            values[index] = previous
        }
    }
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
        if (positionMs >= activeFrom && positionMs < activeUntil) return cachedActive
        val last = lastStarted(positionMs)
        val active = mutableSetOf<Int>()
        var until = lines.getOrNull(last + 1)?.startMs ?: Long.MAX_VALUE
        var i = last
        while (i >= 0 && prefixEnds[i] > positionMs) {
            if (lines[i].startMs >= 0 && ends[i] > positionMs && lines[i].text.isNotBlank()) {
                active += i
                until = minOf(until, ends[i])
            }
            i--
        }
        activeFrom = positionMs
        activeUntil = until
        cachedActive = active.ifEmpty { emptySet() }
        return cachedActive
    }
    fun nextStart(positionMs: Long): Long? = lines.getOrNull(lastStarted(positionMs) + 1)?.startMs

    // An instrumental gap ends singing, but should not reset the surrounding visual context.
    fun contextAt(positionMs: Long): Int = previousContent.getOrElse(lastStarted(positionMs)) { -1 }

    fun endAt(index: Int): Long? = ends.getOrNull(index)?.takeUnless { it == Long.MAX_VALUE }
}

package app.echo.android.feature.player.afterglow

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import java.text.BreakIterator
import java.util.Locale

/** Only timing and source references are held for the document; glyph layouts are a one-line window. */
internal class AfterglowPlan private constructor(val lines: List<EchoLyricLine>, private val ends: LongArray) {
    fun indexAt(positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMs <= positionMs) { result = mid; low = mid + 1 } else high = mid - 1
        }
        return result
    }

    fun endAt(index: Int): Long = ends[index]

    companion object {
        // Oversized / unsupported documents use the ordinary lyric view without discarding content.
        fun build(lyrics: EchoLyrics, durationMs: Long, checkCancelled: () -> Unit = {}): AfterglowPlan? {
            if (lyrics.lines.size > 4096) return null
            val lines = ArrayList<EchoLyricLine>()
            val markerStarts = LongArray(lyrics.lines.size)
            var markerCount = 0
            lyrics.lines.forEachIndexed { index, line ->
                if (index % 128 == 0) checkCancelled()
                if (line.text.length > 1024 || line.words.size > 256 ||
                    (line.translation?.length ?: 0) > 1024 || (line.romanization?.length ?: 0) > 1024) return null
                if (line.startMs >= 0) {
                    markerStarts[markerCount++] = line.startMs
                    if (line.text.isNotBlank()) lines += line
                }
            }
            if (lines.isEmpty()) return null
            lines.sortBy { it.startMs }
            markerStarts.sort(0, markerCount)
            val cuts = ArrayList<EchoLyricLine>(lines.size)
            lines.forEachIndexed { index, line ->
                if (index % 128 == 0) checkCancelled()
                val previous = cuts.lastOrNull()
                if (previous == null || previous.startMs != line.startMs) cuts += line
                else {
                    val previousEnd = previous.endMs
                    val lineEnd = line.endMs
                    // Duet / backing lines with the same timestamp belong to the same composition.
                    val combined = previous.copy(
                        text = previous.text + "\n" + line.text,
                        translation = listOfNotNull(previous.translation, line.translation).joinToString("\n").ifEmpty { null },
                        romanization = listOfNotNull(previous.romanization, line.romanization).joinToString("\n").ifEmpty { null },
                        endMs = if (previousEnd == null || lineEnd == null) null else maxOf(previousEnd, lineEnd),
                        words = if (previous.words.isEmpty() || line.words.isEmpty()) emptyList() else previous.words + line.words,
                    )
                    if (combined.text.length > 1024 || combined.words.size > 256 ||
                        (combined.translation?.length ?: 0) > 1024 || (combined.romanization?.length ?: 0) > 1024) return null
                    cuts[cuts.lastIndex] = combined
                }
            }
            val ends = LongArray(cuts.size)
            for (i in cuts.indices) {
                // Blank timed markers end singing even though they do not replace visible context.
                var low = 0
                var high = markerCount
                while (low < high) {
                    val mid = (low + high) ushr 1
                    if (markerStarts[mid] <= cuts[i].startMs) low = mid + 1 else high = mid
                }
                val nextStart = if (low < markerCount) markerStarts[low] else maxOf(durationMs, cuts[i].startMs + 4000L)
                ends[i] = (cuts[i].endMs ?: nextStart).coerceAtLeast(cuts[i].startMs + 1L)
            }
            return AfterglowPlan(cuts, ends)
        }
    }
}

internal data class AfterglowBudget(val framesPerSecond: Int, val particles: Int, val glyphMotion: Boolean, val rasterMaxSide: Int) {
    companion object {
        fun forMode(mode: EchoEffectivePerformanceMode): AfterglowBudget = when (mode) {
            EchoEffectivePerformanceMode.Lightweight -> AfterglowBudget(0, 0, false, 360)
            EchoEffectivePerformanceMode.Balanced -> AfterglowBudget(30, 12, true, 540)
            EchoEffectivePerformanceMode.HighPerformance -> AfterglowBudget(60, 24, true, 720)
        }
    }
}

/** A seek/pause/rate change always anchors to the host; extrapolation cannot run away during buffering. */
internal class AfterglowClock {
    private var previous = Long.MIN_VALUE
    private var anchorMs = 0L
    fun position(hostMs: Long, nowMs: Long, playing: Boolean, speed: Float): Long {
        if (hostMs != previous || !playing) { previous = hostMs; anchorMs = nowMs }
        val rate = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val advance = if (playing) (nowMs - anchorMs).coerceIn(0L, 650L) * rate else 0f
        return (hostMs + advance.toLong()).coerceAtLeast(0L)
    }
}

/** Gate state publication at vsync; a second timer must not skip the intended drawing deadline. */
internal class AfterglowFrameGate(fps: Int) {
    private val intervalNs = 1_000_000_000L / fps.coerceIn(1, 60)
    private var lastNs = Long.MIN_VALUE
    fun accept(frameNs: Long): Boolean {
        val last = lastNs
        // Allow sub-millisecond timestamp rounding, not an extra frame at a higher display rate.
        if (last != Long.MIN_VALUE && frameNs - last < intervalNs - 500_000L) return false
        lastNs = frameNs
        return true
    }
}

/** Deterministic cuts make seeking reproduce the composition instead of consuming random state. */
internal fun afterglowComposition(index: Int, seed: Int): Int {
    var value = index xor seed
    value = (value xor (value ushr 16)) * 0x45d9f3b
    value = (value xor (value ushr 16)) * 0x45d9f3b
    return (value xor (value ushr 16)) and Int.MAX_VALUE
}

internal data class AfterglowGlyph(
    val first: Int,
    val end: Int,
    val startMs: Long?,
    val endMs: Long?,
)

/** Preserve grapheme clusters, and use literal word ranges only when they match the line exactly. */
internal fun afterglowGlyphs(line: EchoLyricLine, endMs: Long): List<AfterglowGlyph> {
    val text = line.text
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val ranges = ArrayList<IntRange>()
    var cursor = 0
    var wordTimingValid = line.words.isNotEmpty()
    for (word in line.words) {
        val start = text.indexOf(word.text, cursor)
        if (word.text.isEmpty() || start < 0 || text.substring(cursor, start).any { !it.isWhitespace() }) {
            wordTimingValid = false
            break
        }
        cursor = start + word.text.length
        ranges += start until cursor
    }
    if (text.substring(cursor).any { !it.isWhitespace() }) wordTimingValid = false
    val result = ArrayList<AfterglowGlyph>()
    var start = boundaries.first()
    var end = boundaries.next()
    var wordIndex = 0
    while (end != BreakIterator.DONE) {
        if (text.substring(start, end).any { !it.isWhitespace() }) {
            while (wordIndex < ranges.size && start > ranges[wordIndex].last) wordIndex++
            val range = ranges.getOrNull(wordIndex)
            val word = if (wordTimingValid && range != null && start >= range.first && end - 1 <= range.last) line.words[wordIndex] else null
            result += AfterglowGlyph(start, end, word?.startMs,
                word?.endMs ?: if (word != null) line.words.getOrNull(wordIndex + 1)?.startMs ?: endMs else null)
        }
        start = end
        end = boundaries.next()
    }
    // Spread a multi-character word's wipe over its complete graphemes, without changing its timing.
    var groupStart = 0
    while (groupStart < result.size) {
        val first = result[groupStart]
        if (first.startMs == null || first.endMs == null) { groupStart++; continue }
        var groupEnd = groupStart + 1
        while (groupEnd < result.size && result[groupEnd].startMs == first.startMs && result[groupEnd].endMs == first.endMs) groupEnd++
        val count = groupEnd - groupStart
        val duration = (first.endMs - first.startMs).coerceAtLeast(0L)
        for (i in groupStart until groupEnd) {
            val glyph = result[i]
            result[i] = glyph.copy(startMs = first.startMs + duration * (i - groupStart) / count,
                endMs = first.startMs + duration * (i - groupStart + 1) / count)
        }
        groupStart = groupEnd
    }
    return result
}

internal fun afterglowFraction(now: Long, start: Long, end: Long): Float =
    if (end <= start) { if (now >= start) 1f else 0f }
    else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)

package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat
import app.echo.android.lyrics.EchoLyricsParser.withLineEnds

/** SPL: square/angle word timestamps, explicit ends, repeated starts, and multiline translations. */
internal object EchoSplParser {
    private val time = Regex("""[\[<](\d{1,3}:\d{1,2}(?:\.\d{1,6})?)[\]>]""")
    private val metadataTag = Regex("""\[([A-Za-z]+):([^]]*)]""")

    fun parse(text: String, source: String?): EchoLyrics {
        val metadata = linkedMapOf<String, String>()
        val lines = mutableListOf<EchoLyricLine>()
        val endMarkers = mutableListOf<Long>()
        var previousStarts: List<Long> = emptyList()
        text.lineSequence().map(String::trim).filter(String::isNotBlank).forEach { raw ->
            metadataTag.matchEntire(raw)?.let {
                metadata[it.groupValues[1].lowercase()] = it.groupValues[2].trim()
                return@forEach
            }
            val tags = time.findAll(raw).toList()
            if (tags.isEmpty() || tags.first().range.first != 0) {
                if (previousStarts.isNotEmpty()) previousStarts.forEach { start ->
                    lines += EchoLyricLine(startMs = start, text = raw)
                }
                return@forEach
            }
            val starts = tags.takeWhile { tag ->
                tag.value.startsWith('[') && raw.substring(0, tag.range.first).replace(time, "").isEmpty()
            }.map { EchoLyricsParser.parseClockMs(it.groupValues[1]) }
            if (starts.isEmpty()) return@forEach
            val body = raw.replace(time, "")
            if (body.isBlank()) {
                endMarkers += starts
                previousStarts = emptyList()
                return@forEach
            }
            val trailing = tags.last().takeIf { it.range.last == raw.lastIndex }
                ?.let { EchoLyricsParser.parseClockMs(it.groupValues[1]) }
            val hasWordTags = tags.size > starts.size + (if (trailing != null) 1 else 0) || trailing != null
            starts.forEach { start ->
                val end = trailing?.takeIf { it >= start }
                var wordStart = start
                var cursor = tags[starts.lastIndex].range.last + 1
                val words = mutableListOf<EchoLyricWord>()
                tags.drop(starts.size).forEach wordTag@{ tag ->
                    val stamp = EchoLyricsParser.parseClockMs(tag.groupValues[1])
                    if (stamp < wordStart || end != null && stamp > end) return@wordTag
                    val segment = raw.substring(cursor, tag.range.first).replace(time, "")
                    if (segment.isNotEmpty()) words += EchoLyricWord(wordStart, stamp, segment)
                    wordStart = stamp
                    cursor = tag.range.last + 1
                }
                val tail = raw.substring(cursor).replace(time, "")
                if (tail.isNotEmpty()) words += EchoLyricWord(wordStart, end, tail)
                lines += EchoLyricLine(startMs = start, endMs = end, text = body.trim(),
                    words = if (hasWordTags) words else emptyList())
            }
            previousStarts = starts
        }
        val markers = endMarkers.sorted()
        val starts = lines.map { it.startMs }.distinct().sorted()
        val grouped = lines.groupBy { it.startMs }.map { (_, sameTime) ->
            val main = sameTime.first()
            val markerIndex = markers.binarySearch(main.startMs + 1).let { if (it >= 0) it else -it - 1 }
            val markerEnd = markers.getOrNull(markerIndex)
            val next = starts.getOrNull(starts.binarySearch(main.startMs) + 1)
            main.copy(
                endMs = listOfNotNull(main.endMs ?: markerEnd, next).minOrNull(),
                translation = sameTime.drop(1).map { it.text }.filter(String::isNotBlank)
                    .joinToString("\n").takeIf(String::isNotEmpty),
            )
        }.withLineEnds()
        val offset = metadata["offset"]?.toLongOrNull() ?: 0L
        return EchoLyrics(
            lines = grouped.map { line -> line.copy(
                startMs = (line.startMs + offset).coerceAtLeast(0),
                endMs = line.endMs?.let { (it + offset).coerceAtLeast(0) },
                words = line.words.map { it.copy(startMs = (it.startMs + offset).coerceAtLeast(0),
                    endMs = it.endMs?.let { end -> (end + offset).coerceAtLeast(0) }) },
            ) },
            metadata = metadata, sourceLabel = source, format = EchoLyricsFormat.Spl, offsetMs = offset,
        )
    }
}

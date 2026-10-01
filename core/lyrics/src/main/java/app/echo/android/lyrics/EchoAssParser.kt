package app.echo.android.lyrics

import app.echo.android.lyrics.EchoLyricsParser.decodeEntities
import app.echo.android.lyrics.EchoLyricsParser.parseClockMs
import app.echo.android.lyrics.EchoLyricsParser.withLineEnds
import app.echo.android.model.lyrics.*

/** ASS/SSA karaoke durations are centiseconds, accumulated from the event start. */
internal object EchoAssParser {
    private val override = Regex("""\{([^}]*)\}""")
    private val karaoke = Regex("""\\(k[fo]?|K|kt)(\d+)""")
    private fun plain(text: String): String = decodeEntities(text.replace("\\N", "\n")
        .replace("\\n", "\n").replace("\\h", " "))

    fun parse(text: String, source: String?): EchoLyrics {
        val events = text.lineSequence().map(String::trim).dropWhile { !it.equals("[Events]", true) }
            .drop(1).takeWhile { !it.startsWith('[') }.toList()
        val fields = events.firstOrNull { it.startsWith("Format:", true) }?.substringAfter(':')
            ?.split(',')?.map { it.trim().lowercase() }.orEmpty()
        val startIndex = fields.indexOf("start")
        val endIndex = fields.indexOf("end")
        val textIndex = fields.indexOf("text")
        val nameIndex = fields.indexOfFirst { it == "name" || it == "actor" }
        val lines = if (startIndex < 0 || endIndex < 0 || textIndex != fields.lastIndex) emptyList() else
            events.filter { it.startsWith("Dialogue:", true) }.mapNotNull { event ->
                val values = event.substringAfter(':').trim().split(',', limit = fields.size)
                if (values.size != fields.size) return@mapNotNull null
                val start = parseClockMs(values[startIndex])
                val end = parseClockMs(values[endIndex])
                if (end <= start) return@mapNotNull null
                val raw = values[textIndex]
                val body = plain(override.replace(raw, "")).trim()
                if (body.isBlank()) return@mapNotNull null
                val words = words(raw, start, end).takeIf { it.joinToString("") { word -> word.text }.trim() == body }.orEmpty()
                EchoLyricLine(start, end, body, words = words,
                    speaker = values.getOrNull(nameIndex)?.takeIf(String::isNotBlank))
            }.withLineEnds()
        return EchoLyrics(lines, sourceLabel = source, format = EchoLyricsFormat.Ass)
    }

    private fun words(raw: String, start: Long, end: Long): List<EchoLyricWord> {
        val words = ArrayList<EchoLyricWord>()
        var cursor = 0
        var from = start
        var duration: Long? = null
        val content = StringBuilder()
        var timed = false
        fun flush() {
            if (content.isNotEmpty()) {
                val to = duration?.let { from + minOf(it, (end - from).coerceAtLeast(0L)) } ?: from
                words += EchoLyricWord(from, to, plain(content.toString()))
                from = to
                content.setLength(0)
            } else duration?.let { from += minOf(it, (end - from).coerceAtLeast(0L)) }
            duration = null
        }
        for (tag in override.findAll(raw)) {
            content.append(raw, cursor, tag.range.first)
            val clocks = karaoke.findAll(tag.groupValues[1]).toList()
            for (mark in clocks) {
                val time = mark.groupValues[2].toLongOrNull()?.takeIf { it <= Long.MAX_VALUE / 10 }?.times(10)
                    ?: return emptyList()
                flush()
                if (mark.groupValues[1] == "kt") {
                    if (time > end - start) return emptyList()
                    from = start + time
                    duration = null
                } else {
                    duration = time
                    timed = true
                }
            }
            cursor = tag.range.last + 1
        }
        content.append(raw, cursor, raw.length)
        flush()
        return if (timed) words else emptyList()
    }
}

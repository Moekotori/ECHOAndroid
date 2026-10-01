package app.echo.android.lyrics

import app.echo.android.lyrics.EchoLyricsParser.decodeEntities
import app.echo.android.lyrics.EchoLyricsParser.parseClockMs
import app.echo.android.lyrics.EchoLyricsParser.stripTags
import app.echo.android.lyrics.EchoLyricsParser.withLineEnds
import app.echo.android.model.lyrics.*

internal object EchoVttParser {
    private const val clock = "(?:\\d{1,3}:)?\\d{2}:\\d{2}\\.\\d{3}"
    private val timing = Regex("^($clock)\\s*-->\\s*($clock)(?:\\s+.*)?$")
    private val timestamp = Regex("<($clock)>")
    private val voice = Regex("<v(?:\\.[^ >]+)*\\s+([^>]+)>")

    fun parse(text: String, source: String?): EchoLyrics {
        val lines = text.replace("\r\n", "\n").split(Regex("\n[ \t]*\n")).mapNotNull { block ->
            val rows = block.trim().lines()
            val first = rows.firstOrNull().orEmpty()
            if (first.startsWith("NOTE") || first == "STYLE" || first == "REGION" || first.startsWith("WEBVTT"))
                return@mapNotNull null
            val header = rows.indexOfFirst { timing.matches(it.trim()) }
            if (header !in 0..1) return@mapNotNull null
            val match = timing.matchEntire(rows[header].trim()) ?: return@mapNotNull null
            val start = parseClockMs(match.groupValues[1])
            val end = parseClockMs(match.groupValues[2])
            if (end <= start) return@mapNotNull null
            val raw = rows.drop(header + 1).joinToString("\n").trim()
            val body = decodeEntities(stripTags(raw))
            if (body.isBlank()) return@mapNotNull null
            EchoLyricLine(start, end, body, words = words(raw, start, end),
                speaker = voice.find(raw)?.groupValues?.get(1)?.let(::decodeEntities))
        }.withLineEnds()
        return EchoLyrics(lines, sourceLabel = source, format = EchoLyricsFormat.Vtt)
    }

    private fun words(raw: String, start: Long, end: Long): List<EchoLyricWord> {
        val marks = timestamp.findAll(raw).toList()
        if (marks.isEmpty()) return emptyList()
        val words = ArrayList<EchoLyricWord>()
        var cursor = 0
        var from = start
        for (mark in marks) {
            val to = parseClockMs(mark.groupValues[1])
            if (to < from || to >= end || to == from && cursor > 0) return emptyList()
            val chunk = decodeEntities(stripTags(raw.substring(cursor, mark.range.first)))
            if (chunk.isNotEmpty()) words += EchoLyricWord(from, to, chunk)
            from = to
            cursor = mark.range.last + 1
        }
        val tail = decodeEntities(stripTags(raw.substring(cursor)))
        if (tail.isNotEmpty()) words += EchoLyricWord(from, end, tail)
        return words
    }
}

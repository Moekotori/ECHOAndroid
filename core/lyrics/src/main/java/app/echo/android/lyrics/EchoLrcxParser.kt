package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyricAlternateText
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat
import java.text.BreakIterator
import java.util.Locale

/** LyricsKit attachments index visible characters, not UTF-16 code units. */
internal object EchoLrcxParser {
    private val attachment = Regex("""^(\s*\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?])\[([a-z_-]+)(?::([^]]+))?](.*)$""", RegexOption.IGNORE_CASE)
    private val timing = Regex("""<(\d{1,9}),(\d{1,6})>""")
    private val finalTime = Regex("""<(\d{1,9})>\s*$""")
    private val reading = Regex("""<([^,>]+),(\d{1,6}),(\d{1,6})>""")

    fun looksLike(text: String): Boolean = text.lineSequence().any {
        attachment.matchEntire(it.trim())?.groupValues?.get(2)?.lowercase(Locale.ROOT) in setOf("tr", "tt", "fu", "ro")
    }

    fun parse(text: String, source: String?): EchoLyrics {
        val rows = text.lineSequence().map(String::trim).toList()
        val base = EchoLrcParser.parse(rows.filterNot(attachment::matches).joinToString("\n"), source)
        val lines = base.lines.toMutableList()
        val byTime = lines.indices.associateBy { lines[it].startMs }
        for (row in rows) {
            val match = attachment.matchEntire(row) ?: continue
            val stamp = EchoLyricsParser.parseClockMs(match.groupValues[1].trim().removeSurrounding("[", "]"))
            val index = byTime[(stamp + base.offsetMs).coerceAtLeast(0)] ?: continue
            val line = lines[index]
            val body = match.groupValues[4]
            if (body.length > 16_384) continue
            val chars by lazy { characters(line.text) }
            when (match.groupValues[2].lowercase(Locale.ROOT)) {
                "tr" -> if (body.isNotBlank()) {
                    val value = body.trim().take(2000)
                    val alternate = EchoLyricAlternateText("translation", value, match.groupValues[3].takeIf(String::isNotBlank))
                    lines[index] = line.copy(translation = line.translation?.takeIf(String::isNotBlank) ?: value,
                        alternateTexts = (line.alternateTexts + alternate).distinct().take(8))
                }
                "tt" -> {
                    val tags = timing.findAll(body).take(514).map { it.groupValues[1].toLong() to it.groupValues[2].toInt() }.toList()
                    if (tags.isEmpty() || tags.size > 513 || tags.first().second != 0 || tags.any { it.second > chars.size } ||
                        tags.zipWithNext().any { (a, b) -> b.first < a.first || b.second <= a.second }) continue
                    val end = finalTime.find(body)?.groupValues?.get(1)?.toLong()
                    val words = tags.mapIndexedNotNull { i, (time, at) ->
                        val next = tags.getOrNull(i + 1)
                        val value = chars.subList(at, next?.second ?: chars.size).joinToString("")
                        if (value.isEmpty()) null else EchoLyricWord(line.startMs + time,
                            (next?.first ?: end)?.takeIf { it > time }?.let { line.startMs + it }, value)
                    }
                    if (words.size <= 512 && words.joinToString("") { it.text } == line.text) lines[index] = line.copy(words = words)
                }
                "ro", "fu" -> {
                    var cursor = 0
                    val result = StringBuilder()
                    for (tag in reading.findAll(body).take(512)) {
                        val start = tag.groupValues[2].toInt()
                        val end = tag.groupValues[3].toInt()
                        if (start < cursor || end <= start || end > chars.size) continue
                        result.append(chars.subList(cursor, start).joinToString("")).append(tag.groupValues[1])
                        cursor = end
                    }
                    if (cursor > 0) {
                        result.append(chars.subList(cursor, chars.size).joinToString(""))
                        lines[index] = if (match.groupValues[2].equals("ro", true)) line.copy(romanization = result.toString())
                            else line.copy(kana = result.toString(), romanization = line.romanization ?: result.toString())
                    }
                }
            }
        }
        return base.copy(lines = lines, format = EchoLyricsFormat.Lrcx)
    }

    private fun characters(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val result = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return result
    }
}

package app.echo.android.lyrics

import app.echo.android.lyrics.EchoLyricsParser.decodeEntities
import app.echo.android.lyrics.EchoLyricsParser.stripTags
import app.echo.android.model.lyrics.*

/** SAMI is HTML-like rather than well-formed XML; paragraph end tags are often omitted. */
internal object EchoSamiParser {
    private val sync = Regex("""<sync\b[^>]*\bstart\s*=\s*["']?(\d+)["']?[^>]*>""", RegexOption.IGNORE_CASE)
    private val paragraph = Regex("""<p\b([^>]*)>(.*?)(?=<p\b|</body\s*>|</sami\s*>|\z)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val languageClass = Regex("""\bclass\s*=\s*["']?([^\s"'>]+)""", RegexOption.IGNORE_CASE)

    fun parse(text: String, source: String?): EchoLyrics {
        val marks = sync.findAll(text).toList()
        var primaryClass: String? = null
        var established = false
        val lines = marks.mapIndexedNotNull { index, mark ->
            val start = mark.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val next = marks.getOrNull(index + 1)
            val body = text.substring(mark.range.last + 1, next?.range?.first ?: text.length)
            val paragraphs = paragraph.findAll(body).map {
                languageClass.find(it.groupValues[1])?.groupValues?.get(1)?.lowercase() to
                    decodeEntities(stripTags(it.groupValues[2])).trim()
            }.toList()
            if (!established) {
                val first = paragraphs.firstOrNull { it.second.isNotBlank() } ?: return@mapIndexedNotNull null
                primaryClass = first.first
                established = true
            }
            val primary = paragraphs.filter { it.first == primaryClass }.map { it.second }
                .filter(String::isNotBlank).joinToString("\n")
            if (primary.isBlank()) return@mapIndexedNotNull null
            EchoLyricLine(start, next?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > start }, primary,
                translation = paragraphs.filter { it.first != primaryClass }.map { it.second }
                    .filter(String::isNotBlank).joinToString("\n").ifBlank { null })
        }.sortedBy { it.startMs }
        return EchoLyrics(lines, sourceLabel = source, format = EchoLyricsFormat.Sami)
    }
}

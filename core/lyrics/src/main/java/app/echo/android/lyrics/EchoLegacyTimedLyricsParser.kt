package app.echo.android.lyrics

import app.echo.android.lyrics.EchoLyricsParser.withLineEnds
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat

/** TRC durations are cumulative milliseconds; SNC clocks are HHMMSScc. */
internal object EchoLegacyTimedLyricsParser {
    private val snc = Regex("""^⑩(\d{2})(\d{2})(\d{2})(\d{2})⑿(.*)$""")
    private val trc = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?](.*)$""")
    private val word = Regex("""<(\d{1,9})>([^<]*)""")
    private val duration = Regex("""<\d+>""")
    private val offsetTag = Regex("""^\[offset:([+-]?\d+)]$""", RegexOption.IGNORE_CASE)

    fun looksLike(text: String): Boolean = text.lineSequence().any {
        val row = it.trim()
        snc.matches(row) || trc.matchEntire(row)?.groupValues?.get(4)?.let(duration::containsMatchIn) == true
    }

    fun parse(text: String, source: String?): EchoLyrics {
        val isSnc = source?.endsWith(".snc", true) == true || text.lineSequence().any { snc.matches(it.trim()) }
        val offset = if (isSnc) 0L else text.lineSequence().mapNotNull {
            offsetTag.matchEntire(it.trim())?.groupValues?.get(1)?.toLongOrNull()
        }.lastOrNull()?.coerceIn(-600_000L, 600_000L) ?: 0L
        val lines = ArrayList<EchoLyricLine>()
        for (row in text.lineSequence().map(String::trim)) {
            if (lines.size >= 2000) break
            if (row.isBlank() || offsetTag.matches(row)) continue
            if (isSnc) {
                val match = snc.matchEntire(row)
                if (match == null) {
                    // Malformed timestamp markers cannot become visible continuation text.
                    if (row.startsWith('⑩')) continue
                    val previous = lines.lastOrNull() ?: continue
                    if (previous.text.length + row.length < 2000) lines[lines.lastIndex] = previous.copy(text = previous.text + " " + row)
                    continue
                }
                val (hours, minutes, seconds, hundredths, body) = match.destructured
                if (minutes.toInt() > 59 || seconds.toInt() > 59 || body.length !in 1..2000) continue
                lines += EchoLyricLine(hours.toLong() * 3_600_000 + minutes.toLong() * 60_000 +
                    seconds.toLong() * 1000 + hundredths.toLong() * 10, text = body.trim())
            } else {
                val match = trc.matchEntire(row) ?: continue
                if (match.groupValues[2].toInt() > 59) continue
                val start = (match.groupValues[1].toLong() * 60_000 + match.groupValues[2].toLong() * 1000 +
                    match.groupValues[3].padEnd(3, '0').toLong() - offset).coerceAtLeast(0)
                val body = match.groupValues[4]
                val clean = body.replace(duration, "").trim()
                if (clean.length !in 1..2000) continue
                val tags = word.findAll(body).take(513).toList()
                var cursor = start
                val words = if (tags.size > 512) emptyList() else tags.mapNotNull { tag ->
                    val end = cursor + tag.groupValues[1].toLong()
                    val value = tag.groupValues[2]
                    val result = if (value.isNotBlank() && end > cursor) EchoLyricWord(cursor, end, value) else null
                    cursor = end
                    result
                }
                // A prefix or malformed duration must not silently disappear from word rendering.
                val usable = words.takeIf { it.joinToString("") { part -> part.text }.trim() == clean }.orEmpty()
                lines += EchoLyricLine(start, cursor.takeIf { usable.isNotEmpty() }, clean, words = usable)
            }
        }
        return EchoLyrics(lines.withLineEnds(), sourceLabel = source,
            format = if (isSnc) EchoLyricsFormat.Snc else EchoLyricsFormat.Trc, offsetMs = -offset)
    }
}

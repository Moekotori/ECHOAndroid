package app.echo.android.lyrics

import app.echo.android.lyrics.EchoLyricsParser.withLineEnds
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat
import java.util.Base64

/** AWLRC is a base64 container embedded in LRC metadata, not another file extension. */
internal object EchoAwlrcParser {
    private val container = Regex("""\[awlrc:([^]]+)]""")
    private val clock = Regex("""^\[(\d{1,3}(?::\d{1,3}){0,2})\.(\d{1,3})](.*)$""")
    private val word = Regex("""<(\d{1,9}),(\d{1,9})(?:,\d+)?>([^<]*)""")

    fun parse(text: String, source: String?): EchoLyrics? {
        val payload = container.find(text)?.groupValues?.get(1) ?: return null
        val tracks = HashMap<String, String>()
        for (part in payload.split(',')) {
            val key = part.substringBefore(':')
            if (key !in setOf("lrc", "tlrc", "rlrc", "awlrc")) continue
            val encoded = part.substringAfter(':', "")
            if (encoded.length > 512 * 1024) continue
            val decoded = runCatching { Base64.getDecoder().decode(encoded.replace('-', '+').replace('_', '/')).toString(Charsets.UTF_8) }.getOrNull()
            if (!decoded.isNullOrBlank()) tracks[key] = decoded
        }
        if (tracks.isEmpty()) return null
        val timed = tracks["awlrc"]
        val base = if (timed != null) EchoLyrics(parseTrack(timed, true), sourceLabel = source, format = EchoLyricsFormat.EnhancedLrc)
            else EchoLrcParser.parse(tracks["lrc"].orEmpty(), source)
        fun secondary(key: String): Map<Long, String> {
            val raw = tracks[key] ?: return emptyMap()
            val rows = if (timed != null) parseTrack(raw, false) else EchoLrcParser.parse(raw).lines
            return rows.associate { it.startMs to it.text }
        }
        val translation = secondary("tlrc")
        val romanization = secondary("rlrc")
        fun attach(values: Map<Long, String>): List<String?> {
            if (base.lines.any { it.startMs in values }) return base.lines.map { values[it.startMs] }
            val times = values.keys.sorted()
            val used = HashSet<Long>()
            return base.lines.map { line ->
                val at = times.binarySearch(line.startMs).let { if (it >= 0) it else -it - 1 }
                val closest = listOfNotNull(times.getOrNull(at - 1), times.getOrNull(at)).filterNot { it in used }
                    .minByOrNull { kotlin.math.abs(it - line.startMs) }
                    ?.takeIf { kotlin.math.abs(it - line.startMs) <= 1000 }
                closest?.let { used += it; values[it] }
            }
        }
        val translated = attach(translation)
        val romanized = attach(romanization)
        return base.copy(lines = base.lines.mapIndexed { i, line -> line.copy(
            translation = translated[i]?.takeIf(String::isNotBlank) ?: line.translation,
            romanization = romanized[i]?.takeIf(String::isNotBlank) ?: line.romanization,
        ) })
    }

    private fun parseTrack(text: String, timed: Boolean): List<EchoLyricLine> = text.lineSequence().mapNotNull { raw ->
        val match = clock.matchEntire(raw.trim()) ?: return@mapNotNull null
        val fields = match.groupValues[1].split(':').map(String::toLong).let { List(3 - it.size) { 0L } + it }
        if (fields[1] > 59 || fields[2] > 59) return@mapNotNull null
        val start = fields[0] * 3_600_000 + fields[1] * 60_000 + fields[2] * 1000 + match.groupValues[2].toLong()
        val body = match.groupValues[3]
        if (body.length > 16_384) return@mapNotNull null
        if (!timed) return@mapNotNull body.trim().takeIf { it.length in 1..2000 }?.let { EchoLyricLine(start, text = it) }
        val tags = word.findAll(body).take(513).toList()
        if (tags.size !in 1..512) return@mapNotNull null
        var cursor = start
        val words = tags.map { tag ->
            val from = maxOf(cursor, start + tag.groupValues[1].toLong())
            val to = from + tag.groupValues[2].toLong().coerceAtLeast(1)
            cursor = to
            EchoLyricWord(from, to, tag.groupValues[3])
        }
        val value = words.joinToString("") { it.text }.trim()
        if (value.length !in 1..2000) null else EchoLyricLine(start, cursor, value, words = words)
    }.take(2000).toList().withLineEnds()
}

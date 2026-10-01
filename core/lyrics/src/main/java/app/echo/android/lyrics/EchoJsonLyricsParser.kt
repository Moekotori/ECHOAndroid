package app.echo.android.lyrics

import app.echo.android.lyrics.EchoLyricsParser.withLineEnds
import app.echo.android.model.lyrics.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** File import for ECHO exports, timestamped line arrays, and supported provider response envelopes. */
internal object EchoJsonLyricsParser {
    fun parse(text: String, source: String?): EchoLyrics {
        val root = if (text.trimStart().startsWith('[')) JSONObject().put("lines", JSONArray(text)) else JSONObject(text)
        if (root.optInt("version") == 1 && root.has("format") && root.has("metadata") && root.has("lines")) {
            return EchoLyricsJson.decode(text).copy(sourceLabel = source)
        }
        val payload = root.optJSONObject("lyrics") ?: root
        val rows = payload.optJSONArray("lines")
        if (rows != null) return parseLines(rows, source)

        fun embedded(key: String): EchoLyrics? {
            val raw = root.optJSONObject(key)?.string("lyric") ?: root.string(key)
            if (raw.isNullOrBlank()) return null
            require(!raw.trimStart().startsWith('{')) { "Nested JSON lyric envelopes are not supported" }
            return EchoLyricsParser.parse(raw, sourceLabel = when (key) {
                "yrc" -> "lyrics.yrc"
                else -> "lyrics.lrc"
            }).takeIf { it.lines.any { line -> line.text.isNotBlank() } }
        }
        val primary = embedded("yrc") ?: embedded("lrc") ?: embedded("syncedLyrics") ?: embedded("plainLyrics")
            ?: throw IllegalArgumentException("Unrecognized JSON lyric structure")
        val translated = embedded("ytlrc") ?: embedded("tlyric")
        val romanized = embedded("yromalrc") ?: embedded("romalrc")
        return primary.copy(sourceLabel = source, lines = primary.lines.map { line ->
            line.copy(translation = matching(translated, line.startMs), romanization = matching(romanized, line.startMs))
        })
    }

    private fun matching(auxiliary: EchoLyrics?, start: Long): String? {
        val lines = auxiliary?.lines ?: return null
        var low = 0
        var high = lines.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMs < start) low = mid + 1 else high = mid
        }
        val next = lines.getOrNull(low)
        val previous = lines.getOrNull(low - 1)
        val closest = if (previous != null && (next == null || abs(previous.startMs - start) <= abs(next.startMs - start)))
            previous else next
        return closest?.takeIf { abs(it.startMs - start) <= 500L }?.text
    }

    private fun parseLines(rows: JSONArray, source: String?): EchoLyrics {
        require(rows.length() <= 10_000) { "Too many JSON lyric lines" }
        val lines = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val wordRows = row.optJSONArray("words")
            require(wordRows == null || wordRows.length() <= 1024) { "Too many words in a JSON lyric line" }
            val words = if (wordRows == null) emptyList() else (0 until wordRows.length()).mapNotNull word@ { i ->
                val word = wordRows.optJSONObject(i) ?: return@word null
                val start = word.milliseconds("startMs", "start", "startTimeMs")?.takeIf { it >= 0 } ?: return@word null
                val value = word.string("text") ?: word.string("word") ?: return@word null
                EchoLyricWord(start, word.milliseconds("endMs", "end", "endTimeMs")?.takeIf { it >= start }, value)
            }
            val value = row.string("text") ?: row.string("words") ?: words.joinToString("") { it.text }
            if (value.isBlank()) return@mapNotNull null
            val start = row.milliseconds("startMs", "start", "startTimeMs") ?: words.firstOrNull()?.startMs ?: -1L
            val end = row.milliseconds("endMs", "end", "endTimeMs")?.takeIf { it > start }
            EchoLyricLine(start, end, value, row.string("translation"), row.string("romanization"), words,
                row.string("speaker"), row.optBoolean("background", row.optBoolean("isBackground")))
        }.withLineEnds()
        return EchoLyrics(lines, sourceLabel = source, format = EchoLyricsFormat.Json)
    }

    private fun JSONObject.string(key: String): String? = (opt(key) as? String)?.takeIf(String::isNotBlank)
    private fun JSONObject.milliseconds(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        // Numbers and integer strings are accepted; fractional/invalid units are never silently zeroed.
        opt(key)?.toString()?.toLongOrNull()
    }
}

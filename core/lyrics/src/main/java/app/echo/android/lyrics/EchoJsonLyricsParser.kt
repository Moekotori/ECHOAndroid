package app.echo.android.lyrics

import app.echo.android.lyrics.EchoLyricsParser.withLineEnds
import app.echo.android.model.lyrics.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToLong

/** File import for ECHO exports, timestamped line arrays, and supported provider response envelopes. */
internal object EchoJsonLyricsParser {
    fun parse(text: String, source: String?): EchoLyrics {
        val root = if (text.trimStart().startsWith('[')) JSONObject().put("lines", JSONArray(text)) else JSONObject(text)
        if (root.optInt("version") == 1 && root.has("format") && root.has("metadata") && root.has("lines")) {
            return EchoLyricsJson.decode(text).copy(sourceLabel = source)
        }
        val payload = root.optJSONObject("lyrics") ?: root
        val rows = payload.optJSONArray("lines")
        if (rows != null) return parseLines(rows, source).copy(offsetMs = payload.milliseconds("offsetMs") ?: 0L)

        fun embedded(key: String): EchoLyrics? {
            val raw = root.optJSONObject(key)?.string("lyric") ?: root.string(key)
            if (raw.isNullOrBlank()) return null
            require(!raw.trimStart().startsWith('{')) { "Nested JSON lyric envelopes are not supported" }
            return EchoLyricsParser.parse(raw, sourceLabel = when (key) {
                "yrc" -> "lyrics.yrc"
                "plainLyrics" -> "lyrics.txt"
                else -> "lyrics.lrc"
            }).takeIf { it.lines.any { line -> line.text.isNotBlank() } }
        }
        val primary = embedded("yrc") ?: embedded("lrc") ?: embedded("syncedLyrics") ?: embedded("plainLyrics")
            ?: throw IllegalArgumentException("Unrecognized JSON lyric structure")
        val translated = embedded("ytlrc") ?: embedded("tlyric")
        val romanized = embedded("yromalrc") ?: embedded("romalrc")
        return primary.copy(sourceLabel = source, lines = primary.lines.map { line ->
            line.copy(
                translation = matching(translated, line.startMs) ?: line.translation,
                romanization = matching(romanized, line.startMs) ?: line.romanization,
            )
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
        val lines = buildList<EchoLyricLine> {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                parseLine(row)?.let { require(size < 10_000) { "Too many JSON lyric lines" }; add(it) }
                val background = row.optJSONArray("backgroundVocals") ?: continue
                require(background.length() <= 8) { "Too many background vocals in a JSON lyric line" }
                for (i in 0 until background.length()) {
                    background.optJSONObject(i)?.let(::parseLine)?.copy(isBackground = true)?.let {
                        require(size < 10_000) { "Too many JSON lyric lines" }; add(it)
                    }
                }
            }
        }.withLineEnds()
        return EchoLyrics(lines, sourceLabel = source, format = EchoLyricsFormat.Json)
    }

    private fun parseLine(row: JSONObject): EchoLyricLine? {
        val wordRows = row.optJSONArray("words")
        require(wordRows == null || wordRows.length() <= 1024) { "Too many words in a JSON lyric line" }
        val words = if (wordRows == null) emptyList() else (0 until wordRows.length()).mapNotNull { i ->
            val word = wordRows.optJSONObject(i) ?: return@mapNotNull null
            val start = word.milliseconds("startMs", "start", "startTimeMs")?.takeIf { it >= 0 } ?: return@mapNotNull null
            val text = word.string("text") ?: word.string("word") ?: return@mapNotNull null
            EchoLyricWord(start, word.milliseconds("endMs", "end", "endTimeMs")?.takeIf { it >= start }, text)
        }
        val text = row.string("text") ?: row.string("words") ?: words.joinToString("") { it.text }
        if (text.isBlank()) return null
        val start = row.milliseconds("timeMs", "startMs", "start", "startTimeMs") ?: words.firstOrNull()?.startMs ?: -1L
        val end = row.milliseconds("endMs", "end", "endTimeMs")?.takeIf { it > start }
        return EchoLyricLine(start, end, text, row.string("translation"), row.string("romanization"), words,
            row.string("speaker") ?: row.string("agentName") ?: row.string("agentId"),
            row.optBoolean("background", row.optBoolean("isBackground")))
    }

    private fun JSONObject.string(key: String): String? = (opt(key) as? String)?.takeIf(String::isNotBlank)
    private fun JSONObject.milliseconds(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        // ECHO peer timings may contain fractional milliseconds; integer strings remain supported.
        when (val value = opt(key)) {
            is Number -> value.toDouble().takeIf { it.isFinite() }?.roundToLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
}

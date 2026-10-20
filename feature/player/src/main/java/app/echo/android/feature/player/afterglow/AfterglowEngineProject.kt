package app.echo.android.feature.player.afterglow

import app.echo.android.feature.player.estimatedLyricWords
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.settings.EchoLyricsPageStyle

/** Compact word span in code points without whitespace, the unit the Jizura engine uses for lyric text. */
internal class AfterglowEngineWord(val from: Int, val to: Int, val start: Double, val end: Double)

/** Wire shapes of echosteam's jizuraProject.ts; the engine owns planning, this only hands it text and times. */
internal object AfterglowEngineProject {
    private const val MaxLines = 512
    private const val MaxTextLength = 32768
    private const val MaxTimings = 8192

    fun look(style: EchoLyricsPageStyle): String = if (style == EchoLyricsPageStyle.AfterglowNight) "night" else "mist"

    /** `null` when the document is beyond what the engine accepts; the caller keeps the native stage. */
    fun build(
        plan: AfterglowPlan, durationMs: Long, title: String, artist: String, seed: Long, look: String,
        wordHighlight: Boolean, estimatedHighlight: Boolean, checkCancelled: () -> Unit = {},
    ): String? {
        if (plan.lines.size > MaxLines) return null
        val out = StringBuilder(4096)
        out.append("{\"title\":").append(quote(title.take(256)))
            .append(",\"artist\":").append(quote(artist.take(256)))
            .append(",\"duration\":").append(if (durationMs > 0) durationMs / 1000.0 else 0.0)
            .append(",\"lines\":[")
        var textLength = 0
        var timingBudget = MaxTimings
        plan.lines.forEachIndexed { index, line ->
            if (index % 64 == 0) checkCancelled()
            val text = line.text.replace('\r', ' ').replace('\n', ' ').take(512)
            textLength += text.length
            if (textLength > MaxTextLength) return null
            val start = line.startMs.coerceAtLeast(0L) / 1000.0
            val end = maxOf(start + 0.01, plan.endAt(index) / 1000.0)
            val estimated = wordHighlight && estimatedHighlight && line.words.isEmpty()
            val words = when {
                !wordHighlight -> emptyList()
                line.words.isNotEmpty() -> line.words
                estimatedHighlight -> estimatedLyricWords(line, plan.endAt(index))
                else -> emptyList()
            }
            val timings = wordIntervals(text, start, end, words, timingBudget)
            timingBudget -= timings.size
            if (index > 0) out.append(',')
            out.append("{\"text\":").append(quote(text)).append(",\"start\":").append(start).append(",\"end\":").append(end)
            if (timings.isNotEmpty()) {
                out.append(",\"timings\":[")
                timings.forEachIndexed { i, word ->
                    if (i > 0) out.append(',')
                    out.append("{\"from\":").append(word.from).append(",\"to\":").append(word.to)
                        .append(",\"start\":").append(word.start).append(",\"end\":").append(word.end).append('}')
                }
                out.append(']')
                if (estimated) out.append(",\"estimatedTimings\":true")
            }
            out.append('}')
        }
        out.append("],\"seed\":").append(seed.coerceIn(0L, 0xFFFFFFFFL))
            .append(",\"style\":").append(quote("echo-$look"))
            .append(",\"preferences\":{\"look\":").append(quote(look))
            .append(",\"fit\":\"window\",\"unifiedComposition\":true,\"randomComposition\":false")
            .append(",\"extraExpressions\":false,\"originalStyle\":null,\"themeScenesOnly\":false}}")
        return out.toString()
    }

    /** Literal word ranges only; a word that is not found in order is skipped, as in createJizuraWordIntervals. */
    fun wordIntervals(text: String, lineStart: Double, lineEnd: Double, words: List<EchoLyricWord>, limit: Int): List<AfterglowEngineWord> {
        if (words.isEmpty() || limit <= 0) return emptyList()
        val source = stripWhitespace(text)
        val result = ArrayList<AfterglowEngineWord>()
        var cursor = 0
        val cap = minOf(limit, 256)
        for (i in 0 until minOf(words.size, 512)) {
            if (result.size >= cap) break
            val word = words[i]
            val value = stripWhitespace(word.text.take(512))
            if (value.isEmpty()) continue
            val at = source.indexOf(value, cursor)
            if (at < 0) continue
            val start = maxOf(lineStart, word.startMs / 1000.0)
            if (start >= lineEnd) continue
            val rawEnd = (word.endMs ?: words.getOrNull(i + 1)?.startMs)?.let { it / 1000.0 }
            val end = minOf(lineEnd, maxOf(start + 0.005, rawEnd ?: lineEnd))
            val from = source.codePointCount(0, at)
            result += AfterglowEngineWord(from, from + value.codePointCount(0, value.length), start, end)
            cursor = at + value.length
        }
        return result
    }

    private fun stripWhitespace(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    fun clock(positionMs: Long, sampledAtMs: Long, speed: Float, playing: Boolean, offsetMs: Long, frameIntervalMs: Int?, motion: Boolean): String =
        "{\"type\":\"echo:jizura:clock\",\"clock\":{\"positionMs\":$positionMs,\"sampledAtMs\":$sampledAtMs," +
            "\"playbackRate\":${speed.takeIf { it.isFinite() && it > 0f } ?: 1f},\"playing\":$playing,\"offsetMs\":$offsetMs," +
            "\"frameIntervalMs\":${frameIntervalMs ?: "null"},\"motion\":$motion}}"

    fun renderSettings(fps: Int, stepped: Boolean, pressureReduced: Boolean): String =
        "{\"type\":\"echo:jizura:render-settings\",\"settings\":{\"fps\":${fps.coerceIn(1, 240)},\"quality\":\"auto\"," +
            "\"steppedMotion\":$stepped,\"pressureReduced\":$pressureReduced,\"transparent\":false}}"

    fun projectMessage(project: String): String = "{\"type\":\"echo:jizura:project\",\"project\":$project}"

    /** A JSON string that is also a valid JavaScript string literal, so it can be embedded in evaluateJavascript. */
    fun quote(value: String): String {
        val out = StringBuilder(value.length + 2).append('"')
        for (c in value) when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c < ' ' || c == ' ' || c == ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> out.append(c)
        }
        return out.append('"').toString()
    }
}

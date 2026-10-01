package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat

object EchoLyricsParser {
    val fileExtensions: List<String> = listOf(
        ".spl", ".lrc", ".elrc", ".lrcx", ".yrc", ".ttml", ".dfxp", ".xml",
        ".srt", ".vtt", ".webvtt", ".ass", ".ssa", ".sbv", ".smi", ".sami",
        ".json", ".qrc", ".krc", ".txt",
    )

    fun parse(rawText: String, sourceLabel: String? = null): EchoLyrics {
        val normalized = rawText.replace("\uFEFF", "").trim()
        if (sourceLabel?.endsWith(".json", true) == true ||
            normalized.startsWith('{') && JsonLyricsKeyRegex.containsMatchIn(normalized)) {
            return EchoJsonLyricsParser.parse(normalized, sourceLabel)
        }
        val text = Regex("""LyricContent\s*=\s*"([^"]*)""", RegexOption.IGNORE_CASE)
            .find(normalized)?.groupValues?.get(1)?.let(::decodeEntities) ?: normalized
        if (text.isBlank()) return EchoLyrics(sourceLabel = sourceLabel, format = EchoLyricsFormat.PlainText)

        return when {
            sourceLabel?.endsWith(".spl", true) == true -> EchoSplParser.parse(text, sourceLabel)
            sourceLabel?.endsWith(".smi", true) == true || sourceLabel?.endsWith(".sami", true) == true ||
                text.contains("<SAMI", true) -> EchoSamiParser.parse(text, sourceLabel)
            sourceLabel?.endsWith(".sbv", true) == true || EchoSbvParser.looksLike(text) -> EchoSbvParser.parse(text, sourceLabel)
            looksLikeTtml(text, sourceLabel) -> parseTtml(text, sourceLabel)
            looksLikeVtt(text, sourceLabel) -> EchoVttParser.parse(text, sourceLabel)
            looksLikeSrt(text, sourceLabel) -> parseSrt(text, sourceLabel)
            looksLikeAss(text, sourceLabel) -> EchoAssParser.parse(text, sourceLabel)
            looksLikeLineDurationLyrics(text, sourceLabel) -> parseLineDurationLyrics(text, sourceLabel)
            looksLikeLrc(text, sourceLabel) -> EchoLrcParser.parse(text, sourceLabel)
            sourceLabel?.endsWith(".xml", true) == true -> throw IllegalArgumentException("Unrecognized XML lyric format")
            else -> parsePlainText(text, sourceLabel)
        }
    }

    private fun parseTtml(text: String, sourceLabel: String?): EchoLyrics =
        EchoTtmlParser.parse(text, sourceLabel)

    private fun parseSrt(text: String, sourceLabel: String?): EchoLyrics {
        val normalized = text.replace("\r\n", "\n")
        val lines = SrtBlockRegex.findAll(normalized)
            .mapNotNull { match ->
                val startMs = parseClockMs(match.groupValues[1])
                val endMs = parseClockMs(match.groupValues[2])
                val body = match.groupValues[3]
                    .lineSequence()
                    .map { stripTags(it).trim() }
                    .filter(String::isNotBlank)
                    .joinToString("\n")
                if (body.isBlank()) return@mapNotNull null
                EchoLyricLine(
                    startMs = startMs,
                    endMs = endMs,
                    text = decodeEntities(body),
                )
            }
            .toList()
            .withLineEnds()

        return EchoLyrics(
            lines = lines,
            sourceLabel = sourceLabel,
            format = EchoLyricsFormat.Srt,
        )
    }

    private fun parseLineDurationLyrics(text: String, sourceLabel: String?): EchoLyrics {
        val metadata = linkedMapOf<String, String>()
        val qrc = sourceLabel?.endsWith(".qrc", true) == true || text.lineSequence().any { raw ->
            val body = LineDurationRegex.matchEntire(raw.trim())?.groupValues?.get(3) ?: return@any false
            QrcTimeRegex.containsMatchIn(body) && DurationWordRegex.find(body)?.range?.first != 0
        }
        val lines = text.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { rawLine ->
                val metadataMatch = LrcMetadataRegex.matchEntire(rawLine)
                if (metadataMatch != null) {
                    metadata[metadataMatch.groupValues[1].trim().lowercase()] = metadataMatch.groupValues[2].trim()
                    return@mapNotNull null
                }

                val lineMatch = LineDurationRegex.matchEntire(rawLine) ?: return@mapNotNull null
                val startMs = lineMatch.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val durationMs = lineMatch.groupValues[2].toLongOrNull() ?: 0L
                val body = lineMatch.groupValues[3]
                val words = if (qrc) {
                    parseQrcWords(body)
                } else parseDurationWords(body, lineStartMs = startMs,
                    relative = sourceLabel?.endsWith(".krc", true) == true || body.contains(KrcWordRegex))
                val textValue = if (words.isNotEmpty()) {
                    words.joinToString(separator = "") { it.text }
                } else {
                    body.replace(DurationWordRegex, "").compactWhitespace()
                }
                if (textValue.isBlank() && words.isEmpty()) return@mapNotNull null

                EchoLyricLine(
                    startMs = startMs.coerceAtLeast(0L),
                    endMs = (startMs + durationMs).takeIf { durationMs > 0L },
                    text = textValue,
                    words = words,
                )
            }
            .toList()
            .withLineEnds()

        return EchoLyrics(
            lines = lines,
            metadata = metadata,
            sourceLabel = sourceLabel,
            format = when {
                qrc -> EchoLyricsFormat.Qrc
                sourceLabel?.endsWith(".krc", ignoreCase = true) == true -> EchoLyricsFormat.Krc
                else -> EchoLyricsFormat.Yrc
            },
        )
    }

    private fun parseQrcWords(body: String): List<EchoLyricWord> {
        var cursor = 0
        val words = QrcTimeRegex.findAll(body).map { match ->
            val word = body.substring(cursor, match.range.first)
            cursor = match.range.last + 1
            val start = match.groupValues[1].toLong()
            EchoLyricWord(start, start + match.groupValues[2].toLong(), word)
        }.toMutableList()
        if (cursor < body.length && words.isNotEmpty()) {
            val last = words.last(); words[words.lastIndex] = last.copy(text = last.text + body.substring(cursor))
        }
        return words
    }

    private fun parseDurationWords(body: String, lineStartMs: Long, relative: Boolean): List<EchoLyricWord> {
        val tags = DurationWordRegex.findAll(body).toList()
        return tags.mapIndexed { index, match ->
            val start = (match.groupValues[1].ifBlank { match.groupValues[3] }).toLong()
            val duration = (match.groupValues[2].ifBlank { match.groupValues[4] }).toLong()
            val absolute = if (relative) lineStartMs + start else start
            val prefix = if (index == 0) body.substring(0, match.range.first) else ""
            EchoLyricWord(absolute, absolute + duration,
                prefix + body.substring(match.range.last + 1, tags.getOrNull(index + 1)?.range?.first ?: body.length))
        }
    }

    private fun parsePlainText(text: String, sourceLabel: String?): EchoLyrics {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter(String::isNotBlank)
            .map { line ->
                EchoLyricLine(
                    startMs = -1L,
                    text = decodeEntities(stripTags(line)),
                )
            }
            .toList()

        return EchoLyrics(
            lines = lines,
            sourceLabel = sourceLabel,
            format = EchoLyricsFormat.PlainText,
        )
    }

    internal fun parseClockMs(raw: String): Long {
        val value = raw.trim()
        if (value.endsWith("ms", ignoreCase = true)) {
            return value.dropLast(2).toDoubleOrNull()?.toLong()?.coerceAtLeast(0L) ?: 0L
        }
        if (value.endsWith("s", ignoreCase = true)) {
            return ((value.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000L).toLong().coerceAtLeast(0L)
        }
        val match = ClockRegex.matchEntire(value.replace(',', '.')) ?: return 0L
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: 0L
        val seconds = match.groupValues[3].toLongOrNull() ?: 0L
        val fraction = match.groupValues.getOrNull(4).orEmpty()
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L)
            2 -> fraction.toLongOrNull()?.times(10L)
            else -> fraction.take(3).padEnd(3, '0').toLongOrNull()
        } ?: 0L
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis
    }

    internal fun List<EchoLyricLine>.withLineEnds(): List<EchoLyricLine> {
        val sorted = sortedBy { it.startMs }
        var nextDistinctStart: Long? = null
        return sorted.indices.reversed().map { index ->
            val line = sorted[index]
            if (index < sorted.lastIndex && sorted[index + 1].startMs > line.startMs) {
                nextDistinctStart = sorted[index + 1].startMs
            }
            line.copy(endMs = line.endMs ?: nextDistinctStart)
        }.reversed()
    }

    internal fun stripTags(value: String): String =
        value.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(TagRegex, "")

    internal fun decodeEntities(value: String): String = EntityRegex.replace(value) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
            else -> {
                val codePoint = if (entity.startsWith("#x", true)) entity.drop(2).toIntOrNull(16)
                    else if (entity.startsWith('#')) entity.drop(1).toIntOrNull() else null
                if (codePoint != null && Character.isValidCodePoint(codePoint) && codePoint !in 0xD800..0xDFFF)
                    String(Character.toChars(codePoint)) else match.value
            }
        }
    }

    private fun String.compactWhitespace(): String =
        replace(CompactWhitespaceRegex, " ")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("\n")

    private fun looksLikeTtml(text: String, sourceLabel: String?): Boolean =
        sourceLabel?.endsWith(".ttml", ignoreCase = true) == true ||
            sourceLabel?.endsWith(".dfxp", ignoreCase = true) == true ||
            text.contains("<tt", ignoreCase = true) ||
            text.contains("<p ", ignoreCase = true)

    private fun looksLikeVtt(text: String, sourceLabel: String?): Boolean =
        sourceLabel?.endsWith(".vtt", ignoreCase = true) == true ||
            sourceLabel?.endsWith(".webvtt", ignoreCase = true) == true ||
            text.startsWith("WEBVTT", ignoreCase = true)

    private fun looksLikeSrt(text: String, sourceLabel: String?): Boolean =
        sourceLabel?.endsWith(".srt", ignoreCase = true) == true ||
            SrtBlockRegex.containsMatchIn(text.replace("\r\n", "\n"))

    private fun looksLikeAss(text: String, sourceLabel: String?): Boolean =
        sourceLabel?.endsWith(".ass", ignoreCase = true) == true ||
            sourceLabel?.endsWith(".ssa", ignoreCase = true) == true ||
            (text.contains("[Events]", ignoreCase = true) && text.contains("Dialogue:", ignoreCase = true))

    private fun looksLikeLineDurationLyrics(text: String, sourceLabel: String?): Boolean =
            sourceLabel?.endsWith(".yrc", ignoreCase = true) == true ||
            sourceLabel?.endsWith(".qrc", ignoreCase = true) == true ||
            sourceLabel?.endsWith(".krc", ignoreCase = true) == true ||
            text.lineSequence().any { LineDurationRegex.matches(it.trim()) }

    private fun looksLikeLrc(text: String, sourceLabel: String?): Boolean =
        sourceLabel?.endsWith(".lrc", ignoreCase = true) == true ||
            sourceLabel?.endsWith(".elrc", ignoreCase = true) == true ||
            sourceLabel?.endsWith(".lrcx", ignoreCase = true) == true ||
            LrcTimeRegex.containsMatchIn(text)

    private val LrcTimeRegex = Regex("""\[\d{1,3}:\d{1,2}(?:[\.:]\d{1,3})?\]""")
    private val JsonLyricsKeyRegex = Regex(""""(?:lines|lyrics|lrc|yrc|syncedLyrics|plainLyrics)"\s*:""")
    private val EntityRegex = Regex("""&(#x[0-9a-fA-F]+|#\d+|[A-Za-z]+);""")
    private val LrcMetadataRegex = Regex("""^\[([A-Za-z][\w-]*):(.*)\]$""")
    private val LineDurationRegex = Regex("""^\[(\d{1,8}),(\d{1,8})\](.*)$""")
    private val KrcWordRegex = Regex("""<\d+,\d+(?:,\d+)?>""")
    private val QrcTimeRegex = Regex("""\((\d{1,8}),(\d{1,8})\)""")
    private val DurationWordRegex = Regex("""(?:\((\d{1,8}),(\d{1,8})(?:,\d+)?\)|<(\d{1,8}),(\d{1,8})(?:,\d+)?>)""")
    private val ClockRegex = Regex("""(?:(\d{1,3}):)?(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?""")
    private val CompactWhitespaceRegex = Regex("[ \\t\\u000B\\f\\r]+")
    private val SrtBlockRegex = Regex(
        """(?ms)(?:^\s*\d+\s*\n)?\s*(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3})\s*-->\s*(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3})(?:[^\n]*)\n(.*?)(?=\n\s*\n|\z)""",
    )
    private val TagRegex = Regex("""<[^>]+>""")
}

package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat

object EchoLyricsParser {
    fun parse(rawText: String, sourceLabel: String? = null): EchoLyrics {
        val normalized = rawText.replace("\uFEFF", "").trim()
        val text = Regex("""LyricContent\s*=\s*"([^"]*)""", RegexOption.IGNORE_CASE)
            .find(normalized)?.groupValues?.get(1)?.let(::decodeEntities) ?: normalized
        if (text.isBlank()) return EchoLyrics(sourceLabel = sourceLabel, format = EchoLyricsFormat.PlainText)

        return when {
            looksLikeTtml(text, sourceLabel) -> parseTtml(text, sourceLabel)
            looksLikeVtt(text, sourceLabel) -> parseVtt(text, sourceLabel)
            looksLikeSrt(text, sourceLabel) -> parseSrt(text, sourceLabel)
            looksLikeAss(text, sourceLabel) -> parseAss(text, sourceLabel)
            looksLikeLineDurationLyrics(text, sourceLabel) -> parseLineDurationLyrics(text, sourceLabel)
            looksLikeLrc(text, sourceLabel) -> EchoLrcParser.parse(text, sourceLabel)
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

    private fun parseVtt(text: String, sourceLabel: String?): EchoLyrics {
        val normalized = text.replace("\r\n", "\n")
        val lines = VttCueRegex.findAll(normalized)
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
            format = EchoLyricsFormat.Vtt,
        )
    }

    private fun parseAss(text: String, sourceLabel: String?): EchoLyrics {
        val eventLines = text.replace("\r\n", "\n").lineSequence()
            .map(String::trim)
            .dropWhile { !it.equals("[Events]", ignoreCase = true) }
            .toList()

        val formatFields = eventLines
            .firstOrNull { it.startsWith("Format:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.split(',')
            ?.map { it.trim().lowercase() }
            .orEmpty()

        val startIndex = formatFields.indexOf("start")
        val endIndex = formatFields.indexOf("end")
        val textIndex = formatFields.indexOf("text")
        if (startIndex < 0 || endIndex < 0 || textIndex < 0) {
            return parsePlainText(text, sourceLabel)
        }

        val lines = eventLines
            .asSequence()
            .filter { it.startsWith("Dialogue:", ignoreCase = true) }
            .mapNotNull { line ->
                val values = line.substringAfter(':')
                    .trim()
                    .split(",", limit = formatFields.size)
                val start = values.getOrNull(startIndex)?.let(::parseClockMs) ?: return@mapNotNull null
                val end = values.getOrNull(endIndex)?.let(::parseClockMs)
                val body = values.getOrNull(textIndex)
                    ?.replace(AssOverrideTagRegex, "")
                    ?.replace("\\N", "\n")
                    ?.replace("\\n", "\n")
                    ?.let(::decodeEntities)
                    ?.compactWhitespace()
                    ?: return@mapNotNull null
                if (body.isBlank()) return@mapNotNull null
                EchoLyricLine(
                    startMs = start,
                    endMs = end,
                    text = body,
                )
            }
            .toList()
            .withLineEnds()

        return EchoLyrics(
            lines = lines,
            sourceLabel = sourceLabel,
            format = EchoLyricsFormat.Ass,
        )
    }

    private fun parseLineDurationLyrics(text: String, sourceLabel: String?): EchoLyrics {
        val metadata = linkedMapOf<String, String>()
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
                val words = if (sourceLabel?.endsWith(".qrc", true) == true && !body.startsWith("(")) {
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
                sourceLabel?.endsWith(".qrc", ignoreCase = true) == true -> EchoLyricsFormat.Qrc
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

    private fun parseClockMs(raw: String): Long {
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

    private fun List<EchoLyricLine>.withLineEnds(): List<EchoLyricLine> {
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

    private fun stripTags(value: String): String =
        value.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(TagRegex, "")

    private fun decodeEntities(value: String): String =
        value.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

    private fun String.compactWhitespace(): String =
        replace(CompactWhitespaceRegex, " ")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("\n")

    private fun looksLikeTtml(text: String, sourceLabel: String?): Boolean =
        sourceLabel?.endsWith(".ttml", ignoreCase = true) == true ||
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
    private val LrcMetadataRegex = Regex("""^\[([A-Za-z][\w-]*):(.*)\]$""")
    private val LineDurationRegex = Regex("""^\[(\d{1,8}),(\d{1,8})\](.*)$""")
    private val KrcWordRegex = Regex("""<\d+,\d+(?:,\d+)?>""")
    private val QrcTimeRegex = Regex("""\((\d{1,8}),(\d{1,8})\)""")
    private val DurationWordRegex = Regex("""(?:\((\d{1,8}),(\d{1,8})(?:,\d+)?\)|<(\d{1,8}),(\d{1,8})(?:,\d+)?>)""")
    private val ClockRegex = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?""")
    private val CompactWhitespaceRegex = Regex("[ \\t\\u000B\\f\\r]+")
    private val SrtBlockRegex = Regex(
        """(?ms)(?:^\s*\d+\s*\n)?\s*(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3})\s*-->\s*(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3})(?:[^\n]*)\n(.*?)(?=\n\s*\n|\z)""",
    )
    private val VttCueRegex = Regex(
        """(?ms)(?:^|\n)(?:[^\n]*\n)?\s*((?:\d{1,2}:)?\d{2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{2}:\d{2}[,.]\d{1,3})(?:[^\n]*)\n(.*?)(?=\n\s*\n|\z)""",
    )
    private val TagRegex = Regex("""<[^>]+>""")
    private val AssOverrideTagRegex = Regex("""\{[^}]*\}""")
}

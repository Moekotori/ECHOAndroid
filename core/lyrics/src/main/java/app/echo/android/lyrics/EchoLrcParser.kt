package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat

object EchoLrcParser {
    fun parse(rawText: String, sourceLabel: String? = null): EchoLyrics {
        val metadata = linkedMapOf<String, String>()
        val parsedLines = mutableListOf<ParsedLine>()
        var offsetMs = 0L

        rawText
            .replace("\uFEFF", "")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { rawLine ->
                val metadataMatch = MetadataRegex.matchEntire(rawLine)
                if (metadataMatch != null && TimeTagRegex.find(rawLine) == null) {
                    val key = metadataMatch.groupValues[1].trim().lowercase()
                    val value = metadataMatch.groupValues[2].trim()
                    metadata[key] = value
                    if (key == "offset") offsetMs = value.toLongOrNull() ?: offsetMs
                    return@forEach
                }

                val timestamps = TimeTagRegex.findAll(rawLine)
                    .mapNotNull { it.toTimeMs() }
                    .toList()
                if (timestamps.isEmpty()) return@forEach

                val content = rawLine.replace(TimeTagRegex, "").trim()
                val words = parseEnhancedWords(content)
                val cleanText = cleanLyricText(content, words)
                if (cleanText.isBlank() && words.isEmpty()) return@forEach

                timestamps.forEach { timestamp ->
                    parsedLines += ParsedLine(
                        startMs = (timestamp + offsetMs).coerceAtLeast(0L),
                        text = cleanText,
                        words = words.offsetBy(offsetMs),
                    )
                }
            }

        val grouped = parsedLines
            .sortedBy { it.startMs }
            .groupBy { it.startMs }
            .map { (startMs, linesAtTime) ->
                val primary = linesAtTime.first()
                val secondary = linesAtTime.drop(1).map { it.text }.filter(String::isNotBlank)
                EchoLyricLine(
                    startMs = startMs,
                    text = primary.text,
                    translation = secondary.firstOrNull(),
                    romanization = secondary.drop(1).firstOrNull(),
                    words = primary.words,
                )
            }
            .sortedBy { it.startMs }
            .withLineEnds()

        return EchoLyrics(
            lines = grouped,
            metadata = metadata,
            sourceLabel = sourceLabel,
            format = if (grouped.any { it.words.isNotEmpty() }) EchoLyricsFormat.EnhancedLrc else EchoLyricsFormat.Lrc,
            offsetMs = offsetMs,
        )
    }

    private fun parseEnhancedWords(content: String): List<EchoLyricWord> {
        val matches = EnhancedTimeRegex.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexedNotNull { index, match ->
            val startMs = match.toTimeMs(angleTag = true) ?: return@mapIndexedNotNull null
            val textStart = match.range.last + 1
            val textEnd = matches.getOrNull(index + 1)?.range?.first ?: content.length
            val text = content.substring(textStart, textEnd)
                .replace(EnhancedTimeRegex, "")
                .trimStart()
            if (text.isBlank()) return@mapIndexedNotNull null

            EchoLyricWord(
                startMs = startMs,
                endMs = matches.getOrNull(index + 1)?.toTimeMs(angleTag = true),
                text = text,
            )
        }
    }

    private fun cleanLyricText(content: String, words: List<EchoLyricWord>): String =
        when {
            words.isNotEmpty() -> words.joinToString(separator = "") { it.text }.trim()
            else -> content.replace(EnhancedTimeRegex, "").trim()
        }

    private fun List<EchoLyricWord>.offsetBy(offsetMs: Long): List<EchoLyricWord> =
        if (offsetMs == 0L) {
            this
        } else {
            map { word ->
                word.copy(
                    startMs = (word.startMs + offsetMs).coerceAtLeast(0L),
                    endMs = word.endMs?.let { (it + offsetMs).coerceAtLeast(0L) },
                )
            }
        }

    private fun List<EchoLyricLine>.withLineEnds(): List<EchoLyricLine> =
        mapIndexed { index, line ->
            line.copy(endMs = getOrNull(index + 1)?.startMs)
        }

    private fun MatchResult.toTimeMs(angleTag: Boolean = false): Long? {
        val minuteIndex = if (angleTag) 1 else 1
        val secondIndex = if (angleTag) 2 else 2
        val fractionIndex = if (angleTag) 3 else 3
        val minutes = groupValues.getOrNull(minuteIndex)?.toLongOrNull() ?: return null
        val seconds = groupValues.getOrNull(secondIndex)?.toLongOrNull() ?: return null
        val fraction = groupValues.getOrNull(fractionIndex).orEmpty()
        val millis = when (fraction.length) {
            0 -> 0
            1 -> fraction.toLongOrNull()?.times(100)
            2 -> fraction.toLongOrNull()?.times(10)
            else -> fraction.take(3).padEnd(3, '0').toLongOrNull()
        } ?: 0L
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    private data class ParsedLine(
        val startMs: Long,
        val text: String,
        val words: List<EchoLyricWord>,
    )

    private val MetadataRegex = Regex("""^\[([A-Za-z][\w-]*):(.*)\]$""")
    private val TimeTagRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[\.:](\d{1,3}))?\]""")
    private val EnhancedTimeRegex = Regex("""<(\d{1,3}):(\d{1,2})(?:[\.:](\d{1,3}))?(?:,\d+)?>""")
}

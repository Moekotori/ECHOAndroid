package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import java.text.BreakIterator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Display-only approximation; never replaces provider timings or writes back to the lyric file. */
internal fun estimatedLyricWords(line: EchoLyricLine, lineEndMs: Long?): List<EchoLyricWord> {
    if (line.words.isNotEmpty()) return line.words
    if (line.startMs < 0 || line.text.isBlank() || line.text.length > 1024) return emptyList()
    val endMs = (line.endMs ?: lineEndMs)?.takeIf { it > line.startMs } ?: return emptyList()
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(line.text) }
    val chunks = ArrayList<String>()
    val weights = ArrayList<Int>()
    var total = 0
    var start = boundaries.first()
    var end = boundaries.next()
    while (end != BreakIterator.DONE) {
        val text = line.text.substring(start, end)
        val codePoint = text.codePointAt(0)
        val weight = when {
            Character.isWhitespace(codePoint) -> 2
            Character.getType(codePoint) in punctuationTypes -> 4
            Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN ->
                if (text[0].lowercaseChar() in "aeiouy") 7 else 4
            else -> 10
        }
        chunks += text
        weights += weight
        total += weight
        start = end
        end = boundaries.next()
    }
    if (total == 0) return emptyList()
    // A long instrumental gap is not a long held syllable. Keep this heuristic conservative.
    val duration = minOf(endMs - line.startMs, (total * 35L + 500L).coerceIn(800L, 12_000L))
    var consumed = 0
    return chunks.mapIndexed { index, text ->
        val from = line.startMs + duration * consumed / total
        consumed += weights[index]
        EchoLyricWord(from, line.startMs + duration * consumed / total, text)
    }
}

private val punctuationTypes = setOf(
    Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt(),
)

private class EstimatedWords(val source: EchoLyricLine, val endMs: Long?, val words: List<EchoLyricWord>)

/** One bounded result per composed row. Disposing a row cancels pending estimation. */
@Composable
internal fun rememberEstimatedLyricWords(
    line: EchoLyricLine, lineEndMs: Long?, enabled: Boolean,
): State<List<EchoLyricWord>> {
    val prepared = produceState<EstimatedWords?>(null, line, lineEndMs, enabled) {
        value = null
        if (enabled && line.words.isEmpty()) {
            value = EstimatedWords(line, lineEndMs,
                withContext(Dispatchers.Default) { estimatedLyricWords(line, lineEndMs) })
        }
    }
    return remember(line, lineEndMs, enabled, prepared) {
        derivedStateOf {
            val ready = prepared.value
            if (enabled && ready?.source === line && ready.endMs == lineEndMs) ready.words else line.words
        }
    }
}

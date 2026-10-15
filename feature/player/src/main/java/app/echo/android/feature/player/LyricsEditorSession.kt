package app.echo.android.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import app.echo.android.lyrics.EchoLyricsEditing
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics

internal data class LyricsEditRow(val original: EchoLyricLine, val time: String, val text: String, val translation: String)

/** Adopt the first resolved source, then keep the user's draft across reloads. */
internal class LyricsEditorSession(lyrics: EchoLyrics, loading: Boolean) {
    private var original = lyrics
    var sourceReady by mutableStateOf(!loading)
        private set
    val rows = (if (loading) emptyList() else editRows(lyrics)).toMutableStateList()
    private var savedRows by mutableStateOf<List<LyricsEditRow>?>(null)
    val saved: Boolean get() = savedRows?.let { it == rows } == true

    fun initialize(lyrics: EchoLyrics, loading: Boolean) {
        if (sourceReady || loading) return
        original = lyrics
        rows.clear()
        rows.addAll(editRows(lyrics))
        sourceReady = true
    }

    fun markSaved(snapshot: List<LyricsEditRow>) { savedRows = snapshot.toList() }

    fun snapshot(draftRows: List<LyricsEditRow>): EchoLyrics {
        check(sourceReady)
        require(original.lines.size <= 2000)
        val lines = draftRows.map { row ->
            val time = EchoLyricsEditing.parseTimestamp(row.time) ?: error("Invalid time")
            val delta = if (row.original.startMs >= 0 && time >= 0) time - row.original.startMs else 0
            row.original.copy(
                startMs = time,
                endMs = row.original.endMs?.takeIf { row.original.startMs >= 0 && time >= 0 }
                    ?.let { (it + delta).coerceAtLeast(time) },
                text = row.text,
                translation = row.translation.takeIf(String::isNotBlank),
                words = if (row.text == row.original.text && time >= 0) row.original.words.map {
                    it.copy(startMs = (it.startMs + delta).coerceAtLeast(0),
                        endMs = it.endMs?.let { end -> (end + delta).coerceAtLeast(0) })
                } else emptyList(),
            )
        }
        return EchoLyricsEditing.finish(original, lines)
    }

    private fun editRows(lyrics: EchoLyrics): List<LyricsEditRow> = lyrics.lines.take(2000).map {
        LyricsEditRow(it, EchoLyricsEditing.timestamp(it.startMs), it.text, it.translation.orEmpty())
    }
}

package app.echo.android.lyrics

import app.echo.android.data.OriginalAudioShareLyrics
import app.echo.android.model.lyrics.EchoLyrics
import java.util.Locale

/** Export against the whole original's timeline, rather than a CUE excerpt's zero point. */
internal object OriginalAudioShareLyricsFormatter {
    fun format(original: EchoLyrics, userOffsetMs: Long, clipStartMs: Long): OriginalAudioShareLyrics? {
        if (original.lines.none { it.text.isNotBlank() }) return null
        val lyrics = original.withUserOffset(userOffsetMs.coerceIn(-30_000L, 30_000L) + clipStartMs.coerceAtLeast(0))
        val complete = lyrics.lines.withIndex().any { (index, line) ->
            line.words.isNotEmpty() || line.speaker != null || line.isBackground || line.romanization != null ||
                line.endMs?.let { it != lyrics.lines.getOrNull(index + 1)?.startMs } == true ||
                lyrics.isSynced && line.startMs < 0
        }
        val text = buildString {
            if (lyrics.isSynced) append("[re:ECHO]\n")
            lyrics.lines.forEach { line ->
                val prefix = if (line.startMs >= 0) "[${timestamp(line.startMs)}]" else ""
                append(prefix)
                val words = line.words
                val enhanced = words.isNotEmpty() && words.all { it.startMs >= 0 } &&
                    words.zipWithNext().all { (a, b) -> a.startMs <= b.startMs } && words.joinToString("") { it.text } == line.text
                if (enhanced) words.forEach { append('<').append(timestamp(it.startMs)).append('>').append(clean(it.text)) }
                else append(clean(line.text))
                append('\n')
                listOfNotNull(line.translation, line.romanization).filter(String::isNotBlank).forEach {
                    append(prefix).append(clean(it)).append('\n')
                }
            }
        }
        return OriginalAudioShareLyrics(text, if (lyrics.isSynced) "lrc" else "txt",
            if (complete) EchoLyricsJson.encode(lyrics) else null)
    }

    private fun clean(text: String): String = text.replace('\r', ' ').replace('\n', ' ')
    private fun timestamp(ms: Long): String = "%02d:%02d.%03d".format(Locale.ROOT, ms / 60_000, ms / 1_000 % 60, ms % 1_000)
}

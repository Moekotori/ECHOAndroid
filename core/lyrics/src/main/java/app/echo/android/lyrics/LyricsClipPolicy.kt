package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.library.CueSheetPolicy

/** Whole-file lyrics use the source timeline; per-track sidecars already use the clipped timeline. */
object LyricsClipPolicy {
    fun canMirrorToWholeFile(trackId: String, startMs: Long, endMs: Long): Boolean =
        !CueSheetPolicy.isCueTrackId(trackId) && startMs <= 0L && endMs <= 0L

    fun embedded(lyrics: EchoLyrics, startMs: Long, endMs: Long): EchoLyrics? =
        clip(lyrics, startMs, endMs)

    fun sidecar(lyrics: EchoLyrics, audioFileName: String?, startMs: Long, endMs: Long): EchoLyrics? =
        if (LyricsSidecarNames.isAudioSidecar(lyrics.sourceLabel, audioFileName)) clip(lyrics, startMs, endMs)
        else LyricsContentPolicy.readable(lyrics)

    private fun clip(lyrics: EchoLyrics, startMs: Long, endMs: Long): EchoLyrics? {
        if (startMs <= 0L && endMs <= 0L) return LyricsContentPolicy.readable(lyrics)
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.takeIf { it > 0L }
        if (end != null && end <= start) return null
        val duration = end?.minus(start)
        fun relative(time: Long): Long = (if (time <= start) 0L else time - start).let {
            if (duration != null) it.coerceAtMost(duration) else it
        }
        val lines = lyrics.lines.asSequence().filter { line ->
            line.startMs >= 0L && (end == null || line.startMs < end) &&
                (line.startMs >= start || line.endMs?.let { it > start } == true)
        }.map { line ->
            val boundedEnd = listOfNotNull(line.endMs, end).minOrNull()
            line.copy(
                startMs = relative(line.startMs),
                endMs = boundedEnd?.let(::relative),
                // Preserve the text/word correspondence, including words already sung at the boundary.
                words = line.words.map { word -> word.copy(startMs = relative(word.startMs), endMs = word.endMs?.let(::relative)) },
            )
        }.toList()
        return LyricsContentPolicy.readable(lyrics.copy(lines = lines))
    }
}

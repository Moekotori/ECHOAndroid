package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics

/** Silence markers are useful inside lyrics, but cannot make an empty source win selection. */
object LyricsContentPolicy {
    fun readable(lyrics: EchoLyrics?): EchoLyrics? = lyrics?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
}

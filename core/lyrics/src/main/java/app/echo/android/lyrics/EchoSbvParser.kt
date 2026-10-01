package app.echo.android.lyrics

import app.echo.android.lyrics.EchoLyricsParser.decodeEntities
import app.echo.android.lyrics.EchoLyricsParser.parseClockMs
import app.echo.android.lyrics.EchoLyricsParser.stripTags
import app.echo.android.lyrics.EchoLyricsParser.withLineEnds
import app.echo.android.model.lyrics.*

/** YouTube/SubViewer SBV: comma-separated start/end clocks followed by a multiline cue. */
internal object EchoSbvParser {
    private val timing = Regex("""^(\d{1,3}:\d{2}:\d{2}\.\d{1,3}),\s*(\d{1,3}:\d{2}:\d{2}\.\d{1,3})$""")
    fun looksLike(text: String): Boolean = text.lineSequence().any { timing.matches(it.trim()) }

    fun parse(text: String, source: String?): EchoLyrics {
        val lines = text.replace("\r\n", "\n").split(Regex("\n[ \t]*\n")).mapNotNull { block ->
            val rows = block.trim().lines()
            val clocks = timing.matchEntire(rows.firstOrNull().orEmpty().trim()) ?: return@mapNotNull null
            val start = parseClockMs(clocks.groupValues[1])
            val end = parseClockMs(clocks.groupValues[2])
            val body = decodeEntities(stripTags(rows.drop(1).joinToString("\n"))).trim()
            if (body.isBlank() || end <= start) null else EchoLyricLine(start, end, body)
        }.withLineEnds()
        return EchoLyrics(lines, sourceLabel = source, format = EchoLyricsFormat.Sbv)
    }
}

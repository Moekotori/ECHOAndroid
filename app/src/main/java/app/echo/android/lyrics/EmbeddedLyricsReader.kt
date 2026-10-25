package app.echo.android.lyrics

import app.echo.android.data.readLocalAudioTags
import app.echo.android.data.readBoundedEmbeddedLyrics
import app.echo.android.model.lyrics.EchoLyrics
import java.io.InputStream

/** Share container, charset and ID3 handling with the metadata editor. */
internal object EmbeddedLyricsReader {
    fun read(input: InputStream): EmbeddedLyricsText? =
        readLocalAudioTags(input)?.lyrics?.takeIf { it.isNotBlank() }
            ?.let { EmbeddedLyricsText(it, "Embedded tags") }

    fun readForInspection(input: InputStream, checkActive: () -> Unit): EmbeddedLyricsText? =
        readBoundedEmbeddedLyrics(input, checkActive = checkActive)?.let { EmbeddedLyricsText(it, "Embedded tags") }

    fun parse(embedded: EmbeddedLyricsText): EchoLyrics? =
        EchoLyricsParser.parse(embedded.text, sourceLabel = embedded.sourceLabel)
            .takeIf { it.lines.any { line -> line.text.isNotBlank() } }
}

internal data class EmbeddedLyricsText(
    val text: String,
    val sourceLabel: String,
)

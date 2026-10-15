package app.echo.android.lyrics

import app.echo.android.data.readLocalAudioTags
import java.io.InputStream

/** Share container, charset and ID3 handling with the metadata editor. */
internal object EmbeddedLyricsReader {
    fun read(input: InputStream): EmbeddedLyricsText? =
        readLocalAudioTags(input)?.lyrics?.takeIf { it.isNotBlank() }
            ?.let { EmbeddedLyricsText(it, "Embedded tags") }
}

internal data class EmbeddedLyricsText(
    val text: String,
    val sourceLabel: String,
)

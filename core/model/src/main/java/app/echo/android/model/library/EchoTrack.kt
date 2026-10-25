package app.echo.android.model.library

data class EchoTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val sampleRateHz: Int? = null,
    val dateModifiedSeconds: Long = 0L,
    val source: LibrarySource = LibrarySource.MediaStore,
    val genre: String? = null,
    val composer: String? = null,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = 0L,
    val fileName: String? = null,
)

/** Display only: never changes tags, playback identity, search or ordering. No URI decoding or I/O. */
fun EchoTrack.libraryDisplayTitle(showFileNames: Boolean): String {
    val name = fileName?.trim()?.takeIf { it.isNotEmpty() } ?: return title
    if (!showFileNames) return title
    return if (clipStartMs > 0 || clipEndMs > 0 || id.contains("#cue:"))
        listOf(name, title.ifBlank { trackNumber?.toString().orEmpty() }).filter(String::isNotBlank).joinToString(" · ")
    else name
}

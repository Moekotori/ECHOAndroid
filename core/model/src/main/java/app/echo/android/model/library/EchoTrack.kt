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
)

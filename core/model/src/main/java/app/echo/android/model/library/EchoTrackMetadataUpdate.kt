package app.echo.android.model.library

data class EchoTrackMetadataUpdate(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val composer: String? = null,
    val artworkUri: String? = null,
    val lyrics: String? = null,
)

package app.echo.android.model.library

data class CueSheet(
    val fileName: String? = null,
    val album: String? = null,
    val performer: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val tracks: List<CueSheetTrack> = emptyList(),
)

data class CueSheetTrack(
    val number: Int,
    val title: String,
    val performer: String? = null,
    val startMs: Long,
    val endMs: Long = 0L,
    val fileName: String? = null,
)

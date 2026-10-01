package app.echo.android.model.library

enum class EchoSmartPlaylistSort { Title, RecentlyAdded, LeastPlayed, MostPlayed }

/** All selected conditions must match. Blank fields leave that condition unrestricted. */
data class EchoSmartPlaylistRule(
    val artist: String = "",
    val genre: String = "",
    val favoriteOnly: Boolean = false,
    val notPlayedDays: Int = 0,
    val minimumYear: Int = 0,
    val maximumYear: Int = 0,
    val sort: EchoSmartPlaylistSort = EchoSmartPlaylistSort.Title,
    val matchAny: Boolean = false,
    val album: String = "",
    val folder: String = "",
    val format: String = "",
    val minimumDurationSeconds: Int = 0,
    val maximumDurationSeconds: Int = 0,
    val excludeText: String = "",
) {
    val isValid: Boolean get() = artist.length <= 120 && genre.length <= 120 &&
        notPlayedDays in 0..36500 && minimumYear in 0..9999 && maximumYear in 0..9999 &&
        (minimumYear == 0 || maximumYear == 0 || minimumYear <= maximumYear) &&
        listOf(album, folder, format, excludeText).all { it.length <= 120 } &&
        minimumDurationSeconds in 0..86400 && maximumDurationSeconds in 0..86400 &&
        (minimumDurationSeconds == 0 || maximumDurationSeconds == 0 || minimumDurationSeconds <= maximumDurationSeconds)

    companion object { const val IdPrefix = "local:rule:" }
}

data class EchoSmartPlaylistPreview(val count: Int, val tracks: List<EchoTrack>)

data class EchoTrackBookmark(
    val id: String,
    val trackId: String,
    val positionMs: Long,
    val label: String,
)

data class EchoLibraryRepairItem(val track: EchoTrack, val location: String)

data class EchoSavedMoment(val bookmark: EchoTrackBookmark, val track: EchoTrack)

data class EchoBatchTagPatch(val artist: String? = null, val album: String? = null,
    val albumArtist: String? = null, val composer: String? = null, val year: Int? = null, val genre: String? = null)
data class EchoBatchResult(val completed: Int, val failed: Int, val fileWritesFailed: Int = 0)

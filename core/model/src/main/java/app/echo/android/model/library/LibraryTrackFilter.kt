package app.echo.android.model.library

/** Local song filters. Conditions combine with AND; formats within a set combine with OR. */
data class LibraryTrackFilter(
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val composer: String = "",
    val folder: String = "",
    val formats: Set<LibraryFileFormat> = emptySet(),
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val minDurationMs: Long? = null,
    val maxDurationMs: Long? = null,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val minSampleRateHz: Int? = null,
    val maxSampleRateHz: Int? = null,
    val favoritesOnly: Boolean = false,
    val played: LibraryPlayedFilter = LibraryPlayedFilter.Any,
    val artwork: LibraryPresenceFilter = LibraryPresenceFilter.Any,
    val duplicatesOnly: Boolean = false,
    val lyrics: LibraryPresenceFilter = LibraryPresenceFilter.Any,
) {
    val activeCount: Int get() = listOf(
        artist.isNotBlank(), album.isNotBlank(), genre.isNotBlank(), composer.isNotBlank(), folder.isNotBlank(),
        formats.isNotEmpty(), minYear != null || maxYear != null,
        minDurationMs != null || maxDurationMs != null, minSizeBytes != null || maxSizeBytes != null,
        minSampleRateHz != null || maxSampleRateHz != null, favoritesOnly,
        played != LibraryPlayedFilter.Any, artwork != LibraryPresenceFilter.Any, duplicatesOnly,
        lyrics != LibraryPresenceFilter.Any,
    ).count { it }

    val isActive: Boolean get() = activeCount > 0

    fun normalized(): LibraryTrackFilter = copy(
        artist = artist.trim().take(200), album = album.trim().take(200), genre = genre.trim().take(200),
        composer = composer.trim().take(200), folder = folder.trim().take(500), formats = formats.toSet(),
    ).also {
        require(validRange(minYear?.toLong(), maxYear?.toLong()))
        require(validRange(minDurationMs, maxDurationMs))
        require(validRange(minSizeBytes, maxSizeBytes))
        require(validRange(minSampleRateHz?.toLong(), maxSampleRateHz?.toLong()))
    }
}

private fun validRange(min: Long?, max: Long?): Boolean =
    (min == null || min >= 0) && (max == null || max >= 0) && (min == null || max == null || min <= max)

enum class LibraryPlayedFilter { Any, Played, NeverPlayed }
enum class LibraryPresenceFilter { Any, Present, Missing }
enum class LibraryFileFormat { Flac, Mp3, M4a, Aac, Wav, Aiff, Ogg, Opus, Dsd, Ape, WavPack }

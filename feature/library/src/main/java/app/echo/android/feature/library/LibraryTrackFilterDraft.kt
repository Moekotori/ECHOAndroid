package app.echo.android.feature.library

import androidx.compose.runtime.saveable.Saver
import app.echo.android.model.library.LibraryFileFormat
import app.echo.android.model.library.LibraryPlayedFilter
import app.echo.android.model.library.LibraryPresenceFilter
import app.echo.android.model.library.LibraryTrackFilter

/** Keep incomplete numeric input in the sheet; the applied query only receives validated values. */
internal data class LibraryTrackFilterDraft(
    val filter: LibraryTrackFilter,
    val ranges: List<String> = listOf(
        filter.minYear, filter.maxYear,
        formatLibraryFilterUnit(filter.minDurationMs, 1_000), formatLibraryFilterUnit(filter.maxDurationMs, 1_000),
        formatLibraryFilterUnit(filter.minSizeBytes, 1_048_576), formatLibraryFilterUnit(filter.maxSizeBytes, 1_048_576),
        filter.minSampleRateHz, filter.maxSampleRateHz,
    ).map { it?.toString().orEmpty() },
) {
    val conditionCount: Int get() = (if (filter.favoritesOnly) 1 else 0) +
        (if (filter.played != LibraryPlayedFilter.Any) 1 else 0) +
        (if (filter.artwork != LibraryPresenceFilter.Any) 1 else 0) + (if (filter.duplicatesOnly) 1 else 0) +
        (if (filter.lyrics != LibraryPresenceFilter.Any) 1 else 0)
    val tagCount: Int get() = listOf(filter.artist, filter.album, filter.genre, filter.composer, filter.folder).count(String::isNotBlank)
    val rangeCount: Int get() = ranges.chunked(2).count { bounds -> bounds.any(String::isNotEmpty) }

    fun withRange(pair: Int, min: Long?, max: Long?): LibraryTrackFilterDraft {
        require(pair in 0..3)
        return copy(ranges = ranges.mapIndexed { index, value ->
            when (index) {
                pair * 2 -> min?.toString().orEmpty()
                pair * 2 + 1 -> max?.toString().orEmpty()
                else -> value
            }
        })
    }

    fun applied(): LibraryTrackFilter? {
        val values = parseLibraryFilterRanges(ranges) ?: return null
        if (values.take(2).any { it != null && it > Int.MAX_VALUE } ||
            values.drop(6).any { it != null && it > Int.MAX_VALUE }) return null
        if (values.chunked(2).any { (min, max) -> min != null && max != null && min > max }) return null
        return filter.copy(
            minYear = values[0]?.toInt(), maxYear = values[1]?.toInt(),
            minDurationMs = values[2], maxDurationMs = values[3],
            minSizeBytes = values[4], maxSizeBytes = values[5],
            minSampleRateHz = values[6]?.toInt(), maxSampleRateHz = values[7]?.toInt(),
        ).normalized()
    }
}

internal val LibraryTrackFilterDraftSaver = Saver<LibraryTrackFilterDraft, List<String>>(
    save = { draft ->
        with(draft.filter) {
            listOf(artist, album, genre, composer, folder, formats.joinToString { it.name },
                favoritesOnly.toString(), played.name, artwork.name, duplicatesOnly.toString()) + draft.ranges + lyrics.name
        }
    },
    restore = { values ->
        if (values.size !in 18..19) null else runCatching { LibraryTrackFilterDraft(
            LibraryTrackFilter(
                artist = values[0], album = values[1], genre = values[2], composer = values[3], folder = values[4],
                formats = values[5].split(", ").filter(String::isNotBlank).map { LibraryFileFormat.valueOf(it) }.toSet(),
                favoritesOnly = values[6].toBoolean(), played = LibraryPlayedFilter.valueOf(values[7]),
                artwork = LibraryPresenceFilter.valueOf(values[8]), duplicatesOnly = values[9].toBoolean(),
                lyrics = values.getOrNull(18)?.let { LibraryPresenceFilter.valueOf(it) } ?: LibraryPresenceFilter.Any,
            ),
            ranges = values.subList(10, 18),
        ) }.getOrNull()
    },
)

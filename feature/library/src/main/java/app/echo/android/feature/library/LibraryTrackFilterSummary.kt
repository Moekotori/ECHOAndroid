package app.echo.android.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.library.LibraryPlayedFilter
import app.echo.android.model.library.LibraryPresenceFilter
import app.echo.android.model.library.LibraryTrackFilter

/** An applied condition is always removable without reopening the editor. */
@Composable
internal fun LibraryTrackFilterSummary(filter: LibraryTrackFilter, onChange: (LibraryTrackFilter) -> Unit) {
    if (!filter.isActive) return
    val any = stringResource(R.string.library_filter_any)
    fun range(min: Any?, max: Any?): String = "${min?.takeUnless { it == "" } ?: any} – ${max?.takeUnless { it == "" } ?: any}"
    val chips = buildList<Pair<String, () -> Unit>> {
        if (filter.artist.isNotBlank()) add("${stringResource(R.string.library_filter_artist)}: ${filter.artist}" to { onChange(filter.copy(artist = "")) })
        if (filter.album.isNotBlank()) add("${stringResource(R.string.library_filter_album)}: ${filter.album}" to { onChange(filter.copy(album = "")) })
        if (filter.genre.isNotBlank()) add("${stringResource(R.string.library_filter_genre)}: ${filter.genre}" to { onChange(filter.copy(genre = "")) })
        if (filter.composer.isNotBlank()) add("${stringResource(R.string.library_filter_composer)}: ${filter.composer}" to { onChange(filter.copy(composer = "")) })
        if (filter.folder.isNotBlank()) add("${stringResource(R.string.library_filter_folder)}: ${filter.folder}" to { onChange(filter.copy(folder = "")) })
        if (filter.formats.isNotEmpty()) add(filter.formats.sortedBy { it.ordinal }.joinToString { it.displayName() } to { onChange(filter.copy(formats = emptySet())) })
        if (filter.favoritesOnly) add(stringResource(R.string.library_filter_favorites) to { onChange(filter.copy(favoritesOnly = false)) })
        if (filter.played != LibraryPlayedFilter.Any) add(stringResource(if (filter.played == LibraryPlayedFilter.Played) R.string.library_filter_played_yes else R.string.library_filter_played_no) to { onChange(filter.copy(played = LibraryPlayedFilter.Any)) })
        if (filter.artwork != LibraryPresenceFilter.Any) add(stringResource(if (filter.artwork == LibraryPresenceFilter.Present) R.string.library_filter_artwork_yes else R.string.library_filter_artwork_no) to { onChange(filter.copy(artwork = LibraryPresenceFilter.Any)) })
        if (filter.lyrics != LibraryPresenceFilter.Any) add(stringResource(if (filter.lyrics == LibraryPresenceFilter.Present) R.string.library_filter_lyrics_yes else R.string.library_filter_lyrics_no) to { onChange(filter.copy(lyrics = LibraryPresenceFilter.Any)) })
        if (filter.duplicatesOnly) add(stringResource(R.string.library_filter_duplicates) to { onChange(filter.copy(duplicatesOnly = false)) })
        if (filter.minYear != null || filter.maxYear != null) add("${stringResource(R.string.library_filter_year)}: ${range(filter.minYear, filter.maxYear)}" to { onChange(filter.copy(minYear = null, maxYear = null)) })
        if (filter.minDurationMs != null || filter.maxDurationMs != null) add("${stringResource(R.string.library_filter_duration)}: ${range(formatLibraryFilterUnit(filter.minDurationMs, 1_000), formatLibraryFilterUnit(filter.maxDurationMs, 1_000))}" to { onChange(filter.copy(minDurationMs = null, maxDurationMs = null)) })
        if (filter.minSizeBytes != null || filter.maxSizeBytes != null) add("${stringResource(R.string.library_filter_size)}: ${range(formatLibraryFilterUnit(filter.minSizeBytes, 1_048_576), formatLibraryFilterUnit(filter.maxSizeBytes, 1_048_576))}" to { onChange(filter.copy(minSizeBytes = null, maxSizeBytes = null)) })
        if (filter.minSampleRateHz != null || filter.maxSampleRateHz != null) add("${stringResource(R.string.library_filter_sample_rate)}: ${range(filter.minSampleRateHz, filter.maxSampleRateHz)}" to { onChange(filter.copy(minSampleRateHz = null, maxSampleRateHz = null)) })
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(LibraryTrackFilter()) }) { Text(stringResource(R.string.library_filter_clear)) }
        chips.forEach { (label, remove) ->
            InputChip(selected = true, onClick = remove, modifier = Modifier.widthIn(max = 260.dp),
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { EchoIcon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) })
        }
    }
}

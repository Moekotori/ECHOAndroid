package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.library.LibraryTrackSortMode

@Composable
internal fun LibraryTrackSortMode.label(): String = when (this) {
    LibraryTrackSortMode.Title -> stringResource(R.string.library_sort_title_asc)
    LibraryTrackSortMode.Duration -> stringResource(R.string.library_sort_duration_desc)
    LibraryTrackSortMode.FrequentlyPlayed -> stringResource(R.string.feature_library_frequently_played_9d6932)
    LibraryTrackSortMode.RecentlyPlayed -> stringResource(R.string.feature_library_recently_played_ce4d5f)
    LibraryTrackSortMode.Random -> stringResource(R.string.feature_library_shuffle_f8e15b)
    LibraryTrackSortMode.Artist -> stringResource(R.string.library_sort_artist_asc)
    LibraryTrackSortMode.Album -> stringResource(R.string.library_sort_album_asc)
    LibraryTrackSortMode.RecentlyUpdated -> stringResource(R.string.feature_library_recently_updated_781ab0)
    LibraryTrackSortMode.TitleDescending -> stringResource(R.string.library_sort_title_desc)
    LibraryTrackSortMode.ArtistDescending -> stringResource(R.string.library_sort_artist_desc)
    LibraryTrackSortMode.AlbumDescending -> stringResource(R.string.library_sort_album_desc)
    LibraryTrackSortMode.DurationAscending -> stringResource(R.string.library_sort_duration_asc)
    LibraryTrackSortMode.LeastPlayed -> stringResource(R.string.library_sort_least_played)
    LibraryTrackSortMode.YearAscending -> stringResource(R.string.library_sort_year_asc)
    LibraryTrackSortMode.YearDescending -> stringResource(R.string.library_sort_year_desc)
    LibraryTrackSortMode.FileName -> stringResource(R.string.library_sort_file_name)
    LibraryTrackSortMode.Folder -> stringResource(R.string.library_sort_folder)
    LibraryTrackSortMode.SizeAscending -> stringResource(R.string.library_sort_size_asc)
    LibraryTrackSortMode.SizeDescending -> stringResource(R.string.library_sort_size_desc)
    LibraryTrackSortMode.SampleRateAscending -> stringResource(R.string.library_sort_sample_rate_asc)
    LibraryTrackSortMode.SampleRateDescending -> stringResource(R.string.library_sort_sample_rate_desc)
    LibraryTrackSortMode.Format -> stringResource(R.string.library_sort_format)
    LibraryTrackSortMode.ModifiedOldest -> stringResource(R.string.library_sort_modified_oldest)
    LibraryTrackSortMode.TrackNumber -> stringResource(R.string.library_sort_track_number)
}

internal val LocalTrackSortOptions = listOf(
    LibraryTrackSortMode.Title, LibraryTrackSortMode.TitleDescending, LibraryTrackSortMode.Artist, LibraryTrackSortMode.ArtistDescending, LibraryTrackSortMode.Album, LibraryTrackSortMode.AlbumDescending, LibraryTrackSortMode.TrackNumber, LibraryTrackSortMode.YearDescending, LibraryTrackSortMode.YearAscending,
    LibraryTrackSortMode.RecentlyPlayed, LibraryTrackSortMode.FrequentlyPlayed, LibraryTrackSortMode.LeastPlayed, LibraryTrackSortMode.Random,
    LibraryTrackSortMode.DurationAscending, LibraryTrackSortMode.Duration, LibraryTrackSortMode.SizeAscending, LibraryTrackSortMode.SizeDescending, LibraryTrackSortMode.SampleRateAscending, LibraryTrackSortMode.SampleRateDescending, LibraryTrackSortMode.Format,
    LibraryTrackSortMode.FileName, LibraryTrackSortMode.Folder, LibraryTrackSortMode.RecentlyUpdated, LibraryTrackSortMode.ModifiedOldest,
)

internal fun trackSortSection(mode: LibraryTrackSortMode): Int = when (mode) {
    LibraryTrackSortMode.Title, LibraryTrackSortMode.TitleDescending, LibraryTrackSortMode.Artist, LibraryTrackSortMode.ArtistDescending, LibraryTrackSortMode.Album, LibraryTrackSortMode.AlbumDescending, LibraryTrackSortMode.TrackNumber, LibraryTrackSortMode.YearDescending, LibraryTrackSortMode.YearAscending -> R.string.library_sort_group_browse
    LibraryTrackSortMode.RecentlyPlayed, LibraryTrackSortMode.FrequentlyPlayed, LibraryTrackSortMode.LeastPlayed, LibraryTrackSortMode.Random -> R.string.library_sort_group_listening
    LibraryTrackSortMode.DurationAscending, LibraryTrackSortMode.Duration, LibraryTrackSortMode.SizeAscending, LibraryTrackSortMode.SizeDescending, LibraryTrackSortMode.SampleRateAscending, LibraryTrackSortMode.SampleRateDescending, LibraryTrackSortMode.Format -> R.string.library_sort_group_audio
    LibraryTrackSortMode.FileName, LibraryTrackSortMode.Folder, LibraryTrackSortMode.RecentlyUpdated, LibraryTrackSortMode.ModifiedOldest -> R.string.library_sort_group_files
}

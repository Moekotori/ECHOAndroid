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
    LibraryTrackSortMode.AlbumArtist -> stringResource(R.string.library_sort_album_artist_asc)
    LibraryTrackSortMode.AlbumArtistDescending -> stringResource(R.string.library_sort_album_artist_desc)
    LibraryTrackSortMode.Genre -> stringResource(R.string.library_sort_genre_asc)
    LibraryTrackSortMode.GenreDescending -> stringResource(R.string.library_sort_genre_desc)
    LibraryTrackSortMode.Composer -> stringResource(R.string.library_sort_composer_asc)
    LibraryTrackSortMode.ComposerDescending -> stringResource(R.string.library_sort_composer_desc)
    LibraryTrackSortMode.FileNameDescending -> stringResource(R.string.library_sort_file_name_desc)
    LibraryTrackSortMode.FolderDescending -> stringResource(R.string.library_sort_folder_desc)
    LibraryTrackSortMode.FormatDescending -> stringResource(R.string.library_sort_format_desc)
    LibraryTrackSortMode.RatingDescending -> stringResource(R.string.library_sort_rating_desc)
    LibraryTrackSortMode.RatingAscending -> stringResource(R.string.library_sort_rating_asc)
    LibraryTrackSortMode.TrackNumberDescending -> stringResource(R.string.library_sort_track_number_desc)
}

// Fixed priority: everyday browsing first, specialist and reverse orders last.
private val TrackSortGroups = linkedMapOf(
    R.string.library_sort_group_common to listOf(
        LibraryTrackSortMode.Title, LibraryTrackSortMode.RecentlyUpdated,
        LibraryTrackSortMode.Artist, LibraryTrackSortMode.Album,
        LibraryTrackSortMode.FrequentlyPlayed, LibraryTrackSortMode.RecentlyPlayed,
        LibraryTrackSortMode.Random, LibraryTrackSortMode.FileName,
    ),
    R.string.library_sort_group_more to listOf(
        LibraryTrackSortMode.Folder, LibraryTrackSortMode.TrackNumber,
        LibraryTrackSortMode.TitleDescending, LibraryTrackSortMode.ArtistDescending,
        LibraryTrackSortMode.AlbumDescending, LibraryTrackSortMode.YearDescending,
        LibraryTrackSortMode.YearAscending, LibraryTrackSortMode.AlbumArtist,
        LibraryTrackSortMode.Genre, LibraryTrackSortMode.Composer,
        LibraryTrackSortMode.DurationAscending, LibraryTrackSortMode.Duration,
    ),
    R.string.library_sort_group_rare to listOf(
        LibraryTrackSortMode.LeastPlayed, LibraryTrackSortMode.ModifiedOldest,
        LibraryTrackSortMode.FileNameDescending, LibraryTrackSortMode.FolderDescending,
        LibraryTrackSortMode.AlbumArtistDescending, LibraryTrackSortMode.TrackNumberDescending,
        LibraryTrackSortMode.GenreDescending, LibraryTrackSortMode.ComposerDescending,
        LibraryTrackSortMode.SizeDescending, LibraryTrackSortMode.SizeAscending,
        LibraryTrackSortMode.SampleRateDescending, LibraryTrackSortMode.SampleRateAscending,
        LibraryTrackSortMode.Format, LibraryTrackSortMode.FormatDescending, LibraryTrackSortMode.RatingDescending, LibraryTrackSortMode.RatingAscending,
    ),
)

internal val LocalTrackSortOptions = TrackSortGroups.values.flatten()

private val TrackSortSections = TrackSortGroups.flatMap { (title, options) -> options.map { it to title } }.toMap()

internal fun trackSortSection(mode: LibraryTrackSortMode): Int = TrackSortSections.getValue(mode)

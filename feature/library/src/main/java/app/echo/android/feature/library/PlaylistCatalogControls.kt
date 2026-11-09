package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.LibrarySmartPlaylistKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

internal enum class PlaylistCatalogSort { Updated, Name, Count }
internal data class PlaylistCatalogSection(val key: String, val label: Int?, val playlists: List<EchoPlaylist>)

@Composable
internal fun rememberPlaylistCatalog(playlists: List<EchoPlaylist>, query: String, sort: PlaylistCatalogSort): State<List<PlaylistCatalogSection>> {
    val names = mapOf(
        EchoPlaylist.LikedSongsId to stringResource(R.string.feature_library_liked_songs_8d6245),
        LibrarySmartPlaylistKind.Recent.id to stringResource(R.string.feature_library_smart_recent),
        LibrarySmartPlaylistKind.Frequent.id to stringResource(R.string.feature_library_smart_frequent),
        LibrarySmartPlaylistKind.Never.id to stringResource(R.string.feature_library_smart_never),
        LibrarySmartPlaylistKind.Added.id to stringResource(R.string.feature_library_smart_added),
    )
    return produceState(emptyList(), playlists, query, sort, names) {
        if (query.isNotBlank()) delay(160)
        value = withContext(Dispatchers.Default) {
            fun name(playlist: EchoPlaylist) = names[playlist.id] ?: playlist.name
            val needle = query.trim().lowercase(Locale.ROOT)
            val matching = playlists.filter { needle.isBlank() || name(it).lowercase(Locale.ROOT).contains(needle) }
            val byName = compareBy<EchoPlaylist, String>(String.CASE_INSENSITIVE_ORDER, ::name).thenBy { it.id }
            val sorted = matching.sortedWith(when (sort) {
                PlaylistCatalogSort.Name -> byName
                PlaylistCatalogSort.Count -> compareByDescending<EchoPlaylist> { it.trackCount }.then(byName)
                PlaylistCatalogSort.Updated -> compareByDescending<EchoPlaylist> { it.updatedAtEpochMs }.then(byName)
            })
            buildList {
                sorted.filter { it.isLikedSongs }.takeIf { it.isNotEmpty() }?.let { add(PlaylistCatalogSection("liked", null, it)) }
                sorted.filter { it.isSmartPlaylist }.takeIf { it.isNotEmpty() }?.let { add(PlaylistCatalogSection("smart", R.string.playlist_smart_section, it)) }
                val user = sorted.filterNot { it.isLikedSongs || it.isSmartPlaylist }
                if (user.isNotEmpty()) add(PlaylistCatalogSection("user", R.string.playlist_user_section, user))
            }
        }
    }
}

@Composable
internal fun PlaylistCatalogSortMenu(
    selected: PlaylistCatalogSort,
    onSelect: (PlaylistCatalogSort) -> Unit,
    onCustomize: (() -> Unit)?,
) {
    LibrarySortMenu(
        selected = selected,
        options = PlaylistCatalogSort.entries,
        label = { stringResource(it.label) },
        contentDescription = stringResource(R.string.library_sort_title),
        onSelect = onSelect,
        onCustomize = onCustomize,
    )
}

private val PlaylistCatalogSort.label: Int get() = when (this) {
    PlaylistCatalogSort.Updated -> R.string.playlist_list_recent
    PlaylistCatalogSort.Name -> R.string.playlist_list_name
    PlaylistCatalogSort.Count -> R.string.playlist_list_count
}

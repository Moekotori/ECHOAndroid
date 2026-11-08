package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.compose.LazyPagingItems
import app.echo.android.model.library.EchoTrack

internal class LibraryRangeSource(val token: Any, val count: () -> Int, val peek: (Int) -> EchoTrack?,
    val locate: (String) -> Int = { id -> (0 until count()).firstOrNull { peek(it)?.id == id } ?: -1 }) {
    fun indexOf(id: String): Int = locate(id)
}

internal data class LibraryRangeAnchor(val token: Any, val index: Int, val id: String, val knownIds: Map<Int, String> = emptyMap())
internal sealed interface LibraryRangeResult {
    data class Ready(val tracks: List<EchoTrack>) : LibraryRangeResult
    data object Changed : LibraryRangeResult
    data object Unloaded : LibraryRangeResult
    data object TooLarge : LibraryRangeResult
}

internal fun resolveLibraryRange(source: LibraryRangeSource, anchor: LibraryRangeAnchor, endId: String, selected: Set<String>): LibraryRangeResult {
    if (source.token !== anchor.token || anchor.index !in 0 until source.count() || source.peek(anchor.index)?.id != anchor.id) return LibraryRangeResult.Changed
    val end = source.indexOf(endId)
    if (end < 0) return LibraryRangeResult.Changed
    val first = minOf(anchor.index, end); val last = maxOf(anchor.index, end)
    if (last - first + 1 > LibraryBatchSelection.Limit) return LibraryRangeResult.TooLarge
    val tracks = ArrayList<EchoTrack>(last - first + 1)
    for (index in first..last) {
        val track = source.peek(index) ?: return LibraryRangeResult.Unloaded
        if (anchor.knownIds[index]?.let { it != track.id } == true) return LibraryRangeResult.Changed
        tracks += track
    }
    val ids = tracks.mapTo(HashSet()) { it.id }
    if (ids.size != tracks.size) return LibraryRangeResult.Changed
    if (selected.size + ids.count { it !in selected } > LibraryBatchSelection.Limit) return LibraryRangeResult.TooLarge
    return LibraryRangeResult.Ready(tracks)
}

@Composable
internal fun rememberLibraryRangeSource(tracks: LazyPagingItems<EchoTrack>): LibraryRangeSource {
    // Refresh includes query/sort replacement. Appending a page keeps the generation unchanged.
    val token = remember(tracks, tracks.loadState.refresh) { Any() }
    return remember(tracks, token) { LibraryRangeSource(token, { tracks.itemCount }, { tracks.peek(it) }, { id ->
        val snapshot = tracks.itemSnapshotList
        val index = snapshot.items.indexOfFirst { it.id == id }
        if (index < 0) -1 else snapshot.placeholdersBefore + index
    }) }
}

@Composable
internal fun rememberLibraryRangeSource(tracks: List<EchoTrack>): LibraryRangeSource = remember(tracks) {
    LibraryRangeSource(Any(), { tracks.size }, { tracks.getOrNull(it) })
}

package app.echo.android.feature.library

import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.LibraryTrackSortMode

/** An immutable sort result. Page appends reuse normalized keys and the previous order. */
internal class LinkedLibraryTrackOrder private constructor(
    private val source: List<EchoRemoteTrack>,
    private val mode: LibraryTrackSortMode,
    private val rows: List<Row>,
    val tracks: List<EchoRemoteTrack>,
) {
    private class Row(val track: EchoRemoteTrack) {
        val title = track.title.lowercase()
        val artist = track.artist.lowercase()
        val album = track.album.orEmpty().lowercase()
    }

    companion object {
        fun build(
            tracks: List<EchoRemoteTrack>,
            mode: LibraryTrackSortMode,
            previous: LinkedLibraryTrackOrder?,
            checkActive: () -> Unit,
        ): LinkedLibraryTrackOrder {
            val sameMode = previous?.takeIf { it.mode == mode }
            if (sameMode?.source === tracks) return sameMode

            val prefix = sameMode?.takeIf { old ->
                old.source.size < tracks.size && old.source.indices.all { index ->
                    checkActive()
                    old.source[index] === tracks[index]
                }
            }
            val firstNewRow = prefix?.source?.size ?: 0
            val newRows = ArrayList<Row>(tracks.size - firstNewRow)
            for (index in firstNewRow until tracks.size) {
                checkActive()
                newRows.add(Row(tracks[index]))
            }

            val order = when (mode) {
                LibraryTrackSortMode.Artist -> compareBy<Row> { it.artist }.thenBy { it.album }.thenBy { it.title }
                LibraryTrackSortMode.Album -> compareBy<Row> { it.album }.thenBy { it.artist }.thenBy { it.title }
                LibraryTrackSortMode.Duration -> compareByDescending<Row> { it.track.durationMs }.thenBy { it.title }
                else -> compareBy<Row> { it.title }.thenBy { it.artist }.thenBy { it.album }
            }
            val compare = Comparator<Row> { left, right ->
                checkActive()
                order.compare(left, right)
            }
            newRows.sortWith(compare)
            val sorted = if (prefix == null) newRows else merge(prefix.rows, newRows, compare)
            val sortedTracks = sorted.map { row ->
                checkActive()
                row.track
            }
            return LinkedLibraryTrackOrder(tracks, mode, sorted, sortedTracks)
        }

        private fun merge(previous: List<Row>, added: List<Row>, compare: Comparator<Row>): List<Row> {
            val result = ArrayList<Row>(previous.size + added.size)
            var oldIndex = 0
            var newIndex = 0
            while (oldIndex < previous.size && newIndex < added.size) {
                // On ties keep the previous page first, as a stable sort of the full input would.
                if (compare.compare(previous[oldIndex], added[newIndex]) <= 0) {
                    result.add(previous[oldIndex++])
                } else {
                    result.add(added[newIndex++])
                }
            }
            result.addAll(previous.subList(oldIndex, previous.size))
            result.addAll(added.subList(newIndex, added.size))
            return result
        }
    }
}

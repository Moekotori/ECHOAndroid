package app.echo.android.data

import app.echo.android.model.library.LibraryPlaybackOrigin
import app.echo.android.model.library.LibrarySource

data class LibraryPlaybackQueueCandidate(
    val id: String,
    val source: String,
)

object LibraryPlaybackQueuePolicy {
    fun usesCollectionQueue(origin: LibraryPlaybackOrigin): Boolean =
        origin !is LibraryPlaybackOrigin.Songs

    fun collectionKey(origin: LibraryPlaybackOrigin): String? = when (origin) {
        is LibraryPlaybackOrigin.Album -> origin.albumKey
        is LibraryPlaybackOrigin.Artist -> origin.artistKey
        is LibraryPlaybackOrigin.Folder -> origin.folderKey
        is LibraryPlaybackOrigin.Playlist -> origin.playlistId
        LibraryPlaybackOrigin.Songs -> null
    }

    fun startIndex(queueIds: List<String>, tappedTrackId: String): Int =
        queueIds.indexOfFirst { it == tappedTrackId }.takeIf { it >= 0 } ?: 0

    fun usesLocalTrackQueue(selectedLibrarySource: String): Boolean =
        selectedLibrarySource != EchoLibrarySelectedSource.Cloud

    fun trackMatchesSelectedLibrarySource(
        trackSource: String,
        selectedLibrarySource: String,
    ): Boolean {
        val local = LibraryScanPolicy.isLocalLibrarySource(trackSource)
        return if (usesLocalTrackQueue(selectedLibrarySource)) local else !local
    }

    fun mergeAnchorIntoQueue(
        anchor: LibraryPlaybackQueueCandidate?,
        candidates: List<LibraryPlaybackQueueCandidate>,
        selectedLibrarySource: String,
        limit: Int,
    ): List<LibraryPlaybackQueueCandidate> {
        val safeLimit = limit.coerceAtLeast(1)
        val matching = candidates.filter { candidate ->
            trackMatchesSelectedLibrarySource(candidate.source, selectedLibrarySource)
        }
        if (anchor == null) return matching.take(safeLimit)
        if (!trackMatchesSelectedLibrarySource(anchor.source, selectedLibrarySource)) {
            return listOf(anchor)
        }
        if (matching.any { it.id == anchor.id }) return matching.take(safeLimit)
        return (listOf(anchor) + matching.filterNot { it.id == anchor.id }).take(safeLimit)
    }

    fun sourceSql(selectedLibrarySource: String): String =
        if (usesLocalTrackQueue(selectedLibrarySource)) {
            LibraryScanPolicy.LocalSourceSql
        } else {
            LibraryScanPolicy.RemoteSourceSql
        }

    fun isLocalTrackSource(source: String): Boolean =
        source == LibrarySource.MediaStore.id || source == LibraryScanPolicy.SafSourceId
}

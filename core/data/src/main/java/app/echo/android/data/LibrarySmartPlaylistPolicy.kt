package app.echo.android.data

import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.LibrarySmartPlaylistKind
import app.echo.android.model.library.LibrarySource

data class LibrarySmartPlaylistStats(
    val recentCount: Int = 0,
    val frequentCount: Int = 0,
    val neverCount: Int = 0,
    val addedCount: Int = 0,
    val recentArtworkUri: String? = null,
    val frequentArtworkUri: String? = null,
    val neverArtworkUri: String? = null,
    val addedArtworkUri: String? = null,
)

object LibrarySmartPlaylistPolicy {
    fun isSmartPlaylistId(playlistId: String): Boolean =
        LibrarySmartPlaylistKind.fromId(playlistId) != null

    fun playlist(
        kind: LibrarySmartPlaylistKind,
        trackCount: Int,
        artworkUri: String?,
    ): EchoPlaylist =
        EchoPlaylist(
            id = kind.id,
            name = kind.id,
            trackCount = trackCount.coerceAtLeast(0),
            artworkUri = artworkUri,
            source = LibrarySource.MediaStore.id,
        )

    fun pinned(stats: LibrarySmartPlaylistStats): List<EchoPlaylist> =
        listOf(
            playlist(LibrarySmartPlaylistKind.Recent, stats.recentCount, stats.recentArtworkUri),
            playlist(LibrarySmartPlaylistKind.Frequent, stats.frequentCount, stats.frequentArtworkUri),
            playlist(LibrarySmartPlaylistKind.Never, stats.neverCount, stats.neverArtworkUri),
            playlist(LibrarySmartPlaylistKind.Added, stats.addedCount, stats.addedArtworkUri),
        )
}

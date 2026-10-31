package app.echo.android.feature.library

import app.echo.android.model.connect.EchoLinkLibraryQueryPolicy
import app.echo.android.model.connect.EchoRemoteAlbum
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSortMode
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.library.sortedForLibrary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class LinkedLibraryCatalog(
    val tracks: List<EchoRemoteTrack>,
    val albums: List<AlbumSummary>,
    val artists: List<ArtistSummary>,
    val playlists: List<EchoRemotePlaylist>,
) {
    companion object {
        val Empty = LinkedLibraryCatalog(
            tracks = emptyList(),
            albums = emptyList(),
            artists = emptyList(),
            playlists = emptyList(),
        )

        suspend fun build(
            searchText: LinkedLibrarySearchTextCache,
            tracks: List<EchoRemoteTrack>,
            playlists: List<EchoRemotePlaylist>,
            query: String,
            remoteQuery: String,
            sortMode: LibraryTrackSortMode,
            albumSortMode: AlbumSortMode = AlbumSortMode.Title,
            artistSortMode: ArtistSortMode = ArtistSortMode.Name,
            includeSortedTracks: Boolean = true,
            includeAlbums: Boolean = true,
            includeArtists: Boolean = true,
            includePlaylists: Boolean = true,
            remoteAlbums: List<EchoRemoteAlbum> = emptyList(),
            randomOrder: LinkedLibraryRandomOrder = LinkedLibraryRandomOrder(),
        ): LinkedLibraryCatalog {
            val normalizedQuery = query.trim()
            val computationContext = currentCoroutineContext()
            val checkActive = { computationContext.ensureActive() }
            val needsTracks = includeSortedTracks || includeArtists || (includeAlbums && remoteAlbums.isEmpty())
            val filteredTracks = if (!needsTracks) {
                emptyList()
            } else {
                tracks.filterLinkedLibraryQuery(normalizedQuery, searchText, checkActive)
            }
            val filteredPlaylists = if (!includePlaylists) {
                emptyList()
            } else {
                playlists.filterLinkedPlaylistQuery(normalizedQuery, searchText, checkActive)
            }
            val catalogAlbums = when {
                !includeAlbums -> emptyList()
                remoteAlbums.isNotEmpty() -> remoteAlbums.toAlbumSummaries().filterLinkedAlbumQuery(normalizedQuery, searchText, checkActive)
                else -> filteredTracks.toLinkedAlbums(checkActive)
            }
            checkActive()
            currentCoroutineContext().ensureActive()
            return LinkedLibraryCatalog(
                tracks = if (includeSortedTracks) {
                    filteredTracks.sortedForLinkedLibrary(sortMode, randomOrder, checkActive)
                } else {
                    filteredTracks
                },
                albums = catalogAlbums.sortedForLibrary(linkedAlbumSort(albumSortMode)).also { checkActive() },
                artists = if (includeArtists) {
                    filteredTracks.toLinkedArtists(checkActive).sortedForLibrary(artistSortMode).also { checkActive() }
                } else {
                    emptyList()
                },
                playlists = filteredPlaylists,
            )
        }
    }
}

internal fun EchoRemoteAlbum.toAlbumSummary(): AlbumSummary =
    AlbumSummary(
        albumKey = EchoLinkLibraryQueryPolicy.remoteAlbumKey(id),
        title = title,
        albumArtist = albumArtist ?: artist.takeIf { it.isNotBlank() },
        artist = artist.takeIf { it.isNotBlank() },
        artworkUri = artworkUrl,
        trackCount = trackCount,
        durationMs = durationMs,
        year = year,
    )

private fun List<EchoRemoteAlbum>.toAlbumSummaries(): List<AlbumSummary> =
    map { it.toAlbumSummary() }

internal fun EchoRemoteTrack.linkedAlbumKey(): String =
    "echo-link:${album?.trim().orEmpty().lowercase()}|${artist.trim().lowercase()}"

internal fun EchoRemoteTrack.linkedArtistKey(): String =
    "echo-link:${artist.trim().ifBlank { "PC ECHO" }.lowercase()}"

private fun List<EchoRemoteTrack>.toLinkedAlbums(checkActive: () -> Unit): List<AlbumSummary> =
    groupBy { checkActive(); it.linkedAlbumKey() }
        .values
        .map { albumTracks ->
            checkActive()
            val first = albumTracks.first()
            AlbumSummary(
                albumKey = first.linkedAlbumKey(),
                title = first.album?.takeIf { it.isNotBlank() }.orEmpty(),
                albumArtist = first.artist.takeIf { it.isNotBlank() },
                artist = first.artist.takeIf { it.isNotBlank() },
                artworkUri = albumTracks.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) },
                trackCount = albumTracks.size,
                durationMs = albumTracks.sumOf { it.durationMs.coerceAtLeast(0L) },
                year = null,
            )
        }

private fun List<EchoRemoteTrack>.toLinkedArtists(checkActive: () -> Unit): List<ArtistSummary> =
    groupBy { checkActive(); it.linkedArtistKey() }
        .values
        .map { artistTracks ->
            checkActive()
            val first = artistTracks.first()
            ArtistSummary(
                artistKey = first.linkedArtistKey(),
                name = first.artist.takeIf { it.isNotBlank() }.orEmpty(),
                artworkUri = artistTracks.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) },
                albumCount = artistTracks.map { it.linkedAlbumKey() }.distinct().size,
                trackCount = artistTracks.size,
                durationMs = artistTracks.sumOf { it.durationMs.coerceAtLeast(0L) },
            )
        }

private suspend fun List<EchoRemoteTrack>.filterLinkedLibraryQuery(query: String, searchText: LinkedLibrarySearchTextCache, checkActive: () -> Unit): List<EchoRemoteTrack> {
    val terms = normalizedSearchTerms(query)
    if (terms.isEmpty()) return this
    return filterIndexed { index, track ->
        if (index % 64 == 0) currentCoroutineContext().ensureActive()
        checkActive()
        val searchableText = searchText.text(
            track.title,
            track.artist,
            track.album.orEmpty(),
        )
        terms.all(searchableText::contains)
    }
}

private suspend fun List<AlbumSummary>.filterLinkedAlbumQuery(query: String, searchText: LinkedLibrarySearchTextCache, checkActive: () -> Unit): List<AlbumSummary> {
    val terms = normalizedSearchTerms(query)
    if (terms.isEmpty()) return this
    return filterIndexed { index, album ->
        if (index % 64 == 0) currentCoroutineContext().ensureActive()
        checkActive()
        val searchableText = searchText.text(
            album.title,
            album.artist.orEmpty(),
            album.albumArtist.orEmpty(),
        )
        terms.all(searchableText::contains)
    }
}

private suspend fun List<EchoRemotePlaylist>.filterLinkedPlaylistQuery(query: String, searchText: LinkedLibrarySearchTextCache, checkActive: () -> Unit): List<EchoRemotePlaylist> {
    val terms = normalizedSearchTerms(query)
    if (terms.isEmpty()) return this
    return filterIndexed { index, playlist ->
        if (index % 64 == 0) currentCoroutineContext().ensureActive()
        checkActive()
        val searchableText = searchText.text(
            playlist.name,
            playlist.sourceLabel.orEmpty(),
        )
        terms.all(searchableText::contains)
    }
}

private fun List<EchoRemoteTrack>.sortedForLinkedLibrary(
    sortMode: LibraryTrackSortMode,
    randomOrder: LinkedLibraryRandomOrder,
    checkActive: () -> Unit,
): List<EchoRemoteTrack> {
    val compare = when (sortMode) {
        LibraryTrackSortMode.Title,
        LibraryTrackSortMode.FrequentlyPlayed,
        LibraryTrackSortMode.RecentlyPlayed,
        LibraryTrackSortMode.RecentlyUpdated,
        ->
            compareBy<EchoRemoteTrack> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.album.orEmpty().lowercase() }
        LibraryTrackSortMode.Duration ->
            compareByDescending<EchoRemoteTrack> { it.durationMs }
                .thenBy { it.title.lowercase() }
        LibraryTrackSortMode.Random -> return randomOrder.order(this, checkActive)
        LibraryTrackSortMode.Artist ->
            compareBy<EchoRemoteTrack> { it.artist.lowercase() }
                .thenBy { it.album.orEmpty().lowercase() }
                .thenBy { it.title.lowercase() }
        LibraryTrackSortMode.Album ->
            compareBy<EchoRemoteTrack> { it.album.orEmpty().lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.title.lowercase() }
        // Local-only modes are never offered for a linked catalog.
        else -> compareBy<EchoRemoteTrack> { it.title.lowercase() }
            .thenBy { it.artist.lowercase() }.thenBy { it.album.orEmpty().lowercase() }
    }
    return sortedWith { left, right ->
        checkActive()
        compare.compare(left, right)
    }
}

private fun normalizedSearchTerms(query: String): List<String> =
    query.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

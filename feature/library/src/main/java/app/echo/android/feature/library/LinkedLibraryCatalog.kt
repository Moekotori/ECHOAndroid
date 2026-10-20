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

        fun build(
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
        ): LinkedLibraryCatalog {
            val normalizedQuery = query.trim()
            remoteQuery
            val needsTracks = includeSortedTracks || includeArtists || (includeAlbums && remoteAlbums.isEmpty())
            val filteredTracks = if (!needsTracks) {
                emptyList()
            } else {
                tracks.filterLinkedLibraryQuery(normalizedQuery, searchText)
            }
            val filteredPlaylists = if (!includePlaylists) {
                emptyList()
            } else {
                playlists.filterLinkedPlaylistQuery(normalizedQuery, searchText)
            }
            val catalogAlbums = when {
                !includeAlbums -> emptyList()
                remoteAlbums.isNotEmpty() -> remoteAlbums.toAlbumSummaries().filterLinkedAlbumQuery(normalizedQuery, searchText)
                else -> filteredTracks.toLinkedAlbums()
            }
            return LinkedLibraryCatalog(
                tracks = if (includeSortedTracks) {
                    filteredTracks.sortedForLinkedLibrary(sortMode)
                } else {
                    filteredTracks
                },
                albums = catalogAlbums.sortedForLibrary(albumSortMode),
                artists = if (includeArtists) {
                    filteredTracks.toLinkedArtists().sortedForLibrary(artistSortMode)
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

private fun List<EchoRemoteTrack>.toLinkedAlbums(): List<AlbumSummary> =
    groupBy { it.linkedAlbumKey() }
        .values
        .map { albumTracks ->
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

private fun List<EchoRemoteTrack>.toLinkedArtists(): List<ArtistSummary> =
    groupBy { it.linkedArtistKey() }
        .values
        .map { artistTracks ->
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

private fun List<EchoRemoteTrack>.filterLinkedLibraryQuery(query: String, searchText: LinkedLibrarySearchTextCache): List<EchoRemoteTrack> {
    val terms = normalizedSearchTerms(query)
    if (terms.isEmpty()) return this
    return filter { track ->
        val searchableText = searchText.text(
            track.title,
            track.artist,
            track.album.orEmpty(),
        )
        terms.all(searchableText::contains)
    }
}

private fun List<AlbumSummary>.filterLinkedAlbumQuery(query: String, searchText: LinkedLibrarySearchTextCache): List<AlbumSummary> {
    val terms = normalizedSearchTerms(query)
    if (terms.isEmpty()) return this
    return filter { album ->
        val searchableText = searchText.text(
            album.title,
            album.artist.orEmpty(),
            album.albumArtist.orEmpty(),
        )
        terms.all(searchableText::contains)
    }
}

private fun List<EchoRemotePlaylist>.filterLinkedPlaylistQuery(query: String, searchText: LinkedLibrarySearchTextCache): List<EchoRemotePlaylist> {
    val terms = normalizedSearchTerms(query)
    if (terms.isEmpty()) return this
    return filter { playlist ->
        val searchableText = searchText.text(
            playlist.name,
            playlist.sourceLabel.orEmpty(),
        )
        terms.all(searchableText::contains)
    }
}

private fun List<EchoRemoteTrack>.sortedForLinkedLibrary(
    sortMode: LibraryTrackSortMode,
): List<EchoRemoteTrack> =
    when (sortMode) {
        LibraryTrackSortMode.Title,
        LibraryTrackSortMode.FrequentlyPlayed,
        LibraryTrackSortMode.RecentlyPlayed,
        LibraryTrackSortMode.RecentlyUpdated,
        -> sortedWith(
            compareBy<EchoRemoteTrack> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.album.orEmpty().lowercase() },
        )
        LibraryTrackSortMode.Duration -> sortedWith(
            compareByDescending<EchoRemoteTrack> { it.durationMs }
                .thenBy { it.title.lowercase() },
        )
        LibraryTrackSortMode.Random -> shuffled()
        LibraryTrackSortMode.Artist -> sortedWith(
            compareBy<EchoRemoteTrack> { it.artist.lowercase() }
                .thenBy { it.album.orEmpty().lowercase() }
                .thenBy { it.title.lowercase() },
        )
        LibraryTrackSortMode.Album -> sortedWith(
            compareBy<EchoRemoteTrack> { it.album.orEmpty().lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.title.lowercase() },
        )
    }

private fun normalizedSearchTerms(query: String): List<String> =
    query.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

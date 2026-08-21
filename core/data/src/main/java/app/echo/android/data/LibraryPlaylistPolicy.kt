package app.echo.android.data

import app.echo.android.model.library.EchoPlaylist

data class LibraryPlaylistRecord(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val artworkUri: String? = null,
    val updatedAtEpochMs: Long = 0L,
) {
    fun toEchoPlaylist(): EchoPlaylist =
        EchoPlaylist(
            id = id,
            name = name,
            trackIds = trackIds,
            trackCount = trackIds.size,
            artworkUri = artworkUri,
            updatedAtEpochMs = updatedAtEpochMs,
        )
}

data class LibraryPlaylistCatalog(
    val playlists: List<LibraryPlaylistRecord> = emptyList(),
)

object LibraryPlaylistPolicy {
    fun create(
        catalog: LibraryPlaylistCatalog,
        name: String,
        id: String,
        nowEpochMs: Long,
    ): LibraryPlaylistCatalog {
        val playlistId = normalizeId(id) ?: return catalog
        val playlistName = normalizeName(name) ?: return catalog
        if (catalog.playlists.any { it.id == playlistId }) return catalog
        return catalog.copy(
            playlists = catalog.playlists + LibraryPlaylistRecord(
                id = playlistId,
                name = playlistName,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    fun rename(
        catalog: LibraryPlaylistCatalog,
        playlistId: String,
        name: String,
        nowEpochMs: Long,
    ): LibraryPlaylistCatalog {
        val playlistName = normalizeName(name) ?: return catalog
        return catalog.replace(playlistId) { playlist ->
            playlist.copy(name = playlistName, updatedAtEpochMs = nowEpochMs)
        }
    }

    fun delete(
        catalog: LibraryPlaylistCatalog,
        playlistId: String,
    ): LibraryPlaylistCatalog {
        val id = normalizeId(playlistId) ?: return catalog
        return catalog.copy(playlists = catalog.playlists.filterNot { it.id == id })
    }

    fun addTrack(
        catalog: LibraryPlaylistCatalog,
        playlistId: String,
        trackId: String,
        nowEpochMs: Long,
    ): LibraryPlaylistCatalog {
        val id = normalizeId(trackId) ?: return catalog
        return catalog.replace(playlistId) { playlist ->
            if (id in playlist.trackIds) {
                playlist
            } else {
                playlist.copy(
                    trackIds = playlist.trackIds + id,
                    updatedAtEpochMs = nowEpochMs,
                )
            }
        }
    }

    fun removeTrack(
        catalog: LibraryPlaylistCatalog,
        playlistId: String,
        trackId: String,
        nowEpochMs: Long,
    ): LibraryPlaylistCatalog {
        val id = normalizeId(trackId) ?: return catalog
        return catalog.replace(playlistId) { playlist ->
            if (id !in playlist.trackIds) {
                playlist
            } else {
                playlist.copy(
                    trackIds = playlist.trackIds.filterNot { it == id },
                    updatedAtEpochMs = nowEpochMs,
                )
            }
        }
    }

    fun reorderTracks(
        catalog: LibraryPlaylistCatalog,
        playlistId: String,
        fromIndex: Int,
        toIndex: Int,
        nowEpochMs: Long,
    ): LibraryPlaylistCatalog =
        catalog.replace(playlistId) { playlist ->
            val ids = playlist.trackIds
            if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) {
                playlist
            } else {
                val nextIds = ids.toMutableList()
                val moved = nextIds.removeAt(fromIndex)
                nextIds.add(toIndex, moved)
                playlist.copy(trackIds = nextIds, updatedAtEpochMs = nowEpochMs)
            }
        }

    fun trackMemberships(playlist: LibraryPlaylistRecord): List<Pair<String, Int>> =
        playlist.trackIds.mapIndexed { index, trackId -> trackId to index }

    fun newLocalPlaylistId(nowEpochMs: Long, entropy: String): String {
        val suffix = entropy.trim().ifEmpty { nowEpochMs.toString() }
        return "local:$nowEpochMs:$suffix"
    }

    fun normalizeName(name: String): String? =
        name.trim().takeIf { it.isNotEmpty() }?.take(MaxPlaylistNameLength)

    private fun LibraryPlaylistCatalog.replace(
        playlistId: String,
        transform: (LibraryPlaylistRecord) -> LibraryPlaylistRecord,
    ): LibraryPlaylistCatalog {
        val id = normalizeId(playlistId) ?: return this
        val index = playlists.indexOfFirst { it.id == id }
        if (index < 0) return this
        val next = playlists.toMutableList()
        next[index] = transform(next[index])
        return copy(playlists = next)
    }

    private fun normalizeId(value: String): String? =
        value.trim().takeIf { it.isNotEmpty() }

    const val MaxPlaylistNameLength = 80
}

package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class EchoLinkPlaylistArtwork(private val transport: EchoLinkTransport) {
    // Same playlist shares a slot, including during list-to-detail transitions.
    private val slots = List(2) { Mutex() }
    private val cache = object : LinkedHashMap<Pair<Long, String>, String?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, String>, String?>): Boolean =
            size > 128
    }

    suspend fun resolve(
        endpoint: EchoRemoteEndpoint,
        generation: Long,
        playlist: EchoRemotePlaylist,
        loadedTracks: () -> List<EchoRemoteTrack>?,
    ): String? {
        playlist.artworkUrl?.let { return it }
        if (playlist.sourceLabel != "local" || playlist.trackCount <= 0) return null
        val key = generation to playlist.id
        val slot = slots[(playlist.id.hashCode() and Int.MAX_VALUE) % slots.size]
        return slot.withLock {
            synchronized(cache) {
                if (cache.containsKey(key)) return@withLock cache[key]
            }
            val tracks = loadedTracks() ?: withTimeoutOrNull(5_000L) {
                transport.fetchPlaylistTracks(endpoint, playlist.id, page = 1, pageSize = PreviewTracks)
            }?.tracks ?: return@withLock null
            val cover = tracks.asSequence().take(PreviewTracks)
                .firstNotNullOfOrNull { it.artworkUrl?.playlistArtworkOrNull() }
            synchronized(cache) { cache[key] = cover }
            cover
        }
    }

    private companion object {
        const val PreviewTracks = 8
    }
}

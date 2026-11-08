package app.echo.android.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Stream through all search pages. Memory is bounded by a page plus the sync's identity set. */
internal suspend fun SubsonicClient.forEachSongPage(consume: suspend (List<SubsonicSong>) -> Unit): Int {
    for (query in SubsonicSyncPolicy.Search3QueryAttempts) {
        var offset = 0
        var previousPage: Pair<String, String>? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = withContext(LibraryScanDispatchers.Remote) {
                request("search3.view", listOf("query" to query, "songCount" to "500", "songOffset" to offset.toString(),
                    "albumCount" to "0", "artistCount" to "0"))
                    .requiredUserObject("searchResult3").validUserRows("song", 500).map { it.toSubsonicSong() }
            }
            if (rows.isEmpty()) break
            val page = rows.first().id to rows.last().id
            check(page != previousPage) { "Subsonic server repeated a sync page" }
            previousPage = page
            consume(rows)
            offset = Math.addExact(offset, rows.size)
        }
        if (offset > 0) return offset
    }
    return 0
}

internal class SubsonicSongMetadata(albums: List<SubsonicAlbum>) {
    private val byId = LinkedHashMap<String, SubsonicAlbum?>(2048, .75f, true).apply {
        albums.take(2048).forEach { put(it.id, it) }
    }
    fun rememberAlbum(album: SubsonicAlbum) {
        byId[album.id] = album
        while (byId.size > 2048) byId.remove(byId.keys.first())
    }

    var failedAlbumLookups: Int = 0
        private set

    suspend fun enrichMissingCovers(client: SubsonicClient, songs: List<SubsonicSong>): List<SubsonicSong> {
        val missing = songs.asSequence().filter { it.coverArt.isNullOrBlank() }.mapNotNull { it.albumId }
            .filterNot { byId.containsKey(it) }.distinct().toList()
        for (chunk in missing.chunked(4)) {
            val fetched = coroutineScope {
                chunk.map { id -> async(LibraryScanDispatchers.Remote) {
                    val album = try { client.fetchAlbumMetadata(id) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    id to album
                } }.awaitAll()
            }
            fetched.forEach { (id, album) ->
                if (album == null) failedAlbumLookups++
                byId[id] = album
                while (byId.size > 2048) byId.remove(byId.keys.first())
            }
        }
        return songs.map(::enrich)
    }
    private val byTags = albums.groupBy { it.name.normalizedForSearch() to it.artist?.normalizedForSearch() }
    fun enrich(song: SubsonicSong): SubsonicSong {
        val album = if (song.albumId != null) byId[song.albumId] else
            byTags[song.album.orEmpty().normalizedForSearch() to
                (song.albumArtist ?: song.artist).normalizedForSearch()]?.singleOrNull()
        return if (album == null) song else song.copy(albumId = song.albumId ?: album.id,
            coverArt = song.coverArt ?: album.coverArt)
    }
}

package app.echo.android.data

import android.content.Context
import androidx.room.withTransaction
import app.echo.android.model.library.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Serialize server user-state snapshots and mutations, including their local commit. */
internal val subsonicUserStateGate = Mutex()

class SubsonicUserLibrary(private val database: EchoLibraryDatabase, context: Context) {
    constructor(repository: EchoLibraryRepository) : this(repository.database, repository.appContext)
    private val context = context.applicationContext
    private val reads = Semaphore(2)
    private fun client(endpoint: SubsonicEndpoint) = SubsonicClient(endpoint, appContext = context)

    suspend fun read(endpoint: SubsonicEndpoint, query: NavidromeQuery): NavidromePage = withContext(Dispatchers.IO) {
        reads.withPermit {
            currentCoroutineContext().ensureActive()
            val result = client(endpoint).readUserLibrary(endpoint, query)
            currentCoroutineContext().ensureActive()
            index(endpoint, result.entries.mapNotNull { it.track })
            result
        }
    }

    suspend fun queue(endpoint: SubsonicEndpoint): NavidromePage = withContext(Dispatchers.IO) {
        val result = client(endpoint).readQueue(endpoint)
        currentCoroutineContext().ensureActive()
        index(endpoint, result.entries.mapNotNull { it.track })
        result
    }

    suspend fun information(endpoint: SubsonicEndpoint, entry: NavidromeEntry): NavidromeInformation =
        withContext(Dispatchers.IO) { client(endpoint).information(endpoint, entry) }

    suspend fun playlist(endpoint: SubsonicEndpoint, id: String): NavidromePlaylistSettings =
        withContext(Dispatchers.IO) {
            val result = client(endpoint).playlistSettings(endpoint, id)
            currentCoroutineContext().ensureActive()
            persistPlaylist(endpoint, result)
            result
        }

    suspend fun refreshPlaylistCatalog(endpoint: SubsonicEndpoint) = withContext(Dispatchers.IO) {
        val client = client(endpoint)
        val rows = client.fetchPlaylists()
        currentCoroutineContext().ensureActive()
        val dao = database.playlistDao()
        database.withTransaction {
            val existing = dao.getPlaylistsBySource(endpoint.sourceId).associateBy { it.id }
            val seen = rows.mapTo(HashSet()) { "${endpoint.sourceId}:playlist:${it.id}" }
            rows.forEach { row ->
                val id = "${endpoint.sourceId}:playlist:${row.id}"
                val old = existing[id]
                dao.upsertPlaylist(LibraryPlaylistEntity(id, row.name, endpoint.sourceId,
                    client.coverArtUrl(row.coverArt) ?: old?.artworkUri, old?.trackCount ?: row.songCount,
                    old?.updatedAtEpochMs ?: System.currentTimeMillis()))
            }
            existing.keys.filterNot(seen::contains).forEach { dao.deletePlaylist(it) }
        }
    }

    suspend fun execute(endpoint: SubsonicEndpoint, command: NavidromeCommand) = withContext(Dispatchers.IO) {
        subsonicUserStateGate.withLock {
            val client = client(endpoint)
            if (command is NavidromeCommand.Playlist) require(client.playlistSettings(endpoint, command.id).writable)
            client.executeUserCommand(command)
            currentCoroutineContext().ensureActive()
            if (command is NavidromeCommand.Favorite && command.kind == NavidromeKind.Track) {
                val id = "${endpoint.sourceId}:song:${command.id}"
                if (database.trackDao().getTrackById(id)?.source == endpoint.sourceId) {
                    if (command.value) database.playlistDao().upsertFavorite(LibraryFavoriteEntity(id, System.currentTimeMillis()))
                    else database.playlistDao().deleteFavorite(id)
                }
            }
            if (command is NavidromeCommand.Playlist) {
                val playlist = client.playlistSettings(endpoint, command.id)
                persistPlaylist(endpoint, playlist)
            }
        }
    }

    private suspend fun persistPlaylist(endpoint: SubsonicEndpoint, playlist: NavidromePlaylistSettings) {
        index(endpoint, playlist.songs.mapNotNull { it.track })
        val ids = playlist.songs.map { requireNotNull(it.track).id }.distinct()
        database.playlistDao().replacePlaylist(LibraryPlaylistEntity(
            "${endpoint.sourceId}:playlist:${playlist.id}", playlist.name, endpoint.sourceId,
            playlist.songs.firstOrNull()?.artworkUri, ids.size, System.currentTimeMillis()),
            ids.mapIndexed { position, id -> LibraryPlaylistTrackEntity("${endpoint.sourceId}:playlist:${playlist.id}", id, position) })
    }

    suspend fun saveQueue(endpoint: SubsonicEndpoint, ids: List<String>, currentIndex: Int, positionMs: Long) =
        withContext(Dispatchers.IO) { client(endpoint).saveQueue(ids, currentIndex, positionMs) }

    /** Only reconcile this account's songs, and only after a validated server snapshot. */
    suspend fun syncFavorites(endpoint: SubsonicEndpoint) = withContext(Dispatchers.IO) {
        subsonicUserStateGate.withLock {
            val desired = client(endpoint).request("getStarred2.view").requiredUserObject("starred2")
                .validUserRows("song", 50_000).map { "${endpoint.sourceId}:song:${it.getString("id")}" }.toHashSet()
            currentCoroutineContext().ensureActive()
            val known = database.trackDao().getIdsFromSource(endpoint.sourceId).toHashSet()
            database.withTransaction {
                val dao = database.playlistDao()
                val previous = dao.getFavoriteTrackIdsFromSource(endpoint.sourceId)
                previous.filterNot(desired::contains).chunked(500).forEach { dao.deleteFavorites(it) }
                val now = System.currentTimeMillis()
                val previousIds = previous.toHashSet()
                desired.filter(known::contains).filterNot(previousIds::contains).chunked(500)
                    .forEach { ids -> dao.upsertFavorites(ids.map { LibraryFavoriteEntity(it, now) }) }
            }
        }
    }

    /** A search page becomes playable without forcing a whole-library sync. Preserve edited tags. */
    private suspend fun index(endpoint: SubsonicEndpoint, tracks: List<EchoTrack>) {
        if (tracks.isEmpty()) return
        val dao = database.trackDao()
        val now = System.currentTimeMillis()
        tracks.distinctBy { it.id }.chunked(100).forEach { chunk ->
            currentCoroutineContext().ensureActive()
            database.withTransaction {
                val existing = dao.getTracksByIds(chunk.map { it.id }).associateBy { it.id }
                val rows = chunk.filterNot { it.id in existing }.map { track ->
                    LibraryTrackEntity(
                        id = track.id, contentUri = track.uri, title = track.title, artist = track.artist,
                        album = track.album, albumArtist = track.albumArtist, artworkUri = track.artworkUri,
                        durationMs = track.durationMs, trackNumber = track.trackNumber, discNumber = track.discNumber,
                        year = track.year, mimeType = track.mimeType, sizeBytes = track.sizeBytes,
                        sampleRateHz = track.sampleRateHz, source = endpoint.sourceId, dateModifiedSeconds = now / 1000,
                        genre = track.genre, composer = track.composer,
                        fileName = track.fileName,
                    ).withScanMetadata(now)
                }
                dao.upsertScanBatch(rows)
            }
        }
    }
}

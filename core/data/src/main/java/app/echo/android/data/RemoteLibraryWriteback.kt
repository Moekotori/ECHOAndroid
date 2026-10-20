package app.echo.android.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.room.withTransaction
import app.echo.android.model.library.EchoPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Explicit Subsonic writeback. Room changes happen only after the server accepts the mutation. */
class RemoteLibraryWriteback(
    private val database: EchoLibraryDatabase,
    private val context: android.content.Context,
) {
    constructor(repository: EchoLibraryRepository) : this(repository.database, repository.appContext)

    fun playlists(source: String) = database.playlistDao().observePlaylists(source)
        .map { rows -> rows.map { it.toEchoPlaylist() } }

    fun candidates(source: String, query: String) = Pager(PagingConfig(pageSize = 50)) {
        database.trackDao().pageRemoteWritebackCandidates(source, query.trim())
    }.flow

    suspend fun create(endpoint: SubsonicEndpoint, name: String): EchoPlaylist = withContext(Dispatchers.IO) {
        val normalized = requireNotNull(LibraryPlaylistPolicy.normalizeName(name))
        val remoteId = client(endpoint).createPlaylist(normalized)
        val id = "${endpoint.sourceId}:playlist:$remoteId"
        val now = System.currentTimeMillis()
        database.playlistDao().upsertPlaylist(LibraryPlaylistEntity(id, normalized, endpoint.sourceId, null, 0, now))
        EchoPlaylist(id, normalized, source = endpoint.sourceId, updatedAtEpochMs = now)
    }

    suspend fun rename(endpoint: SubsonicEndpoint, id: String, name: String) = withContext(Dispatchers.IO) {
        val remoteId = playlistId(endpoint, id)
        val normalized = requireNotNull(LibraryPlaylistPolicy.normalizeName(name))
        client(endpoint).renamePlaylist(remoteId, normalized)
        database.playlistDao().setName(id, normalized, System.currentTimeMillis())
    }

    suspend fun delete(endpoint: SubsonicEndpoint, id: String) = withContext(Dispatchers.IO) {
        client(endpoint).deletePlaylist(playlistId(endpoint, id))
        database.playlistDao().deletePlaylist(id)
    }

    suspend fun add(endpoint: SubsonicEndpoint, id: String, trackId: String) = withContext(Dispatchers.IO) {
        val remoteId = playlistId(endpoint, id)
        val songId = songId(endpoint, trackId)
        val dao = database.playlistDao()
        val client = client(endpoint)
        // A previous accepted request may have lost its response. Avoid a duplicate on manual retry.
        if (client.fetchPlaylistSongs(remoteId).none { it.id == songId }) {
            client.addPlaylistSongs(remoteId, listOf(songId))
        }
        database.withTransaction {
            if (dao.membership(id, trackId) == null) dao.insertPlaylistTracks(listOf(
                LibraryPlaylistTrackEntity(id, trackId, dao.lastPosition(id) + 1)))
            dao.refreshMembershipSummary(id, System.currentTimeMillis())
        }
    }

    suspend fun remove(endpoint: SubsonicEndpoint, id: String, trackId: String) = withContext(Dispatchers.IO) {
        client(endpoint).removePlaylistSong(playlistId(endpoint, id), songId(endpoint, trackId))
        database.withTransaction {
            database.playlistDao().removeMember(id, trackId)
            database.playlistDao().refreshMembershipSummary(id, System.currentTimeMillis())
        }
    }

    suspend fun toggleFavorite(endpoint: SubsonicEndpoint, trackId: String): Boolean = withContext(Dispatchers.IO) {
        val starred = !database.playlistDao().isFavorite(trackId)
        setFavorite(endpoint, trackId, starred)
        starred
    }

    suspend fun setFavorite(endpoint: SubsonicEndpoint, trackId: String, starred: Boolean) = withContext(Dispatchers.IO) {
        val remoteId = songId(endpoint, trackId)
        val dao = database.playlistDao()
        client(endpoint).setStarred(remoteId, starred)
        if (starred) dao.upsertFavorite(LibraryFavoriteEntity(trackId, System.currentTimeMillis()))
        else dao.deleteFavorite(trackId)
    }

    private suspend fun playlistId(endpoint: SubsonicEndpoint, id: String): String {
        val prefix = "${endpoint.sourceId}:playlist:"
        require(id.startsWith(prefix) && id.length > prefix.length)
        require(database.playlistDao().getPlaylist(id)?.source == endpoint.sourceId)
        return id.removePrefix(prefix)
    }

    private suspend fun songId(endpoint: SubsonicEndpoint, id: String): String {
        val prefix = "${endpoint.sourceId}:song:"
        require(id.startsWith(prefix) && id.length > prefix.length)
        require(database.trackDao().getTrackById(id)?.source == endpoint.sourceId)
        return id.removePrefix(prefix)
    }

    private fun client(endpoint: SubsonicEndpoint) = SubsonicClient(endpoint, appContext = context.applicationContext)
}

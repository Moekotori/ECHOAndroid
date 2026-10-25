package app.echo.android.data

import androidx.room.withTransaction
import app.echo.android.model.library.EchoPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** A bounded undo record, never a copy of the playlist. Neighbours survive unrelated edits. */
data class RemovedPlaylistTrack internal constructor(
    val playlistId: String, val trackId: String, internal val position: Int,
    internal val previousId: String?, internal val nextId: String?,
)

suspend fun EchoLibraryRepository.removePlaylistTrackUndoable(id: String, trackId: String): RemovedPlaylistTrack? =
    withContext(Dispatchers.IO) {
        database.withTransaction {
            val dao = database.playlistDao()
            val playlist = dao.getPlaylist(id) ?: return@withTransaction null
            if (!EchoPlaylist(playlist.id, playlist.name, source = playlist.source).canEdit) return@withTransaction null
            val member = dao.membership(id, trackId) ?: return@withTransaction null
            val undo = RemovedPlaylistTrack(id, trackId, member.position,
                dao.previousMembership(id, member.position)?.trackId, dao.nextMembership(id, member.position)?.trackId)
            check(dao.removeMember(id, trackId) > 0)
            dao.refreshMembershipSummary(id, System.currentTimeMillis())
            undo
        }
    }

suspend fun EchoLibraryRepository.restorePlaylistTrack(removed: RemovedPlaylistTrack): Boolean = withContext(Dispatchers.IO) {
    database.withTransaction {
        val dao = database.playlistDao()
        val id = removed.playlistId
        val playlist = dao.getPlaylist(id) ?: return@withTransaction false
        if (!EchoPlaylist(playlist.id, playlist.name, source = playlist.source).canEdit) return@withTransaction false
        if (dao.membership(id, removed.trackId) != null) return@withTransaction true
        if (database.trackDao().getTracksByIds(listOf(removed.trackId)).isEmpty()) return@withTransaction false
        val next = removed.nextId?.let { dao.membership(id, it) }
        val previous = removed.previousId?.let { dao.membership(id, it) }
        val position = next?.position ?: previous?.position?.plus(1) ?: removed.position.coerceAtMost(dao.lastPosition(id) + 1)
        dao.shiftPositions(id, position, Int.MAX_VALUE, 1)
        dao.insertPlaylistTracks(listOf(LibraryPlaylistTrackEntity(id, removed.trackId, position)))
        dao.refreshMembershipSummary(id, System.currentTimeMillis())
        true
    }
}

/** Move by stable IDs so a paging refresh cannot apply a stale UI index to another song. */
suspend fun EchoLibraryRepository.movePlaylistTrackTo(id: String, trackId: String, targetId: String): Boolean = withContext(Dispatchers.IO) {
    if (!observePlaylistOrder(id).first().isManual) return@withContext false
    database.withTransaction {
        val dao = database.playlistDao()
        val playlist = dao.getPlaylist(id) ?: return@withTransaction false
        if (!EchoPlaylist(playlist.id, playlist.name, source = playlist.source).canEdit) return@withTransaction false
        val from = dao.membership(id, trackId) ?: return@withTransaction false
        val to = dao.membership(id, targetId) ?: return@withTransaction false
        if (from.trackId == to.trackId) return@withTransaction true
        if (from.position < to.position) dao.shiftPositions(id, from.position + 1, to.position, -1)
        else dao.shiftPositions(id, to.position, from.position - 1, 1)
        dao.setPosition(id, trackId, to.position)
        dao.refreshMembershipSummary(id, System.currentTimeMillis())
        true
    }
}

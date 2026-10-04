package app.echo.android.data

import androidx.paging.Pager
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteProgram
import app.echo.android.model.library.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.UUID
import kotlin.coroutines.coroutineContext

fun EchoLibraryRepository.observePlaylistOrder(id: String) = playlistOrderStore.observe(id)

fun EchoLibraryRepository.observePlaylistStats(id: String) = database.playlistDao().observeStats(playlistStatsQuery(id)).distinctUntilChanged()

internal fun playlistStatsQuery(id: String): SupportSQLiteQuery {
    val base = playlistTrackQuery(id, PlaylistTrackOrder())
    // Ordering has no role in totals; avoid sorting every member for an aggregate.
    val select = base.sql.substringBeforeLast(" ORDER BY ")
    return object : SupportSQLiteQuery {
        override val sql = "SELECT COUNT(*) AS trackCount, COALESCE(SUM(MAX(durationMs, 0)), 0) AS durationMs FROM ($select) tracks"
        override val argCount = base.argCount
        override fun bindTo(statement: SupportSQLiteProgram) = base.bindTo(statement)
    }
}

suspend fun EchoLibraryRepository.setPlaylistOrder(id: String, order: PlaylistTrackOrder) =
    withContext(Dispatchers.IO) { playlistOrderStore.save(id, order) }

internal fun playlistTrackQuery(id: String, order: PlaylistTrackOrder, limit: Int? = null, anchorId: String? = null): SimpleSQLiteQuery =
    if (id.startsWith(EchoSmartPlaylistRule.IdPrefix)) {
        smartPlaylistQuery(id, limit, anchorId, playlistOrder = order)
    } else LibraryCollectionPlaybackQuery.build(LibraryPlaybackOrigin.Playlist(id), anchorId, limit, order)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun EchoLibraryRepository.orderedPlaylistPaging(id: String) =
    observePlaylistOrder(id).flatMapLatest { order ->
        Pager(config = androidx.paging.PagingConfig(pageSize = 80, initialLoadSize = 80,
            prefetchDistance = 24, maxSize = 400, enablePlaceholders = true),
            pagingSourceFactory = {
                val query = playlistTrackQuery(id, order)
                if (id.startsWith(EchoSmartPlaylistRule.IdPrefix)) database.experienceDao().pageRuleTracks(query)
                else database.playlistDao().pageOrderedTracks(query)
            }).flow
    }

suspend fun EchoLibraryRepository.preparePlaylistImport(text: String): PlaylistImportPreview = withContext(Dispatchers.IO) {
    val entries = PlaylistFileCodec.parse(text)
    require(entries.isNotEmpty()) { "Empty playlist" }
    val matcher = PlaylistImportMatcher(entries)
    var after = ""
    while (true) {
        coroutineContext.ensureActive()
        val batch = database.trackDao().playlistMatchBatch(after)
        matcher.add(batch)
        if (batch.size < 512) break
        after = batch.last().id
        yield()
    }
    matcher.preview()
}

suspend fun EchoLibraryRepository.createImportedPlaylist(name: String, preview: PlaylistImportPreview): EchoPlaylist? {
    require(preview.trackIds.size <= PlaylistImportLimits.MaxEntries)
    return createOrderedLocalPlaylist(name, preview.trackIds)
}

private suspend fun EchoLibraryRepository.createOrderedLocalPlaylist(name: String, ids: List<String>): EchoPlaylist? =
    withContext(Dispatchers.IO) {
        val normalized = LibraryPlaylistPolicy.normalizeName(name) ?: return@withContext null
        val unique = ids.distinct()
        require(unique.size <= 10_000) { "Playlist exceeds 10,000 tracks" }
        database.withTransaction {
            val existing = unique.chunked(400).flatMap { database.trackDao().getTracksByIds(it).map { row -> row.id } }.toSet()
            val valid = unique.filter { it in existing }
            if (valid.isEmpty()) return@withTransaction null
            val now = System.currentTimeMillis()
            val id = LibraryPlaylistPolicy.newLocalPlaylistId(now, UUID.randomUUID().toString())
            val dao = database.playlistDao()
            dao.upsertPlaylist(LibraryPlaylistEntity(id, normalized, LibrarySource.MediaStore.id, null, valid.size, now))
            valid.chunked(400).forEachIndexed { batch, tracks ->
                dao.insertPlaylistTracks(tracks.mapIndexed { index, trackId -> LibraryPlaylistTrackEntity(id, trackId, batch * 400 + index) })
            }
            dao.refreshMembershipSummary(id, now)
            dao.getPlaylist(id)?.let { EchoPlaylist(it.id, it.name, trackCount = it.trackCount, artworkUri = it.artworkUri, updatedAtEpochMs = now) }
        }
    }

suspend fun EchoLibraryRepository.copyPlaylist(id: String, name: String): EchoPlaylist? = withContext(Dispatchers.IO) {
    val order = observePlaylistOrder(id).first()
    val tracks = database.trackDao().queryTracks(playlistTrackQuery(id, order, 10_001))
    require(tracks.size <= 10_000) { "Playlist exceeds 10,000 tracks" }
    createOrderedLocalPlaylist(name, tracks.map { it.id })
}

suspend fun EchoLibraryRepository.addTracksToPlaylist(id: String, trackIds: List<String>): Int = withContext(Dispatchers.IO) {
    require(trackIds.size <= PlaylistImportLimits.MaxEntries)
    val ids = trackIds.distinct()
    database.withTransaction {
        val dao = database.playlistDao()
        val playlist = dao.getPlaylist(id) ?: return@withTransaction 0
        if (!EchoPlaylist(playlist.id, playlist.name, source = playlist.source).canEdit) return@withTransaction 0
        val valid = ids.chunked(400).flatMap { database.trackDao().getTracksByIds(it).map { row -> row.id } }.toSet()
        val existing = ids.chunked(400).flatMap { dao.existingMembers(id, it) }.toSet()
        val additions = ids.filter { it in valid && it !in existing }
        val start = dao.lastPosition(id) + 1
        additions.chunked(400).forEachIndexed { batch, tracks ->
            dao.insertPlaylistTracks(tracks.mapIndexed { index, trackId -> LibraryPlaylistTrackEntity(id, trackId, start + batch * 400 + index) })
        }
        if (additions.isNotEmpty()) dao.refreshMembershipSummary(id, System.currentTimeMillis())
        additions.size
    }
}

suspend fun EchoLibraryRepository.movePlaylistTrack(id: String, trackId: String, direction: Int): Boolean = withContext(Dispatchers.IO) {
    require(direction == -1 || direction == 1)
    if (!observePlaylistOrder(id).first().isManual) return@withContext false
    database.withTransaction {
        val dao = database.playlistDao()
        val playlist = dao.getPlaylist(id) ?: return@withTransaction false
        if (!EchoPlaylist(playlist.id, playlist.name, source = playlist.source).canEdit) return@withTransaction false
        val current = dao.membership(id, trackId) ?: return@withTransaction false
        val neighbor = if (direction < 0) dao.previousMembership(id, current.position) else dao.nextMembership(id, current.position)
        neighbor ?: return@withTransaction false
        dao.setPosition(id, current.trackId, neighbor.position)
        dao.setPosition(id, neighbor.trackId, current.position)
        dao.refreshMembershipSummary(id, System.currentTimeMillis())
        true
    }
}

suspend fun EchoLibraryRepository.setPlaylistArtwork(id: String, uri: String): Boolean = withContext(Dispatchers.IO) {
    database.withTransaction {
        val playlist = database.playlistDao().getPlaylist(id) ?: return@withTransaction false
        if (!EchoPlaylist(playlist.id, playlist.name, source = playlist.source).canEdit) return@withTransaction false
        database.playlistDao().setArtwork(id, uri, System.currentTimeMillis())
        true
    }
}

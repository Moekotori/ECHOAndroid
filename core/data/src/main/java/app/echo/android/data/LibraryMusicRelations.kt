package app.echo.android.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.room.withTransaction
import app.echo.android.model.library.EchoMusicRelation
import app.echo.android.model.library.EchoMusicRelationKind
import app.echo.android.model.library.EchoMusicRelationOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/** Page-owned reads; file inspection is explicit, serial, cancellable and never writes media. */
class LibraryMusicRelations internal constructor(
    private val database: EchoLibraryDatabase,
    private val readTags: (String, String?) -> AudioTagFields?,
) {
    private val mutex = Mutex()
    private var indexed = false
    private val dao get() = database.musicRelationDao()

    fun overview(selection: List<EchoMusicRelation>) = combine(dao.coverage(MusicRelationQueries.coverage(selection)),
        dao.counts(MusicRelationQueries.counts(selection))) { coverage, rows ->
        EchoMusicRelationOverview(coverage.trackCount, coverage.taggedTrackCount,
            rows.mapNotNull { row -> EchoMusicRelationKind.entries.firstOrNull { it.name == row.kind }?.let { it to row.entityCount } }.toMap())
    }.flowOn(Dispatchers.IO)

    fun entities(kind: EchoMusicRelationKind, selection: List<EchoMusicRelation>, query: String) =
        Pager(PagingConfig(pageSize = 40, initialLoadSize = 40, maxSize = 200)) {
            dao.entities(MusicRelationQueries.build(kind, selection, query))
        }.flow

    fun tracks(selection: List<EchoMusicRelation>, query: String) =
        Pager(PagingConfig(pageSize = 40, initialLoadSize = 40, maxSize = 200)) {
            dao.tracks(MusicRelationQueries.build(null, selection, query))
        }.flow.map { page -> page.map(LibraryTrackEntity::toEchoTrack) }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (indexed) return@withLock
            var after = ""
            while (true) {
                coroutineContext.ensureActive()
                val page = database.withTransaction {
                    val current = dao.localBatch(after)
                    replaceIndex(current)
                    current
                }
                if (page.isEmpty()) break
                after = page.last().id
                yield()
            }
            indexed = true
        }
    }

    /** Returns inspected / unreadable counts without retaining covers or lyrics between files. */
    suspend fun readFileTags(onProgress: (Int, Int) -> Unit): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            var after = ""
            var inspected = 0
            var unreadable = 0
            while (true) {
                coroutineContext.ensureActive()
                val page = dao.localBatch(after)
                if (page.isEmpty()) break
                val facts = mutableListOf<Pair<LibraryTrackEntity, String?>>()
                for (original in page) {
                    coroutineContext.ensureActive()
                    val tags = readTags(original.contentUri, original.mimeType)
                    if (tags == null) unreadable++ else facts += original to tags.musicRelations.encoded()
                    inspected++
                }
                // One bounded commit per page avoids invalidating paged readers for every file.
                database.withTransaction {
                    val currentById = database.trackDao().getTracksByIds(page.map { it.id }).associateBy { it.id }
                    val updates = facts.mapNotNull { (original, json) ->
                        val current = currentById[original.id]
                        if (current != null && current.contentUri == original.contentUri &&
                            current.fingerprint == original.fingerprint && current.dateModifiedSeconds == original.dateModifiedSeconds) {
                            dao.setMetadata(current.id, json)
                            current.copy(musicRelationsJson = json)
                        } else null
                    }
                    replaceIndex(updates)
                }
                onProgress(inspected, unreadable)
                after = page.last().id
                yield()
            }
            onProgress(inspected, unreadable)
            indexed = true
        }
    }

    private suspend fun replaceIndex(tracks: List<LibraryTrackEntity>) {
        if (tracks.isEmpty()) return
        val trackDao = database.trackDao()
        trackDao.deleteMusicRelationMemberships(tracks.map { it.id })
        tracks.flatMap(::musicRelationMemberships).chunked(500).forEach { trackDao.insertMusicRelationMemberships(it) }
    }
}

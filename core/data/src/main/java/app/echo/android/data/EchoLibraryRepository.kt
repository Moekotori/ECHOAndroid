package app.echo.android.data

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSortMode
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.LibraryStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class EchoLibraryRepository(
    private val database: EchoLibraryDatabase,
    private val scanner: MediaStoreTrackScanner,
) {
    fun pagedTracks(query: String? = null): Flow<PagingData<LibraryTrackEntity>> =
        flow {
            val dao = database.trackDao()
            val trimmedQuery = query?.trim().orEmpty()
            val matchQuery = sanitizeFtsQuery(trimmedQuery)
            val rankQuery = ftsRankQuery(trimmedQuery)
            val pagingSourceFactory = when {
                trimmedQuery.isBlank() -> {
                    { dao.pageTracks() }
                }
                matchQuery == null -> {
                    { dao.pageTracksByLike(trimmedQuery, rankQuery) }
                }
                canUseFts(dao, matchQuery) -> {
                    { dao.pageTracksByFts(matchQuery, rankQuery) }
                }
                else -> {
                    { dao.pageTracksByLike(trimmedQuery, rankQuery) }
                }
            }

            emitAll(
                Pager(
                    config = PagingConfig(
                        pageSize = 60,
                        prefetchDistance = 20,
                        enablePlaceholders = false,
                    ),
                    pagingSourceFactory = pagingSourceFactory,
                ).flow,
            )
        }.flowOn(Dispatchers.IO)

    fun observeLibraryStats(): Flow<LibraryStats> =
        database.trackDao().observeLibraryStats()
            .flowOn(Dispatchers.IO)

    fun observeRecommendedTracks(limit: Int = RecommendedTrackLimit): Flow<List<LibraryTrackEntity>> =
        database.trackDao().observeRecommendedTracks(limit)
            .flowOn(Dispatchers.IO)

    fun observeRecentlyAddedAlbums(limit: Int = RecentAlbumLimit): Flow<List<AlbumSummary>> =
        database.trackDao().observeRecentlyAddedAlbums(limit)
            .flowOn(Dispatchers.IO)

    fun pagedAlbums(
        query: String? = null,
        sort: AlbumSortMode = AlbumSortMode.Title,
    ): Flow<PagingData<AlbumSummary>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = {
                database.trackDao().pageAlbums(query?.trim()?.takeIf { it.isNotBlank() }, sort.name)
            },
        ).flow

    fun pagedArtists(
        query: String? = null,
        sort: ArtistSortMode = ArtistSortMode.Name,
    ): Flow<PagingData<ArtistSummary>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = {
                database.trackDao().pageArtists(query?.trim()?.takeIf { it.isNotBlank() }, sort.name)
            },
        ).flow

    fun pagedAlbumTracks(albumKey: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = { database.trackDao().pageTracksByAlbum(albumKey) },
        ).flow

    fun pagedArtistTracks(artistKey: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = { database.trackDao().pageTracksByArtist(artistKey) },
        ).flow

    suspend fun albumTracks(albumKey: String): List<LibraryTrackEntity> =
        database.trackDao().getTracksByAlbum(albumKey)

    suspend fun artistTracks(artistKey: String): List<LibraryTrackEntity> =
        database.trackDao().getTracksByArtist(artistKey)

    suspend fun queueAroundTrack(
        query: String?,
        anchorTrackId: String,
        limit: Int = TrackQueueLimit,
    ): List<LibraryTrackEntity> {
        val dao = database.trackDao()
        val safeLimit = limit.coerceAtLeast(1)
        val anchor = dao.getTrackById(anchorTrackId)
        val candidates = trackQueueCandidates(
            dao = dao,
            query = query,
            limit = safeLimit,
        )
        return withAnchorTrack(anchor, candidates, safeLimit)
    }

    suspend fun albumSummaryForTrack(trackId: String): AlbumSummary? {
        val track = database.trackDao().getTrackById(trackId) ?: return null
        return database.trackDao().getAlbumSummary(track.albumKey())
    }

    suspend fun artistSummaryForTrack(trackId: String): ArtistSummary? {
        val track = database.trackDao().getTrackById(trackId) ?: return null
        return database.trackDao().getArtistSummary(track.artistKey())
    }

    suspend fun trackForLyrics(trackId: String): LibraryTrackEntity? =
        database.trackDao().getTrackById(trackId)

    suspend fun albumTracksForPlayback(
        albumKey: String,
        limit: Int = AggregationQueueLimit,
    ): List<LibraryTrackEntity> =
        database.trackDao().getAlbumTracksForPlayback(albumPlaybackQuery(albumKey, limit.coerceAtLeast(1)))

    suspend fun artistTracksForPlayback(
        artistKey: String,
        limit: Int = AggregationQueueLimit,
    ): List<LibraryTrackEntity> =
        database.trackDao().getArtistTracksForPlayback(artistPlaybackQuery(artistKey, limit.coerceAtLeast(1)))

    fun refreshMediaStoreSnapshot(
        relativePathPrefix: String? = null,
        batchSize: Int = ScanBatchSize,
    ): Flow<LibraryScanProgress> = flow {
        val dao = database.trackDao()
        val source = LibrarySource.MediaStore.id
        val normalizedRelativePath = normalizeRelativePathPrefix(relativePathPrefix)
        val relativePathLike = normalizedRelativePath?.let { "${escapeSqlLikeArgument(it)}%" }
        val scanRunId = System.currentTimeMillis()
        var progress = LibraryScanProgress(phase = LibraryScanPhase.Preparing)
        var insertedCount = 0
        var updatedCount = 0
        var scannedCount = 0
        var totalCount: Int? = null
        var lastProgressEmitCount = 0

        suspend fun emitProgress(
            phase: LibraryScanPhase = progress.phase,
            currentTitle: String? = progress.currentTitle,
            deletedCount: Int = progress.deletedCount,
            error: String? = null,
            isCompleted: Boolean = false,
        ) {
            progress = LibraryScanProgress(
                phase = phase,
                scannedCount = scannedCount,
                insertedCount = insertedCount,
                updatedCount = updatedCount,
                deletedCount = deletedCount,
                totalCount = totalCount,
                currentTitle = currentTitle,
                error = error,
                isCompleted = isCompleted,
            )
            emit(progress)
        }

        try {
            emitProgress()
            coroutineContext.ensureActive()

            emitProgress(phase = LibraryScanPhase.Diffing)
            val existingFingerprints = if (relativePathLike == null) {
                dao.getExistingMediaStoreFingerprints(source)
            } else {
                dao.getExistingMediaStoreFingerprintsInRelativePath(source, relativePathLike)
            }
                .associateBy(TrackFingerprint::id)

            emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
            scanner.scanAudio(
                batchSize = batchSize,
                relativePathPrefix = normalizedRelativePath,
                onTotalCount = { count ->
                    totalCount = count
                    emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
                },
                onProgress = { count, currentTrack ->
                    scannedCount = count
                    if (count == 0 || count - lastProgressEmitCount >= ProgressEmitStride) {
                        lastProgressEmitCount = count
                        emitProgress(
                            phase = LibraryScanPhase.QueryingMediaStore,
                            currentTitle = currentTrack?.title,
                        )
                    }
                },
                onBatch = { batch ->
                    coroutineContext.ensureActive()
                    val inserts = ArrayList<LibraryTrackEntity>(batch.size)
                    val updates = ArrayList<LibraryTrackEntity>(batch.size)
                    val unchangedIds = ArrayList<String>(batch.size)

                    batch.forEach { rawTrack ->
                        val track = rawTrack.withScanMetadata(scanRunId)
                        val existing = existingFingerprints[track.id]
                        when {
                            existing == null -> inserts += track
                            existing.fingerprint != track.fingerprint -> updates += track
                            else -> unchangedIds += track.id
                        }
                    }

                    emitProgress(phase = LibraryScanPhase.WritingDatabase)
                    (inserts + updates).chunked(DatabaseBatchSize).forEach { chunk ->
                        dao.upsertBatchWithFts(chunk)
                    }
                    unchangedIds.chunked(DatabaseBatchSize).forEach { ids ->
                        dao.markSeen(ids, scanRunId)
                    }
                    insertedCount += inserts.size
                    updatedCount += updates.size
                    lastProgressEmitCount = scannedCount
                    emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
                },
            )

            coroutineContext.ensureActive()
            emitProgress(phase = LibraryScanPhase.CleaningRemoved)
            val missingTrackIds = if (relativePathLike == null) {
                dao.getMissingTrackIdsFromSource(source, scanRunId)
            } else {
                dao.getMissingTrackIdsFromRelativePath(source, relativePathLike, scanRunId)
            }
            val deletedCount = if (relativePathLike == null) {
                dao.deleteMissingFromSource(source, scanRunId)
            } else {
                dao.deleteMissingFromRelativePath(source, relativePathLike, scanRunId)
            }
            missingTrackIds.chunked(DatabaseBatchSize).forEach { trackIds ->
                dao.deleteFtsByTrackIds(trackIds)
            }
            emitProgress(
                phase = LibraryScanPhase.Completed,
                currentTitle = null,
                deletedCount = deletedCount,
                isCompleted = true,
            )
        } catch (error: CancellationException) {
            emitProgress(
                phase = LibraryScanPhase.Cancelled,
                currentTitle = null,
                isCompleted = true,
            )
            throw error
        } catch (error: Throwable) {
            emitProgress(
                phase = LibraryScanPhase.Error,
                currentTitle = null,
                error = error.message ?: "曲库扫描失败",
                isCompleted = true,
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun countTracks(): Int = database.trackDao().countTracks()

    private suspend fun canUseFts(dao: LibraryTrackDao, matchQuery: String): Boolean =
        runCatching {
            dao.validateFtsQuery(matchQuery)
            true
        }.onFailure { error ->
            Log.w(TAG, "FTS search failed; falling back to LIKE query.", error)
        }.getOrDefault(false)

    private suspend fun trackQueueCandidates(
        dao: LibraryTrackDao,
        query: String?,
        limit: Int,
    ): List<LibraryTrackEntity> {
        val trimmedQuery = query?.trim().orEmpty()
        val matchQuery = sanitizeFtsQuery(trimmedQuery)
        val rankQuery = ftsRankQuery(trimmedQuery)
        return when {
            trimmedQuery.isBlank() -> dao.getTrackQueue(limit)
            matchQuery == null -> dao.getTrackQueueByLike(trimmedQuery, rankQuery, limit)
            canUseFts(dao, matchQuery) -> dao.getTrackQueueByFts(matchQuery, rankQuery, limit)
            else -> dao.getTrackQueueByLike(trimmedQuery, rankQuery, limit)
        }
    }

    private fun withAnchorTrack(
        anchor: LibraryTrackEntity?,
        candidates: List<LibraryTrackEntity>,
        limit: Int,
    ): List<LibraryTrackEntity> {
        if (anchor == null) return candidates.take(limit)
        if (candidates.any { it.id == anchor.id }) return candidates.take(limit)
        return (listOf(anchor) + candidates.filterNot { it.id == anchor.id }).take(limit)
    }

    private fun LibraryTrackEntity.albumKey(): String =
        libraryAlbumKey(
            normalizedAlbum = normalizedAlbum,
            normalizedAlbumArtist = normalizedAlbumArtist,
            normalizedArtist = normalizedArtist,
        )

    private fun LibraryTrackEntity.artistKey(): String =
        libraryArtistKey(normalizedArtist)

    private fun albumPlaybackQuery(albumKey: String, limit: Int): SimpleSQLiteQuery {
        val fallbackParts = albumKey.split("::", limit = 2)
        val fallbackAlbum = fallbackParts.getOrElse(0) { albumKey }
        val fallbackArtist = fallbackParts.getOrElse(1) { albumKey }
        return SimpleSQLiteQuery(
            """
            SELECT * FROM library_tracks
            WHERE (
                COALESCE(NULLIF(normalizedAlbum, ''), ?) ||
                '::' ||
                COALESCE(NULLIF(normalizedAlbumArtist, ''), NULLIF(normalizedArtist, ''), ?)
            ) = ?
            ORDER BY
                CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
                CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
                title COLLATE NOCASE ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf<Any>(fallbackAlbum, fallbackArtist, albumKey, limit),
        )
    }

    private fun artistPlaybackQuery(artistKey: String, limit: Int): SimpleSQLiteQuery =
        SimpleSQLiteQuery(
            """
            SELECT * FROM library_tracks
            WHERE COALESCE(NULLIF(normalizedArtist, ''), ?) = ?
            ORDER BY
                album COLLATE NOCASE ASC,
                CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
                CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
                title COLLATE NOCASE ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf<Any>(artistKey, artistKey, limit),
        )

    private fun defaultPagingConfig(): PagingConfig =
        PagingConfig(
            pageSize = 60,
            prefetchDistance = 20,
            enablePlaceholders = false,
        )

    private companion object {
        const val TAG = "EchoLibraryRepository"
        const val ScanBatchSize = 500
        const val DatabaseBatchSize = 500
        const val ProgressEmitStride = 100
        const val RecommendedTrackLimit = 8
        const val RecentAlbumLimit = 12
        const val TrackQueueLimit = 200
        const val AggregationQueueLimit = 500
    }
}

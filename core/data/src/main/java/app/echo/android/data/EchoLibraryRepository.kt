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
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.library.NeteaseAudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class EchoLibraryRepository(
    private val database: EchoLibraryDatabase,
    private val scanner: MediaStoreTrackScanner,
    private val neteaseClient: NeteaseCloudMusicClient = NeteaseCloudMusicClient(),
) {
    fun pagedTracks(
        query: String? = null,
        sort: LibraryTrackSortMode = LibraryTrackSortMode.Title,
    ): Flow<PagingData<LibraryTrackEntity>> =
        flow {
            val dao = database.trackDao()
            val trimmedQuery = query?.trim().orEmpty()
            val matchQuery = sanitizeFtsQuery(trimmedQuery)
            val rankQuery = ftsRankQuery(trimmedQuery)
            val useFts = matchQuery != null && canUseFts(dao, matchQuery)

            emitAll(
                Pager(
                    config = defaultPagingConfig(),
                    pagingSourceFactory = {
                        dao.pageTracksSorted(
                            trackPagingQuery(
                                query = trimmedQuery,
                                matchQuery = matchQuery,
                                rankQuery = rankQuery,
                                useFts = useFts,
                                sort = sort,
                            ),
                        )
                    },
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

    fun pagedRemoteAlbums(
        query: String? = null,
        sort: AlbumSortMode = AlbumSortMode.Title,
    ): Flow<PagingData<AlbumSummary>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = {
                database.trackDao().pageRemoteAlbums(query?.trim()?.takeIf { it.isNotBlank() }, sort.name)
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

    fun pagedFolders(query: String? = null): Flow<PagingData<FolderSummary>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = {
                database.trackDao().pageFolders(query?.trim()?.takeIf { it.isNotBlank() })
            },
        ).flow

    fun pagedAlbumTracks(albumKey: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = {
                val remoteAlbum = RemoteAlbumKey.parse(albumKey)
                if (remoteAlbum == null) {
                    database.trackDao().pageTracksByAlbum(albumKey)
                } else {
                    database.trackDao().pageTracksByRemoteAlbum(remoteAlbum.source, remoteAlbum.albumKey)
                }
            },
        ).flow

    fun pagedArtistTracks(artistKey: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = { database.trackDao().pageTracksByArtist(artistKey) },
        ).flow

    fun pagedFolderTracks(folderKey: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = { database.trackDao().pageTracksByFolder(folderKey) },
        ).flow

    fun observeNeteasePlaylists(): Flow<List<EchoPlaylist>> =
        database.playlistDao().observePlaylists(NeteaseSourceId)
            .map { playlists -> playlists.map { it.toEchoPlaylist() } }
            .flowOn(Dispatchers.IO)

    fun pagedPlaylistTracks(playlistId: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = { database.playlistDao().pagePlaylistTracks(playlistId) },
        ).flow

    suspend fun albumTracks(albumKey: String): List<LibraryTrackEntity> =
        RemoteAlbumKey.parse(albumKey)?.let { remoteAlbum ->
            database.trackDao().getTracksByRemoteAlbum(remoteAlbum.source, remoteAlbum.albumKey)
        } ?: database.trackDao().getTracksByAlbum(albumKey)

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
        return if (track.source == LibrarySource.MediaStore.id) {
            database.trackDao().getAlbumSummary(track.albumKey())
        } else {
            database.trackDao().getRemoteAlbumSummary(track.source, track.albumKey())
        }
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
    ): List<LibraryTrackEntity> {
        val safeLimit = limit.coerceAtLeast(1)
        val remoteAlbum = RemoteAlbumKey.parse(albumKey)
        return if (remoteAlbum == null) {
            database.trackDao().getAlbumTracksForPlayback(albumPlaybackQuery(albumKey, safeLimit))
        } else {
            database.trackDao().getAlbumTracksForPlayback(
                remoteAlbumPlaybackQuery(
                    source = remoteAlbum.source,
                    albumKey = remoteAlbum.albumKey,
                    limit = safeLimit,
                ),
            )
        }
    }

    suspend fun artistTracksForPlayback(
        artistKey: String,
        limit: Int = AggregationQueueLimit,
    ): List<LibraryTrackEntity> =
        database.trackDao().getArtistTracksForPlayback(artistPlaybackQuery(artistKey, limit.coerceAtLeast(1)))

    suspend fun folderTracksForPlayback(
        folderKey: String,
        limit: Int = AggregationQueueLimit,
    ): List<LibraryTrackEntity> =
        database.trackDao().getTracksByFolderForPlayback(folderKey, limit.coerceAtLeast(1))

    suspend fun playlistTracksForPlayback(
        playlistId: String,
        limit: Int = AggregationQueueLimit,
    ): List<LibraryTrackEntity> =
        database.playlistDao().getPlaylistTracksForPlayback(playlistId, limit.coerceAtLeast(1))

    suspend fun importNeteasePlaylist(
        session: NeteaseSession,
        playlistId: Long,
    ): NeteasePlaylistImport {
        val imported = neteaseClient.fetchPlaylistImport(session, playlistId)
        database.trackDao().upsertBatchWithFts(imported.tracks)
        database.playlistDao().replacePlaylist(imported.playlist, imported.playlistTracks)
        return imported
    }

    suspend fun resolveNeteasePlaybackUrls(
        sessionCookie: String?,
        songIds: List<Long>,
        quality: NeteaseAudioQuality,
    ): Map<Long, String> =
        neteaseClient.resolvePlaybackUrls(sessionCookie, songIds, quality)

    suspend fun fetchNeteaseUserPlaylists(session: NeteaseSession) =
        neteaseClient.fetchUserPlaylists(session)

    suspend fun loginNeteaseByPhone(phone: String, password: String): NeteaseSession =
        neteaseClient.loginByPhone(phone, password)

    suspend fun loginNeteaseWithCookie(cookie: String): NeteaseSession =
        neteaseClient.loginWithCookie(cookie)

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
                existingTracks = existingFingerprints,
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

    fun refreshSubsonicSnapshot(
        endpoint: SubsonicEndpoint,
        batchSize: Int = ScanBatchSize,
    ): Flow<LibraryScanProgress> = flow {
        val client = SubsonicClient(endpoint)
        val dao = database.trackDao()
        val source = endpoint.sourceId
        val scanRunId = System.currentTimeMillis()
        var progress = LibraryScanProgress(phase = LibraryScanPhase.Preparing)
        var insertedCount = 0
        var updatedCount = 0
        var scannedCount = 0
        var totalCount: Int? = null
        var deletedCount = 0

        suspend fun emitProgress(
            phase: LibraryScanPhase = progress.phase,
            currentTitle: String? = progress.currentTitle,
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

            emitProgress(phase = LibraryScanPhase.Diffing, currentTitle = "读取远程曲库索引")
            val existingFingerprints = dao.getExistingMediaStoreFingerprints(source)
                .associateBy(TrackFingerprint::id)

            emitProgress(phase = LibraryScanPhase.QueryingMediaStore, currentTitle = "连接 Navidrome/Subsonic")
            client.ping()
            val albums = client.fetchAlbums()
            totalCount = albums.sumOf { it.songCount.coerceAtLeast(1) }
            emitProgress(phase = LibraryScanPhase.QueryingMediaStore, currentTitle = "发现 ${albums.size} 张远程专辑")

            val pending = ArrayList<LibraryTrackEntity>(batchSize)
            for (album in albums) {
                coroutineContext.ensureActive()
                val songs = client.fetchAlbumSongs(album)
                for (song in songs) {
                    coroutineContext.ensureActive()
                    scannedCount += 1
                    pending += song.toLibraryTrackEntity(endpoint, client, scanRunId)
                    if (pending.size >= batchSize) {
                        val counts = writeRemoteBatch(dao, pending, existingFingerprints)
                        insertedCount += counts.first
                        updatedCount += counts.second
                        pending.clear()
                        emitProgress(phase = LibraryScanPhase.WritingDatabase, currentTitle = album.name)
                    }
                }
                emitProgress(phase = LibraryScanPhase.QueryingMediaStore, currentTitle = album.name)
            }
            if (pending.isNotEmpty()) {
                val counts = writeRemoteBatch(dao, pending, existingFingerprints)
                insertedCount += counts.first
                updatedCount += counts.second
                pending.clear()
            }

            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            val missingTrackIds = dao.getMissingTrackIdsFromSource(source, scanRunId)
            deletedCount = dao.deleteMissingFromSource(source, scanRunId)
            missingTrackIds.chunked(DatabaseBatchSize).forEach { trackIds ->
                dao.deleteFtsByTrackIds(trackIds)
            }
            emitProgress(phase = LibraryScanPhase.Completed, currentTitle = null, isCompleted = true)
        } catch (error: CancellationException) {
            emitProgress(phase = LibraryScanPhase.Cancelled, currentTitle = null, isCompleted = true)
            throw error
        } catch (error: Throwable) {
            emitProgress(
                phase = LibraryScanPhase.Error,
                currentTitle = null,
                error = error.message ?: "远程曲库同步失败",
                isCompleted = true,
            )
        }
    }.flowOn(Dispatchers.IO)

    fun refreshWebDavSnapshot(
        endpoint: WebDavEndpoint,
        batchSize: Int = ScanBatchSize,
    ): Flow<LibraryScanProgress> = flow {
        val client = WebDavClient(endpoint)
        val dao = database.trackDao()
        val source = endpoint.sourceId
        val scanRunId = System.currentTimeMillis()
        var progress = LibraryScanProgress(phase = LibraryScanPhase.Preparing)
        var insertedCount = 0
        var updatedCount = 0
        var scannedCount = 0
        var deletedCount = 0
        val pending = ArrayList<LibraryTrackEntity>(batchSize)

        suspend fun emitProgress(
            phase: LibraryScanPhase = progress.phase,
            currentTitle: String? = progress.currentTitle,
            error: String? = null,
            isCompleted: Boolean = false,
        ) {
            progress = LibraryScanProgress(
                phase = phase,
                scannedCount = scannedCount,
                insertedCount = insertedCount,
                updatedCount = updatedCount,
                deletedCount = deletedCount,
                currentTitle = currentTitle,
                error = error,
                isCompleted = isCompleted,
            )
            emit(progress)
        }

        try {
            emitProgress()
            coroutineContext.ensureActive()
            emitProgress(phase = LibraryScanPhase.Diffing, currentTitle = "读取 WebDAV 索引")
            val existingFingerprints = dao.getExistingMediaStoreFingerprints(source)
                .associateBy(TrackFingerprint::id)

            emitProgress(phase = LibraryScanPhase.QueryingMediaStore, currentTitle = "扫描 WebDAV 目录")
            client.scanAudioFiles { file ->
                coroutineContext.ensureActive()
                scannedCount += 1
                pending += file.toLibraryTrackEntity(endpoint, scanRunId)
                if (pending.size >= batchSize) {
                    val counts = writeRemoteBatch(dao, pending, existingFingerprints)
                    insertedCount += counts.first
                    updatedCount += counts.second
                    pending.clear()
                }
            }
            if (pending.isNotEmpty()) {
                val counts = writeRemoteBatch(dao, pending, existingFingerprints)
                insertedCount += counts.first
                updatedCount += counts.second
                pending.clear()
            }

            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            val missingTrackIds = dao.getMissingTrackIdsFromSource(source, scanRunId)
            deletedCount = dao.deleteMissingFromSource(source, scanRunId)
            missingTrackIds.chunked(DatabaseBatchSize).forEach { trackIds ->
                dao.deleteFtsByTrackIds(trackIds)
            }
            emitProgress(phase = LibraryScanPhase.Completed, currentTitle = null, isCompleted = true)
        } catch (error: CancellationException) {
            emitProgress(phase = LibraryScanPhase.Cancelled, currentTitle = null, isCompleted = true)
            throw error
        } catch (error: Throwable) {
            emitProgress(
                phase = LibraryScanPhase.Error,
                currentTitle = null,
                error = error.message ?: "WebDAV 曲库同步失败",
                isCompleted = true,
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun countTracks(): Int = database.trackDao().countTracks()

    suspend fun recordPlayback(trackId: String) {
        database.trackDao().recordPlayback(
            trackId = trackId,
            playedAtEpochMs = System.currentTimeMillis(),
        )
    }

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

    private fun trackPagingQuery(
        query: String,
        matchQuery: String?,
        rankQuery: String,
        useFts: Boolean,
        sort: LibraryTrackSortMode,
    ): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val sql = StringBuilder(
            """
            SELECT library_tracks.* FROM library_tracks
            LEFT JOIN library_playback_stats
                ON library_tracks.id = library_playback_stats.trackId
            """.trimIndent(),
        )
        if (query.isNotBlank()) {
            if (useFts && matchQuery != null) {
                sql.appendLine()
                sql.append("JOIN library_tracks_fts ON library_tracks.id = library_tracks_fts.trackId")
                sql.appendLine()
                sql.append("WHERE library_tracks_fts MATCH ?")
                args += matchQuery
            } else {
                val likeQuery = "%${query.lowercase()}%"
                sql.appendLine()
                sql.append(
                    """
                    WHERE library_tracks.normalizedTitle LIKE ?
                       OR library_tracks.normalizedArtist LIKE ?
                       OR library_tracks.normalizedAlbum LIKE ?
                       OR library_tracks.normalizedAlbumArtist LIKE ?
                    """.trimIndent(),
                )
                repeat(4) { args += likeQuery }
            }
        }
        sql.appendLine()
        sql.append("ORDER BY ")
        if (query.isNotBlank() && sort == LibraryTrackSortMode.Title) {
            sql.append(
                """
                CASE
                    WHEN library_tracks.normalizedTitle LIKE ? THEN 0
                    WHEN library_tracks.normalizedArtist LIKE ? THEN 1
                    WHEN library_tracks.normalizedAlbum LIKE ? THEN 2
                    ELSE 3
                END,
                """.trimIndent(),
            )
            repeat(3) { args += rankQuery }
            sql.appendLine()
        }
        sql.append(trackSortOrder(sort))
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    private fun trackSortOrder(sort: LibraryTrackSortMode): String =
        when (sort) {
            LibraryTrackSortMode.Title -> "library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.Duration -> "library_tracks.durationMs DESC, library_tracks.title COLLATE NOCASE ASC"
            LibraryTrackSortMode.FrequentlyPlayed -> """
                COALESCE(library_playback_stats.playCount, 0) DESC,
                COALESCE(library_playback_stats.lastPlayedAtEpochMs, 0) DESC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
            LibraryTrackSortMode.Random -> "RANDOM()"
            LibraryTrackSortMode.Artist -> """
                CASE WHEN trim(library_tracks.artist) = '' THEN 1 ELSE 0 END ASC,
                library_tracks.artist COLLATE NOCASE ASC,
                CASE WHEN library_tracks.album IS NULL OR trim(library_tracks.album) = '' THEN 1 ELSE 0 END ASC,
                library_tracks.album COLLATE NOCASE ASC,
                CASE WHEN library_tracks.discNumber IS NULL THEN 0 ELSE library_tracks.discNumber END ASC,
                CASE WHEN library_tracks.trackNumber IS NULL THEN 0 ELSE library_tracks.trackNumber END ASC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
            LibraryTrackSortMode.Album -> """
                CASE WHEN library_tracks.album IS NULL OR trim(library_tracks.album) = '' THEN 1 ELSE 0 END ASC,
                library_tracks.album COLLATE NOCASE ASC,
                CASE WHEN library_tracks.discNumber IS NULL THEN 0 ELSE library_tracks.discNumber END ASC,
                CASE WHEN library_tracks.trackNumber IS NULL THEN 0 ELSE library_tracks.trackNumber END ASC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
            LibraryTrackSortMode.RecentlyUpdated -> """
                library_tracks.dateModifiedSeconds DESC,
                library_tracks.title COLLATE NOCASE ASC
            """.trimIndent()
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

    private fun remoteAlbumPlaybackQuery(source: String, albumKey: String, limit: Int): SimpleSQLiteQuery {
        val fallbackParts = albumKey.split("::", limit = 2)
        val fallbackAlbum = fallbackParts.getOrElse(0) { albumKey }
        val fallbackArtist = fallbackParts.getOrElse(1) { albumKey }
        return SimpleSQLiteQuery(
            """
            SELECT * FROM library_tracks
            WHERE source = ?
              AND (
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
            arrayOf<Any>(source, fallbackAlbum, fallbackArtist, albumKey, limit),
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

private data class RemoteAlbumKey(
    val source: String,
    val albumKey: String,
) {
    companion object {
        fun parse(value: String): RemoteAlbumKey? {
            if (!value.startsWith(Prefix)) return null
            val parts = value.split("||", limit = 3)
            if (parts.size != 3 || parts[1].isBlank() || parts[2].isBlank()) return null
            return RemoteAlbumKey(source = parts[1], albumKey = parts[2])
        }

        private const val Prefix = "remote||"
    }
}

private suspend fun writeRemoteBatch(
    dao: LibraryTrackDao,
    tracks: List<LibraryTrackEntity>,
    existingFingerprints: Map<String, TrackFingerprint>,
): Pair<Int, Int> {
    val inserts = ArrayList<LibraryTrackEntity>(tracks.size)
    val updates = ArrayList<LibraryTrackEntity>(tracks.size)
    val unchangedIds = ArrayList<String>(tracks.size)
    tracks.forEach { track ->
        val existing = existingFingerprints[track.id]
        when {
            existing == null -> inserts += track
            existing.fingerprint != track.fingerprint -> updates += track
            else -> unchangedIds += track.id
        }
    }
    (inserts + updates).chunked(500).forEach { chunk -> dao.upsertBatchWithFts(chunk) }
    unchangedIds.chunked(500).forEach { ids -> dao.markSeen(ids, tracks.first().lastSeenScanRunId) }
    return inserts.size to updates.size
}

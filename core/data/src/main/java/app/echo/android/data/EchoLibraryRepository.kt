package app.echo.android.data


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
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.LibraryStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class EchoLibraryRepository(
    private val database: EchoLibraryDatabase,
    private val scanner: MediaStoreTrackScanner,
    private val documentTreeScanner: DocumentTreeTrackScanner,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        repositoryScope.launch {
            refreshLegacyLibrarySearchIndex()
        }
    }

    fun pagedTracks(
        query: String? = null,
        sort: LibraryTrackSortMode = LibraryTrackSortMode.Title,
    ): Flow<PagingData<LibraryTrackEntity>> =
        flow {
            val dao = database.trackDao()
            val trimmedQuery = query?.trim().orEmpty()
            val matchQuery = sanitizeFtsQuery(trimmedQuery)
            val rankQuery = ftsRankQuery(trimmedQuery)
            val useFts = matchQuery != null && canUseFts(dao, matchQuery, trimmedQuery)

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

    fun observeRecommendedTracks(limit: Int = RECOMMENDED_TRACK_LIMIT): Flow<List<LibraryTrackEntity>> =
        database.trackDao().observeRecommendedTracks(limit)
            .flowOn(Dispatchers.IO)

    fun observeRecentlyAddedAlbums(limit: Int = RECENT_ALBUM_LIMIT): Flow<List<AlbumSummary>> =
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

    suspend fun searchLocalLibrary(
        query: String,
        limitPerType: Int = SEARCH_RESULT_LIMIT_PER_TYPE,
    ): LocalLibrarySearchResults {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return LocalLibrarySearchResults()
        val dao = database.trackDao()
        val matchQuery = sanitizeFtsQuery(trimmedQuery)
        val rankQuery = ftsRankQuery(trimmedQuery)
        val tracks = if (matchQuery != null && canUseFts(dao, matchQuery, trimmedQuery)) {
            dao.searchTracksByFts(matchQuery, rankQuery, limitPerType)
        } else {
            dao.searchTracks(trimmedQuery, limitPerType)
        }
        return LocalLibrarySearchResults(
            tracks = tracks,
            albums = dao.searchAlbums(trimmedQuery, limitPerType),
            artists = dao.searchArtists(trimmedQuery, limitPerType),
        )
    }

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

    fun observeLocalPlaylists(): Flow<List<EchoPlaylist>> =
        database.playlistDao().observePlaylists(LibrarySource.MediaStore.id)
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
        limit: Int = TRACK_QUEUE_LIMIT,
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
        return if (LibraryScanPolicy.isLocalLibrarySource(track.source)) {
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

    suspend fun updateTrackMetadata(update: EchoTrackMetadataUpdate): Boolean {
        val dao = database.trackDao()
        val current = dao.getTrackById(update.trackId) ?: return false
        val updated = current.withUserMetadata(
            update = update,
            editedAtEpochMs = System.currentTimeMillis(),
        )
        if (current.hasSameUserMetadata(updated)) return true
        dao.upsertBatchWithFts(listOf(updated))
        dao.rebuildLibrarySummaries()
        return true
    }

    suspend fun updateTrackArtwork(trackId: String, artworkUri: String): Boolean {
        val dao = database.trackDao()
        val current = dao.getTrackById(trackId) ?: return false
        val updated = current.copy(
            artworkUri = artworkUri.trim().takeIf { it.isNotBlank() } ?: return false,
            metadataEditedAtEpochMs = System.currentTimeMillis(),
        ).withScanMetadata()
        if (current.hasSameUserMetadata(updated)) return true
        dao.upsertBatchWithFts(listOf(updated))
        dao.rebuildLibrarySummaries()
        return true
    }

    suspend fun albumTracksForPlayback(
        albumKey: String,
        limit: Int = AGGREGATION_QUEUE_LIMIT,
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
        limit: Int = AGGREGATION_QUEUE_LIMIT,
    ): List<LibraryTrackEntity> =
        database.trackDao().getArtistTracksForPlayback(artistPlaybackQuery(artistKey, limit.coerceAtLeast(1)))

    suspend fun folderTracksForPlayback(
        folderKey: String,
        limit: Int = AGGREGATION_QUEUE_LIMIT,
    ): List<LibraryTrackEntity> =
        database.trackDao().getTracksByFolderForPlayback(folderKey, limit.coerceAtLeast(1))

    suspend fun playlistTracksForPlayback(
        playlistId: String,
        limit: Int = AGGREGATION_QUEUE_LIMIT,
    ): List<LibraryTrackEntity> =
        database.playlistDao().getPlaylistTracksForPlayback(playlistId, limit.coerceAtLeast(1))

    fun refreshMediaStoreSnapshot(
        relativePathPrefix: String? = null,
        batchSize: Int = SCAN_BATCH_SIZE,
        skipSampleRateRead: Boolean = false,
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
            val editedTracks = if (relativePathLike == null) {
                dao.getMetadataEditedTracks(source)
            } else {
                dao.getMetadataEditedTracksInRelativePath(source, relativePathLike)
            }.associateBy(LibraryTrackEntity::id)
            val seenIds = HashSet<String>(existingFingerprints.size)
            val scanOutcome = scanner.scanAudio(
                batchSize = batchSize,
                relativePathPrefix = normalizedRelativePath,
                existingTracks = existingFingerprints,
                readSampleRate = !skipSampleRateRead,
                onTotalCount = { count ->
                    totalCount = count
                    emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
                },
                onProgress = { count, currentTrack ->
                    scannedCount = count
                    if (count == 0 || count - lastProgressEmitCount >= PROGRESS_EMIT_STRIDE) {
                        lastProgressEmitCount = count
                        emitProgress(
                            phase = LibraryScanPhase.QueryingMediaStore,
                            currentTitle = currentTrack?.title,
                        )
                    }
                },
                onBatch = { batch ->
                    coroutineContext.ensureActive()
                    val classified = classifyScanBatch(
                        batch = batch,
                        existingFingerprints = existingFingerprints,
                        editedTracks = editedTracks,
                        scanRunId = scanRunId,
                    )
                    seenIds.addAll(classified.seenIds)
                    emitProgress(phase = LibraryScanPhase.WritingDatabase)
                    writeClassifiedScanBatch(dao, classified)
                    insertedCount += classified.inserts.size
                    updatedCount += classified.updates.size
                    lastProgressEmitCount = scannedCount
                    emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
                },
            )
            scannedCount = scanOutcome.scannedCount

            coroutineContext.ensureActive()
            emitProgress(phase = LibraryScanPhase.CleaningRemoved)
            val completeness = LibraryScanCompleteness(
                querySucceeded = scanOutcome.querySucceeded,
                scannedCount = scannedCount,
                existingCount = existingFingerprints.size,
            )
            val deletedCount = deleteMissingIfComplete(
                dao = dao,
                completeness = completeness,
                missingIds = {
                    val existingIds = if (relativePathLike == null) {
                        dao.getIdsFromSource(source)
                    } else {
                        dao.getIdsFromRelativePath(source, relativePathLike)
                    }.filter(LibraryScanPolicy::isMediaStoreNativeId)
                    LibraryScanPolicy.unseenIds(existingIds, seenIds)
                },
            )
            rebuildSummariesIfNeeded(dao, insertedCount, updatedCount, deletedCount)
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

    fun refreshDocumentTreeSnapshot(
        treeUri: android.net.Uri,
        relativePathPrefix: String,
        batchSize: Int = DOCUMENT_TREE_SCAN_BATCH_SIZE,
        skipSampleRateRead: Boolean = false,
    ): Flow<LibraryScanProgress> = flow {
        val dao = database.trackDao()
        val source = LibraryScanPolicy.SafSourceId
        val normalizedRelativePath = normalizeRelativePathPrefix(relativePathPrefix)
            ?: error("Document tree scan requires a relative path")
        val relativePathLike = "${escapeSqlLikeArgument(normalizedRelativePath)}%"
        val scanRunId = System.currentTimeMillis()
        var progress = LibraryScanProgress(phase = LibraryScanPhase.Preparing)
        var insertedCount = 0
        var updatedCount = 0
        var scannedCount = 0
        var deletedCount = 0
        var lastProgressEmitCount = 0

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
                totalCount = null,
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
            val existingFingerprints = (
                dao.getExistingMediaStoreFingerprintsInRelativePath(
                    source = LibrarySource.MediaStore.id,
                    relativePathLike = relativePathLike,
                ).filter { LibraryScanPolicy.isSafTrackId(it.id) } +
                    dao.getExistingMediaStoreFingerprintsInRelativePath(
                        source = source,
                        relativePathLike = relativePathLike,
                    )
                ).associateBy(TrackFingerprint::id)
            val editedTracks = (
                dao.getMetadataEditedTracksInRelativePath(
                    source = LibrarySource.MediaStore.id,
                    relativePathLike = relativePathLike,
                ).filter { LibraryScanPolicy.isSafTrackId(it.id) } +
                    dao.getMetadataEditedTracksInRelativePath(
                        source = source,
                        relativePathLike = relativePathLike,
                    )
                ).associateBy(LibraryTrackEntity::id)
            val seenIds = HashSet<String>(existingFingerprints.size)

            emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
            documentTreeScanner.scanAudioTree(
                treeUri = treeUri,
                relativePathPrefix = normalizedRelativePath,
                batchSize = batchSize,
                existingTracks = existingFingerprints,
                readSampleRate = !skipSampleRateRead,
                onProgress = { count, currentTrack ->
                    scannedCount = count
                    if (count == 0 || count - lastProgressEmitCount >= PROGRESS_EMIT_STRIDE) {
                        lastProgressEmitCount = count
                        emitProgress(
                            phase = LibraryScanPhase.QueryingMediaStore,
                            currentTitle = currentTrack?.title,
                        )
                    }
                },
                onBatch = { batch ->
                    coroutineContext.ensureActive()
                    val classified = classifyScanBatch(
                        batch = batch,
                        existingFingerprints = existingFingerprints,
                        editedTracks = editedTracks,
                        scanRunId = scanRunId,
                    )
                    seenIds.addAll(classified.seenIds)
                    emitProgress(phase = LibraryScanPhase.WritingDatabase)
                    writeClassifiedScanBatch(dao, classified)
                    insertedCount += classified.inserts.size
                    updatedCount += classified.updates.size
                    lastProgressEmitCount = scannedCount
                    emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
                },
            )

            coroutineContext.ensureActive()
            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            deletedCount = deleteMissingIfComplete(
                dao = dao,
                completeness = LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = scannedCount,
                    existingCount = existingFingerprints.size,
                ),
                missingIds = {
                    val existingIds =
                        dao.getIdsFromRelativePath(source, relativePathLike).filter(LibraryScanPolicy::isSafTrackId) +
                            dao.getIdsFromRelativePath(LibrarySource.MediaStore.id, relativePathLike)
                                .filter(LibraryScanPolicy::isSafTrackId)
                    LibraryScanPolicy.unseenIds(existingIds, seenIds)
                },
            )
            rebuildSummariesIfNeeded(dao, insertedCount, updatedCount, deletedCount)
            emitProgress(
                phase = LibraryScanPhase.Completed,
                currentTitle = null,
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
                error = error.message ?: "Document tree scan failed",
                isCompleted = true,
            )
        }
    }.flowOn(Dispatchers.IO)

    fun refreshSubsonicSnapshot(
        endpoint: SubsonicEndpoint,
        batchSize: Int = SCAN_BATCH_SIZE,
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
            val seenIds = HashSet<String>(existingFingerprints.size)

            emitProgress(phase = LibraryScanPhase.QueryingMediaStore, currentTitle = "连接 Navidrome/Subsonic")
            client.ping()
            val albums = client.fetchAlbums()
            val expectedSongCount = albums.sumOf { it.songCount.coerceAtLeast(0) }
            totalCount = expectedSongCount.takeIf { it > 0 } ?: albums.size
            emitProgress(phase = LibraryScanPhase.QueryingMediaStore, currentTitle = "发现 ${albums.size} 张远程专辑")

            val pending = ArrayList<LibraryTrackEntity>(batchSize)
            suspend fun flushPending(title: String?) {
                if (pending.isEmpty()) return
                val written = writeRemoteBatch(dao, pending, existingFingerprints)
                insertedCount += written.insertedCount
                updatedCount += written.updatedCount
                seenIds.addAll(written.seenIds)
                pending.clear()
                emitProgress(phase = LibraryScanPhase.WritingDatabase, currentTitle = title)
            }

            suspend fun ingestSongs(songs: List<SubsonicSong>, title: String?) {
                for (song in songs) {
                    coroutineContext.ensureActive()
                    scannedCount += 1
                    pending += song.toLibraryTrackEntity(endpoint, client, scanRunId)
                    if (pending.size >= batchSize) {
                        flushPending(title)
                    }
                }
            }

            val bulkSongs = runCatching { client.fetchSongsBySearch3() }.getOrDefault(emptyList())
            val usedSearch3 = SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount, bulkSongs.size)
            if (usedSearch3) {
                emitProgress(
                    phase = LibraryScanPhase.QueryingMediaStore,
                    currentTitle = "已批量读取 ${bulkSongs.size} 首远程歌曲",
                )
                ingestSongs(bulkSongs, title = "search3")
            } else {
                for (chunk in albums.chunked(SubsonicSyncPolicy.AlbumFetchConcurrency)) {
                    coroutineContext.ensureActive()
                    val chunkSongs = coroutineScope {
                        chunk.map { album ->
                            async { album to client.fetchAlbumSongs(album) }
                        }.awaitAll()
                    }
                    for ((album, songs) in chunkSongs) {
                        ingestSongs(songs, album.name)
                        emitProgress(phase = LibraryScanPhase.QueryingMediaStore, currentTitle = album.name)
                    }
                }
            }
            flushPending(title = null)

            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            deletedCount = deleteMissingIfComplete(
                dao = dao,
                completeness = LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = scannedCount,
                    existingCount = existingFingerprints.size,
                    hitVisitCap = albums.size >= SubsonicClient.MaxAlbumsPerSync ||
                        (usedSearch3 && bulkSongs.size >= SubsonicClient.MaxSongsPerSync),
                ),
                missingIds = { LibraryScanPolicy.unseenIds(existingFingerprints.keys, seenIds) },
            )
            rebuildSummariesIfNeeded(dao, insertedCount, updatedCount, deletedCount)
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
        batchSize: Int = SCAN_BATCH_SIZE,
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
            val seenIds = HashSet<String>(existingFingerprints.size)

            emitProgress(phase = LibraryScanPhase.QueryingMediaStore, currentTitle = "扫描 WebDAV 目录")
            val visit = client.scanAudioFiles { file ->
                coroutineContext.ensureActive()
                scannedCount += 1
                pending += file.toLibraryTrackEntity(endpoint, scanRunId)
                if (pending.size >= batchSize) {
                    val written = writeRemoteBatch(dao, pending, existingFingerprints)
                    insertedCount += written.insertedCount
                    updatedCount += written.updatedCount
                    seenIds.addAll(written.seenIds)
                    pending.clear()
                }
            }
            if (pending.isNotEmpty()) {
                val written = writeRemoteBatch(dao, pending, existingFingerprints)
                insertedCount += written.insertedCount
                updatedCount += written.updatedCount
                seenIds.addAll(written.seenIds)
                pending.clear()
            }

            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            deletedCount = deleteMissingIfComplete(
                dao = dao,
                completeness = LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = scannedCount,
                    existingCount = existingFingerprints.size,
                    hitVisitCap = visit.hitVisitCap,
                ),
                missingIds = { LibraryScanPolicy.unseenIds(existingFingerprints.keys, seenIds) },
            )
            rebuildSummariesIfNeeded(dao, insertedCount, updatedCount, deletedCount)
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

    private suspend fun canUseFts(dao: LibraryTrackDao, matchQuery: String, rawQuery: String): Boolean {
        if (matchQuery.isBlank() || rawQuery.isBlank()) return false
        return runCatching { dao.validateFtsQuery(matchQuery) }.isSuccess
    }

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
            canUseFts(dao, matchQuery, trimmedQuery) -> dao.getTrackQueueByFts(matchQuery, rankQuery, limit)
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
        val trimmed = query.trim()
        val sql = LibraryTrackQueryBuilder.buildTrackPagingSql(
            query = trimmed,
            useFts = useFts && matchQuery != null,
            sort = sort,
        )
        val args = mutableListOf<Any>()
        if (trimmed.isNotBlank() && useFts && matchQuery != null) {
            args += matchQuery
            if (sort == LibraryTrackSortMode.Title) {
                repeat(3) { args += rankQuery }
            }
        } else if (trimmed.isNotBlank()) {
            val likeQuery = "%${trimmed.lowercase()}%"
            repeat(6) { args += likeQuery }
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
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

    private suspend fun refreshLegacyLibrarySearchIndex() {
        val dao = database.trackDao()
        var backfilled = false
        while (true) {
            val staleTracks = dao.getTracksNeedingPinyinBackfill(PINYIN_BACKFILL_BATCH_SIZE)
            if (staleTracks.isEmpty()) break
            dao.upsertBatch(staleTracks.map(LibraryTrackEntity::withComputedSearchMetadata))
            backfilled = true
        }
        if (backfilled) {
            dao.rebuildLibrarySummaries()
        }
    }

    private fun albumPlaybackQuery(albumKey: String, limit: Int): SimpleSQLiteQuery =
        SimpleSQLiteQuery(
            """
            SELECT * FROM library_tracks
            WHERE (source = 'mediastore' OR source = 'saf')
              AND albumKey = ?
            ORDER BY
                CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
                CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
                title COLLATE NOCASE ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf<Any>(albumKey, limit),
        )

    private fun remoteAlbumPlaybackQuery(source: String, albumKey: String, limit: Int): SimpleSQLiteQuery =
        SimpleSQLiteQuery(
            """
            SELECT * FROM library_tracks
            WHERE source = ?
              AND albumKey = ?
            ORDER BY
                CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
                CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
                title COLLATE NOCASE ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf<Any>(source, albumKey, limit),
        )

    private fun artistPlaybackQuery(artistKey: String, limit: Int): SimpleSQLiteQuery =
        SimpleSQLiteQuery(
            """
            SELECT * FROM library_tracks
            WHERE (source = 'mediastore' OR source = 'saf')
              AND artistKey = ?
            ORDER BY
                album COLLATE NOCASE ASC,
                CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
                CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
                title COLLATE NOCASE ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf<Any>(artistKey, limit),
        )

    private fun defaultPagingConfig(): PagingConfig =
        PagingConfig(
            pageSize = 60,
            prefetchDistance = 20,
            enablePlaceholders = false,
        )

    private suspend fun deleteMissingIfComplete(
        dao: LibraryTrackDao,
        completeness: LibraryScanCompleteness,
        missingIds: suspend () -> List<String>,
    ): Int {
        if (!LibraryScanPolicy.shouldDeleteMissingLibraryRows(completeness)) {
            return 0
        }
        val ids = missingIds()
        ids.chunked(DATABASE_BATCH_SIZE).forEach { chunk ->
            dao.deleteTracksByIds(chunk)
            dao.deleteFtsByTrackIds(chunk)
        }
        return ids.size
    }

    private fun classifyScanBatch(
        batch: List<LibraryTrackEntity>,
        existingFingerprints: Map<String, TrackFingerprint>,
        editedTracks: Map<String, LibraryTrackEntity>,
        scanRunId: Long,
    ): ClassifiedScanBatch {
        val inserts = ArrayList<LibraryTrackEntity>(batch.size)
        val updates = ArrayList<LibraryTrackEntity>(batch.size)
        val seenIds = ArrayList<String>(batch.size)
        batch.forEach { rawTrack ->
            val preserved = rawTrack.withPreservedUserMetadata(editedTracks[rawTrack.id])
            val incomingFingerprint = preserved.fingerprint ?: buildTrackFingerprint(preserved)
            seenIds += preserved.id
            when (
                LibraryScanPolicy.scanRowAction(
                    existingFingerprint = existingFingerprints[preserved.id]?.fingerprint,
                    incomingFingerprint = incomingFingerprint,
                )
            ) {
                LibraryScanRowAction.Insert -> inserts += preserved.withScanMetadata(scanRunId)
                LibraryScanRowAction.Update -> updates += preserved.withScanMetadata(scanRunId)
                LibraryScanRowAction.RememberSeen -> Unit
            }
        }
        return ClassifiedScanBatch(inserts = inserts, updates = updates, seenIds = seenIds)
    }

    private suspend fun writeClassifiedScanBatch(dao: LibraryTrackDao, classified: ClassifiedScanBatch) {
        (classified.inserts + classified.updates).chunked(DATABASE_BATCH_SIZE).forEach { chunk ->
            dao.upsertBatchWithFts(chunk)
        }
        if (LibraryScanPolicy.shouldStampLastSeenOnUnchangedRow()) {
            val unchangedIds = classified.seenIds.filter { id ->
                classified.inserts.none { it.id == id } && classified.updates.none { it.id == id }
            }
            val scanRunId = (classified.inserts + classified.updates).firstOrNull()?.lastSeenScanRunId ?: return
            unchangedIds.chunked(DATABASE_BATCH_SIZE).forEach { ids -> dao.markSeen(ids, scanRunId) }
        }
    }

    private suspend fun rebuildSummariesIfNeeded(
        dao: LibraryTrackDao,
        insertedCount: Int,
        updatedCount: Int,
        deletedCount: Int,
    ) {
        if (insertedCount + updatedCount + deletedCount <= 0) return
        dao.rebuildLibrarySummaries()
    }

    private companion object {
        const val SCAN_BATCH_SIZE = 500
        const val DOCUMENT_TREE_SCAN_BATCH_SIZE = 200
        const val DATABASE_BATCH_SIZE = 500
        const val PINYIN_BACKFILL_BATCH_SIZE = 200
        const val PROGRESS_EMIT_STRIDE = 100
        const val RECOMMENDED_TRACK_LIMIT = 8
        const val RECENT_ALBUM_LIMIT = 12
        const val SEARCH_RESULT_LIMIT_PER_TYPE = 6
        const val TRACK_QUEUE_LIMIT = 200
        const val AGGREGATION_QUEUE_LIMIT = 500
    }
}

data class LocalLibrarySearchResults(
    val tracks: List<LibraryTrackEntity> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
)

private fun LibraryTrackEntity.hasSameUserMetadata(other: LibraryTrackEntity): Boolean =
    title == other.title &&
        artist == other.artist &&
        album == other.album &&
        albumArtist == other.albumArtist &&
        artworkUri == other.artworkUri &&
        trackNumber == other.trackNumber &&
        discNumber == other.discNumber &&
        year == other.year

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

private data class ClassifiedScanBatch(
    val inserts: List<LibraryTrackEntity>,
    val updates: List<LibraryTrackEntity>,
    val seenIds: List<String>,
)

private data class RemoteBatchWriteResult(
    val insertedCount: Int,
    val updatedCount: Int,
    val seenIds: List<String>,
)

private suspend fun writeRemoteBatch(
    dao: LibraryTrackDao,
    tracks: List<LibraryTrackEntity>,
    existingFingerprints: Map<String, TrackFingerprint>,
): RemoteBatchWriteResult {
    val inserts = ArrayList<LibraryTrackEntity>(tracks.size)
    val updates = ArrayList<LibraryTrackEntity>(tracks.size)
    val seenIds = ArrayList<String>(tracks.size)
    tracks.forEach { track ->
        seenIds += track.id
        when (
            LibraryScanPolicy.scanRowAction(
                existingFingerprint = existingFingerprints[track.id]?.fingerprint,
                incomingFingerprint = track.fingerprint,
            )
        ) {
            LibraryScanRowAction.Insert -> inserts += track
            LibraryScanRowAction.Update -> updates += track
            LibraryScanRowAction.RememberSeen -> Unit
        }
    }
    (inserts + updates).chunked(500).forEach { chunk -> dao.upsertBatchWithFts(chunk) }
    if (LibraryScanPolicy.shouldStampLastSeenOnUnchangedRow() && tracks.isNotEmpty()) {
        val unchangedIds = seenIds.filter { id ->
            inserts.none { it.id == id } && updates.none { it.id == id }
        }
        unchangedIds.chunked(500).forEach { ids -> dao.markSeen(ids, tracks.first().lastSeenScanRunId) }
    }
    return RemoteBatchWriteResult(
        insertedCount = inserts.size,
        updatedCount = updates.size,
        seenIds = seenIds,
    )
}

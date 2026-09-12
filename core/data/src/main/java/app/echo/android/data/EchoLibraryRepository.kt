package app.echo.android.data


import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.i18n.echoText
import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSortMode
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.FolderSortMode
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.LibrarySmartPlaylistKind
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.LibraryStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class EchoLibraryRepository(
    private val database: EchoLibraryDatabase,
    private val scanner: MediaStoreTrackScanner,
    private val documentTreeScanner: DocumentTreeTrackScanner,
    private val tagWriter: EmbeddedTagWriter? = null,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        repositoryScope.launch {
            delay(PINYIN_BACKFILL_START_DELAY_MS)
            refreshLegacyLibrarySearchIndex()
            backfillWavTags()
            backfillAggregationKeys()
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

    fun observeAlbumListenStats(limit: Int = LISTEN_STATS_SEED_LIMIT): Flow<List<LibraryAlbumListenStatsRow>> =
        database.trackDao().observeAlbumListenStats(limit.coerceAtLeast(1))
            // GROUP BY 大查询在任何 join 表失效时都会重跑;结果没变就不向下游发射
            .distinctUntilChanged()
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

    fun pagedGenres(
        query: String? = null,
        sort: app.echo.android.model.library.GenreSortMode = app.echo.android.model.library.GenreSortMode.Name,
    ): Flow<PagingData<app.echo.android.model.library.GenreSummary>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = {
                database.trackDao().pageGenres(query?.trim()?.takeIf { it.isNotBlank() }, sort.name)
            },
        ).flow

    fun pagedGenreTracks(genreKey: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = { database.trackDao().pageTracksByGenre(genreKey) },
        ).flow

    fun pagedFolders(
        query: String? = null,
        sort: FolderSortMode = FolderSortMode.Path,
    ): Flow<PagingData<FolderSummary>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = {
                database.trackDao().pageFolders(query?.trim()?.takeIf { it.isNotBlank() }, sort.name)
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

    fun pagedArtistAlbums(artistKey: String): Flow<PagingData<AlbumSummary>> =
        Pager(config = defaultPagingConfig(), pagingSourceFactory = {
            database.trackDao().pageAlbumsByArtist(artistKey)
        }).flow

    fun observeArtistSummary(artistKey: String): Flow<ArtistSummary?> =
        database.trackDao().observeArtistSummary(artistKey)

    fun pagedArtistTracks(artistKey: String, query: String? = null, sort: LibraryTrackSortMode = LibraryTrackSortMode.Album): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = { database.trackDao().pageTracksByArtist(artistKey, query?.trim()?.takeIf { it.isNotEmpty() }, sort.name) },
        ).flow

    fun pagedFolderTracks(folderKey: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = { database.trackDao().pageTracksByFolder(folderKey) },
        ).flow

    fun observeLocalPlaylists(): Flow<List<EchoPlaylist>> =
        combine(
            database.playlistDao().observeAllPlaylists(),
            database.playlistDao().observeFavoriteTrackIds(),
            database.playlistDao().observeFavoriteAlbums(1),
            database.trackDao().observeSmartPlaylistStats(),
        ) { playlists, favoriteIds, favoriteAlbums, smartStats ->
            val liked = LibraryFavoritePolicy.likedSongsPlaylist(
                trackCount = favoriteIds.size,
                artworkUri = favoriteAlbums.firstOrNull()?.artworkUri,
            )
            val pinned = LibrarySmartPlaylistPolicy.pinned(smartStats)
            listOf(liked) + pinned + playlists.map { it.toEchoPlaylist() }
                .filterNot { it.isLikedSongs || it.isSmartPlaylist }
        }.flowOn(Dispatchers.IO)

    fun observeFavoriteTrackIds(): Flow<Set<String>> =
        database.playlistDao().observeFavoriteTrackIds()
            .map { ids -> ids.toSet() }
            .flowOn(Dispatchers.IO)

    fun observeFavoriteAlbums(limit: Int = LibraryFavoritePolicy.FavoriteAlbumLimit): Flow<List<AlbumSummary>> =
        database.playlistDao().observeFavoriteAlbums(limit.coerceAtLeast(1))
            .flowOn(Dispatchers.IO)

    fun pagedPlaylistTracks(playlistId: String): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = defaultPagingConfig(),
            pagingSourceFactory = {
                when (LibrarySmartPlaylistKind.fromId(playlistId)) {
                    LibrarySmartPlaylistKind.Recent -> database.trackDao().pageRecentlyPlayedTracks()
                    LibrarySmartPlaylistKind.Frequent -> database.trackDao().pageFrequentlyPlayedTracks()
                    LibrarySmartPlaylistKind.Never -> database.trackDao().pageNeverPlayedTracks()
                    LibrarySmartPlaylistKind.Added -> database.trackDao().pageRecentlyAddedTracks()
                    null -> if (LibraryFavoritePolicy.isLikedSongsId(playlistId)) {
                        database.playlistDao().pageFavoriteTracks()
                    } else {
                        database.playlistDao().pagePlaylistTracks(playlistId)
                    }
                }
            },
        ).flow

    suspend fun toggleFavorite(trackId: String): Boolean {
        val id = trackId.trim()
        if (id.isEmpty()) return false
        val dao = database.playlistDao()
        val current = LibraryFavoriteSnapshot(dao.getFavoriteTrackIds().toSet())
        val next = LibraryFavoritePolicy.toggle(current, id)
        val liked = LibraryFavoritePolicy.isLiked(next, id)
        if (liked) {
            dao.upsertFavorite(
                LibraryFavoriteEntity(
                    trackId = id,
                    favoritedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        } else {
            dao.deleteFavorite(id)
        }
        return liked
    }

    suspend fun exportBackupPlaylists(): List<app.echo.android.model.backup.EchoBackupPlaylist> {
        val dao = database.playlistDao()
        return dao.getPlaylistsBySource(LibrarySource.MediaStore.id).map { playlist ->
            val tracks = dao.getPlaylistTracksForPlayback(playlist.id, 10_000)
            app.echo.android.model.backup.EchoBackupPlaylist(
                name = playlist.name,
                tracks = tracks.map { track ->
                    app.echo.android.model.backup.EchoBackupTrackRef(
                        title = track.title,
                        artist = track.artist,
                        relativePath = track.relativePath,
                        durationMs = track.durationMs,
                    )
                },
            )
        }
    }

    suspend fun exportBackupFavorites(): List<app.echo.android.model.backup.EchoBackupTrackRef> {
        val ids = database.playlistDao().getFavoriteTrackIds()
        if (ids.isEmpty()) return emptyList()
        val rows = database.trackDao().getLocalM3uMatchRows().associateBy { it.id }
        return ids.mapNotNull { id ->
            val row = rows[id] ?: return@mapNotNull null
            app.echo.android.model.backup.EchoBackupTrackRef(
                title = row.title,
                artist = row.artist,
                relativePath = row.relativePath,
            )
        }
    }

    suspend fun restoreBackupCatalog(
        playlists: List<app.echo.android.model.backup.EchoBackupPlaylist>,
        favorites: List<app.echo.android.model.backup.EchoBackupTrackRef>,
    ): app.echo.android.model.backup.EchoBackupRestoreResult {
        val rows = database.trackDao().getLocalM3uMatchRows()
        var matched = 0
        var missing = 0
        fun resolve(track: app.echo.android.model.backup.EchoBackupTrackRef): String? {
            val id = EchoBackupCodec.matchTrackId(track, rows)
            if (id == null) {
                missing++
                return null
            }
            matched++
            return id
        }
        var playlistsRestored = 0
        val existing = database.playlistDao().getPlaylistsBySource(LibrarySource.MediaStore.id)
        playlists.forEach { playlist ->
            val trackIds = playlist.tracks.mapNotNull(::resolve)
            val current = existing.firstOrNull { it.name.equals(playlist.name, ignoreCase = true) }
            val record = if (current != null) {
                LibraryPlaylistRecord(
                    id = current.id,
                    name = current.name,
                    trackIds = trackIds,
                    artworkUri = current.artworkUri,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            } else {
                val created = createLocalPlaylist(playlist.name) ?: return@forEach
                LibraryPlaylistRecord(
                    id = created.id,
                    name = created.name,
                    trackIds = trackIds,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            }
            persistPlaylistRecord(record)
            playlistsRestored++
        }
        var favoritesRestored = 0
        val liked = database.playlistDao().getFavoriteTrackIds().toSet()
        favorites.forEach { track ->
            val id = resolve(track) ?: return@forEach
            if (id !in liked) {
                toggleFavorite(id)
            }
            favoritesRestored++
        }
        return app.echo.android.model.backup.EchoBackupRestoreResult(
            playlistsRestored = playlistsRestored,
            favoritesRestored = favoritesRestored,
            tracksMatched = matched,
            tracksMissing = missing,
        )
    }

    suspend fun createLocalPlaylist(name: String): EchoPlaylist? {
        val now = System.currentTimeMillis()
        val playlistId = LibraryPlaylistPolicy.newLocalPlaylistId(now, java.util.UUID.randomUUID().toString())
        val next = LibraryPlaylistPolicy.create(
            catalog = LibraryPlaylistCatalog(),
            name = name,
            id = playlistId,
            nowEpochMs = now,
        )
        val created = next.playlists.singleOrNull() ?: return null
        persistPlaylistRecord(created)
        return created.toEchoPlaylist()
    }

    suspend fun renameLocalPlaylist(playlistId: String, name: String): Boolean {
        if (!isLocalManagedPlaylist(playlistId)) return false
        val catalog = loadPlaylistCatalog(playlistId) ?: return false
        val next = LibraryPlaylistPolicy.rename(
            catalog = catalog,
            playlistId = playlistId,
            name = name,
            nowEpochMs = System.currentTimeMillis(),
        )
        val renamed = next.playlists.singleOrNull() ?: return false
        if (renamed.name == catalog.playlists.single().name) return false
        persistPlaylistRecord(renamed)
        return true
    }

    suspend fun deleteLocalPlaylist(playlistId: String): Boolean {
        if (!isLocalManagedPlaylist(playlistId)) return false
        val catalog = loadPlaylistCatalog(playlistId) ?: return false
        val next = LibraryPlaylistPolicy.delete(catalog, playlistId)
        if (next.playlists.isNotEmpty()) return false
        database.playlistDao().deletePlaylist(playlistId)
        return true
    }

    suspend fun deleteRemoteLibrarySource(source: String) {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return
        if (!LibraryScanPolicy.isRemoteLibrarySource(trimmed)) return
        val dao = database.trackDao()
        val ids = dao.getIdsFromSource(trimmed)
        ids.chunked(DATABASE_BATCH_SIZE).forEach { chunk ->
            dao.deleteFavoritesByTrackIds(chunk)
            dao.deleteScanBatch(chunk)
            yield()
        }
        database.playlistDao().getPlaylistIdsFromSource(trimmed)
            .forEach { playlistId -> database.playlistDao().deletePlaylist(playlistId) }
    }

    suspend fun addTrackToLocalPlaylist(playlistId: String, trackId: String): Boolean {
        if (LibraryFavoritePolicy.isLikedSongsId(playlistId)) {
            val id = trackId.trim()
            if (id.isEmpty()) return false
            if (database.playlistDao().isFavorite(id)) return false
            return toggleFavorite(id)
        }
        if (!isLocalManagedPlaylist(playlistId)) return false
        val catalog = loadPlaylistCatalog(playlistId) ?: return false
        val next = LibraryPlaylistPolicy.addTrack(
            catalog = catalog,
            playlistId = playlistId,
            trackId = trackId,
            nowEpochMs = System.currentTimeMillis(),
        )
        val updated = next.playlists.singleOrNull() ?: return false
        if (updated.trackIds == catalog.playlists.single().trackIds) return false
        persistPlaylistRecord(updated)
        return true
    }

    suspend fun removeTrackFromLocalPlaylist(playlistId: String, trackId: String): Boolean {
        if (LibraryFavoritePolicy.isLikedSongsId(playlistId)) {
            val id = trackId.trim()
            if (id.isEmpty()) return false
            if (!database.playlistDao().isFavorite(id)) return false
            toggleFavorite(id)
            return true
        }
        if (!isLocalManagedPlaylist(playlistId)) return false
        val catalog = loadPlaylistCatalog(playlistId) ?: return false
        val next = LibraryPlaylistPolicy.removeTrack(
            catalog = catalog,
            playlistId = playlistId,
            trackId = trackId,
            nowEpochMs = System.currentTimeMillis(),
        )
        val updated = next.playlists.singleOrNull() ?: return false
        if (updated.trackIds == catalog.playlists.single().trackIds) return false
        persistPlaylistRecord(updated)
        return true
    }

    suspend fun reorderLocalPlaylistTracks(
        playlistId: String,
        fromIndex: Int,
        toIndex: Int,
    ): Boolean {
        if (!isLocalManagedPlaylist(playlistId)) return false
        val catalog = loadPlaylistCatalog(playlistId) ?: return false
        val next = LibraryPlaylistPolicy.reorderTracks(
            catalog = catalog,
            playlistId = playlistId,
            fromIndex = fromIndex,
            toIndex = toIndex,
            nowEpochMs = System.currentTimeMillis(),
        )
        val updated = next.playlists.singleOrNull() ?: return false
        if (updated.trackIds == catalog.playlists.single().trackIds) return false
        persistPlaylistRecord(updated)
        return true
    }

    private fun isLocalManagedPlaylist(playlistId: String): Boolean {
        val id = playlistId.trim()
        if (id.isEmpty()) return false
        if (LibraryFavoritePolicy.isLikedSongsId(id)) return false
        if (LibrarySmartPlaylistPolicy.isSmartPlaylistId(id)) return false
        if (id.startsWith("${LibrarySource.Subsonic.id}:")) return false
        if (id.startsWith("${LibrarySource.WebDav.id}:")) return false
        return true
    }

    private suspend fun loadPlaylistCatalog(playlistId: String): LibraryPlaylistCatalog? {
        val playlist = database.playlistDao().getPlaylist(playlistId) ?: return null
        val trackIds = database.playlistDao().getPlaylistTrackIds(playlistId)
        return LibraryPlaylistCatalog(
            playlists = listOf(
                LibraryPlaylistRecord(
                    id = playlist.id,
                    name = playlist.name,
                    trackIds = trackIds,
                    artworkUri = playlist.artworkUri,
                    updatedAtEpochMs = playlist.updatedAtEpochMs,
                ),
            ),
        )
    }

    private suspend fun syncSubsonicPlaylists(
        endpoint: SubsonicEndpoint,
        client: SubsonicClient,
        source: String,
    ) {
        val remotePlaylists = try {
            withContext(LibraryScanDispatchers.Remote) {
                client.fetchPlaylists()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            EchoErrorLog.record(
                EchoErrorSource.Library,
                error.message ?: "Subsonic playlist sync failed.",
                throwable = error,
            )
            return
        }
        val dao = database.playlistDao()
        val knownTrackIds = database.trackDao().getIdsFromSource(source).toHashSet()
        val seenIds = HashSet<String>()
        val now = System.currentTimeMillis()
        for (chunk in remotePlaylists.chunked(SubsonicSyncPolicy.PlaylistFetchConcurrency)) {
            coroutineContext.ensureActive()
            val fetched = coroutineScope {
                chunk.map { playlist ->
                    async(LibraryScanDispatchers.Remote) {
                        val songs = try {
                            client.fetchPlaylistSongs(playlist.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            null
                        }
                        playlist to songs
                    }
                }.awaitAll()
            }
            for ((playlist, songsOrNull) in fetched) {
                val localId = "${endpoint.sourceId}:playlist:${playlist.id}"
                seenIds += localId
                val songs = songsOrNull ?: continue
                val trackIds = songs.map { "${endpoint.sourceId}:song:${it.id}" }.filter(knownTrackIds::contains)
                if (
                    !SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                        fetchSucceeded = true,
                        remoteSongCount = songs.size,
                        matchedTrackCount = trackIds.size,
                    )
                ) {
                    continue
                }
                val incomingName = playlist.name.ifBlank { "Playlist" }
                val existing = dao.getPlaylist(localId)
                val existingTrackIds = existing?.let { dao.getPlaylistTrackIds(localId) }
                if (
                    !SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                        existingName = existing?.name,
                        existingTrackIds = existingTrackIds,
                        incomingName = incomingName,
                        incomingTrackIds = trackIds,
                    )
                ) {
                    continue
                }
                val artworkUri = playlist.coverArt
                    ?.let(client::coverArtUrl)
                    ?: trackIds.firstOrNull()?.let { trackId -> database.trackDao().getTrackById(trackId)?.artworkUri }
                dao.replacePlaylist(
                    playlist = LibraryPlaylistEntity(
                        id = localId,
                        name = incomingName,
                        source = source,
                        artworkUri = artworkUri,
                        trackCount = trackIds.size,
                        updatedAtEpochMs = now,
                    ),
                    tracks = trackIds.mapIndexed { position, trackId ->
                        LibraryPlaylistTrackEntity(
                            playlistId = localId,
                            trackId = trackId,
                            position = position,
                        )
                    },
                )
            }
        }
        dao.getPlaylistIdsFromSource(source)
            .filterNot(seenIds::contains)
            .forEach { leftoverId -> dao.deletePlaylist(leftoverId) }
    }

    private suspend fun persistPlaylistRecord(record: LibraryPlaylistRecord) {
        val artworkUri = record.artworkUri
            ?: record.trackIds.firstOrNull()
                ?.let { trackId -> database.trackDao().getTrackById(trackId)?.artworkUri }
        database.playlistDao().replacePlaylist(
            playlist = LibraryPlaylistEntity(
                id = record.id,
                name = record.name,
                source = LibrarySource.MediaStore.id,
                artworkUri = artworkUri,
                trackCount = record.trackIds.size,
                updatedAtEpochMs = record.updatedAtEpochMs,
            ),
            tracks = LibraryPlaylistPolicy.trackMemberships(record).map { (trackId, position) ->
                LibraryPlaylistTrackEntity(
                    playlistId = record.id,
                    trackId = trackId,
                    position = position,
                )
            },
        )
    }

    suspend fun albumTracks(albumKey: String): List<LibraryTrackEntity> =
        RemoteAlbumKey.parse(albumKey)?.let { remoteAlbum ->
            database.trackDao().getTracksByRemoteAlbum(remoteAlbum.source, remoteAlbum.albumKey)
        } ?: database.trackDao().getTracksByAlbum(albumKey)

    suspend fun artistTracks(artistKey: String): List<LibraryTrackEntity> =
        database.trackDao().getTracksByArtist(artistKey)

    suspend fun queueAroundTrack(
        query: String?,
        anchorTrackId: String,
        selectedLibrarySource: String = EchoLibrarySelectedSource.Local,
        limit: Int = TRACK_QUEUE_LIMIT,
        sort: LibraryTrackSortMode = LibraryTrackSortMode.Title,
    ): List<LibraryTrackEntity> {
        val dao = database.trackDao()
        val safeLimit = limit.coerceAtLeast(1)
        val anchor = dao.getTrackById(anchorTrackId)
        val candidates = trackQueueCandidates(
            dao = dao,
            query = query,
            selectedLibrarySource = selectedLibrarySource,
            limit = safeLimit,
            sort = sort,
        )
        val merged = LibraryPlaybackQueuePolicy.mergeAnchorIntoQueue(
            anchor = anchor?.let { LibraryPlaybackQueueCandidate(it.id, it.source) },
            candidates = candidates.map { LibraryPlaybackQueueCandidate(it.id, it.source) },
            selectedLibrarySource = selectedLibrarySource,
            limit = safeLimit,
        )
        val byId = buildMap {
            anchor?.let { put(it.id, it) }
            candidates.forEach { put(it.id, it) }
        }
        return merged.mapNotNull { candidate -> byId[candidate.id] }
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

    suspend fun trackById(trackId: String): LibraryTrackEntity? =
        database.trackDao().getTrackById(trackId)

    suspend fun trackByContentUri(contentUri: String): LibraryTrackEntity? =
        database.trackDao().getTrackByContentUri(contentUri)

    suspend fun updateTrackMetadata(update: EchoTrackMetadataUpdate): TrackMetadataUpdateResult {
        val dao = database.trackDao()
        val current = dao.getTrackById(update.trackId)
            ?: return TrackMetadataUpdateResult(false, EmbeddedTagWriteResult.Failed)
        val updated = current.withUserMetadata(
            update = update,
            editedAtEpochMs = System.currentTimeMillis(),
        )
        val indexChanged = !current.hasSameUserMetadata(updated)
        if (indexChanged) {
            dao.upsertBatchWithFts(listOf(updated))
            val albums = listOfNotNull(current.normalizedAlbum, updated.normalizedAlbum).distinct()
            val siblings = albums.flatMap { album -> dao.getLocalTracksByNormalizedAlbum(album) }
            var summaryKeys = current.toSummaryKeySet() + updated.toSummaryKeySet() + siblings.toSummaryKeySet()
            val regrouped = reconcileLocalAlbumGrouping(dao, siblings.ifEmpty { listOf(updated) })
            summaryKeys += regrouped.toSummaryKeySet()
            rebuildSummariesIfNeeded(dao, summaryKeys)
        }
        val target = if (indexChanged) updated else current
        val fileWrite = writeEmbeddedTags(target, update.toAudioTagFields())
        persistWrittenFileStats(dao, target, fileWrite)
        return TrackMetadataUpdateResult(indexUpdated = true, fileWrite = fileWrite)
    }

    suspend fun writeReplayGainTrackGain(trackId: String, gainDb: Float): EmbeddedTagWriteResult {
        val dao = database.trackDao()
        val current = dao.getTrackById(trackId) ?: return EmbeddedTagWriteResult.Failed
        val writer = tagWriter ?: return EmbeddedTagWriteResult.NotLocal
        val fields = writer.fieldsForWrite(current).copy(replayGainTrackGainDb = gainDb)
        val fileWrite = writeEmbeddedTags(current, fields)
        persistWrittenFileStats(dao, current, fileWrite)
        return fileWrite
    }

    suspend fun writeEmbeddedTagsForTrack(
        trackId: String,
        lyricsText: String? = null,
        artworkUri: String? = null,
    ): EmbeddedTagWriteResult {
        val dao = database.trackDao()
        val current = dao.getTrackById(trackId) ?: return EmbeddedTagWriteResult.Failed
        val fields = fieldsForWrite(current, lyricsText = lyricsText, artworkUri = artworkUri)
        val fileWrite = writeEmbeddedTags(current, fields)
        persistWrittenFileStats(dao, current, fileWrite)
        return fileWrite
    }

    private suspend fun writeEmbeddedTags(
        track: LibraryTrackEntity,
        fields: AudioTagFields,
    ): EmbeddedTagWriteResult {
        val writer = tagWriter ?: return EmbeddedTagWriteResult.NotLocal
        return writer.write(track, fields)
    }

    private suspend fun persistWrittenFileStats(
        dao: LibraryTrackDao,
        track: LibraryTrackEntity,
        fileWrite: EmbeddedTagWriteResult,
    ) {
        val written = fileWrite as? EmbeddedTagWriteResult.Written ?: return
        val stamped = track.copy(
            sizeBytes = written.sizeBytes,
            dateModifiedSeconds = written.dateModifiedSeconds,
        ).withScanMetadata()
        if (stamped.fingerprint == track.fingerprint &&
            stamped.sizeBytes == track.sizeBytes &&
            stamped.dateModifiedSeconds == track.dateModifiedSeconds
        ) {
            return
        }
        dao.upsertBatchWithFts(listOf(stamped))
    }

    suspend fun updateTrackArtwork(trackId: String, artworkUri: String): TrackMetadataUpdateResult {
        val dao = database.trackDao()
        val current = dao.getTrackById(trackId)
            ?: return TrackMetadataUpdateResult(false, EmbeddedTagWriteResult.Failed)
        val trimmed = artworkUri.trim().takeIf { it.isNotBlank() }
            ?: return TrackMetadataUpdateResult(false, EmbeddedTagWriteResult.Failed)
        val updated = current.copy(
            artworkUri = trimmed,
            metadataEditedAtEpochMs = System.currentTimeMillis(),
        ).withScanMetadata()
        if (!current.hasSameUserMetadata(updated)) {
            dao.upsertBatchWithFts(listOf(updated))
            rebuildSummariesIfNeeded(dao, current.toSummaryKeySet() + updated.toSummaryKeySet())
        }
        val fields = fieldsForWrite(updated, artworkUri = trimmed)
        val fileWrite = writeEmbeddedTags(updated, fields)
        persistWrittenFileStats(dao, updated, fileWrite)
        return TrackMetadataUpdateResult(indexUpdated = true, fileWrite = fileWrite)
    }

    suspend fun writeEmbeddedLyrics(trackId: String, lyricsText: String): TrackMetadataUpdateResult {
        val dao = database.trackDao()
        val current = dao.getTrackById(trackId)
            ?: return TrackMetadataUpdateResult(false, EmbeddedTagWriteResult.Failed)
        val fields = fieldsForWrite(current, lyricsText = lyricsText)
        val fileWrite = writeEmbeddedTags(current, fields)
        persistWrittenFileStats(dao, current, fileWrite)
        return TrackMetadataUpdateResult(indexUpdated = true, fileWrite = fileWrite)
    }

    private fun fieldsForWrite(
        track: LibraryTrackEntity,
        lyricsText: String? = null,
        artworkUri: String? = null,
    ): AudioTagFields =
        tagWriter?.fieldsForWrite(track, lyricsText, artworkUri)
            ?: track.toAudioTagFields().copy(lyrics = lyricsText)

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

    suspend fun genreTracksForPlayback(
        genreKey: String,
        limit: Int = AGGREGATION_QUEUE_LIMIT,
    ): List<LibraryTrackEntity> =
        database.trackDao().getTracksByGenre(genreKey, limit.coerceAtLeast(1))

    suspend fun folderTracksForPlayback(
        folderKey: String,
        limit: Int = AGGREGATION_QUEUE_LIMIT,
    ): List<LibraryTrackEntity> =
        database.trackDao().getTracksByFolderForPlayback(folderKey, limit.coerceAtLeast(1))

    suspend fun playlistTracksForPlayback(
        playlistId: String,
        limit: Int = AGGREGATION_QUEUE_LIMIT,
    ): List<LibraryTrackEntity> {
        val safeLimit = limit.coerceAtLeast(1)
        return when (LibrarySmartPlaylistKind.fromId(playlistId)) {
            LibrarySmartPlaylistKind.Recent ->
                database.trackDao().getRecentlyPlayedTracksForPlayback(safeLimit)
            LibrarySmartPlaylistKind.Frequent ->
                database.trackDao().getFrequentlyPlayedTracksForPlayback(safeLimit)
            LibrarySmartPlaylistKind.Never ->
                database.trackDao().getNeverPlayedTracksForPlayback(safeLimit)
            LibrarySmartPlaylistKind.Added ->
                database.trackDao().getRecentlyAddedTracksForPlayback(safeLimit)
            null -> if (LibraryFavoritePolicy.isLikedSongsId(playlistId)) {
                database.playlistDao().listFavoriteTracksForBrowse(safeLimit, 0)
            } else {
                database.playlistDao().getPlaylistTracksForPlayback(playlistId, safeLimit)
            }
        }
    }

    suspend fun backfillMissingSampleRates(
        limit: Int = SAMPLE_RATE_BACKFILL_LIMIT,
    ): Int = withContext(LibraryScanDispatchers.Limited) {
        val dao = database.trackDao()
        val missing = dao.getTracksMissingSampleRate(limit.coerceAtLeast(1))
        if (missing.isEmpty()) return@withContext 0
        val updated = ArrayList<LibraryTrackEntity>(missing.size)
        for (track in missing) {
            coroutineContext.ensureActive()
            val rate = scanner.readSampleRateHz(track.contentUri) ?: continue
            if (rate == track.sampleRateHz) continue
            updated += track.copy(sampleRateHz = rate).withFingerprint()
        }
        if (updated.isEmpty()) return@withContext 0
        updated.chunked(DATABASE_BATCH_SIZE).forEach { chunk ->
            dao.upsertBatchWithFts(chunk)
            yield()
        }
        updated.size
    }

    fun refreshMediaStoreSnapshot(
        relativePathPrefix: String? = null,
        batchSize: Int = SCAN_BATCH_SIZE,
        skipSampleRateRead: Boolean = false,
        options: LibraryScanOptions = LibraryScanOptions(),
    ): Flow<LibraryScanProgress> = flow {
        val dao = database.trackDao()
        val rejectedFiles = scanner.rejectedFileCache
        val source = LibrarySource.MediaStore.id
        val normalizedRelativePath = normalizeRelativePathPrefix(relativePathPrefix)
        val relativePathLike = normalizedRelativePath?.let { "${escapeSqlLikeArgument(it)}%" }
        val scanRunId = System.currentTimeMillis()
        var progress = LibraryScanProgress(phase = LibraryScanPhase.Preparing)
        var insertedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var scannedCount = 0
        var totalCount: Int? = null
        var lastProgressEmitCount = 0
        var lastProgressEmitAtMs = 0L

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
                skippedCount = skippedCount,
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
            val documentFingerprints = dao.getDocumentFingerprints()
            val changedFingerprints = mutableMapOf<String, TrackFingerprint>()
            val seenIds = HashSet<String>(existingFingerprints.size)
            val scanOutcome = scanner.scanAudio(
                batchSize = batchSize,
                relativePathPrefix = normalizedRelativePath,
                existingTracks = existingFingerprints,
                readSampleRate = !skipSampleRateRead,
                options = options,
                rejectedFiles = rejectedFiles,
                onSkipped = { skippedCount++ },
                onTotalCount = { count ->
                    totalCount = count
                    emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
                },
                // 增量扫描中未变的行不再走全列拉取,只上报 id 供删除检测
                onUnchangedIds = { ids -> seenIds.addAll(ids) },
                onProgress = { count, currentTrack ->
                    scannedCount = count
                    val now = System.currentTimeMillis()
                    if (
                        LibraryScanPolicy.shouldEmitScanProgress(
                            scannedCount = count,
                            lastEmittedCount = lastProgressEmitCount,
                            elapsedSinceEmitMs = now - lastProgressEmitAtMs,
                        )
                    ) {
                        lastProgressEmitCount = count
                        lastProgressEmitAtMs = now
                        emitProgress(
                            phase = LibraryScanPhase.QueryingMediaStore,
                            currentTitle = currentTrack?.title,
                        )
                    }
                },
                onBatch = { batch ->
                    coroutineContext.ensureActive()
                    seenIds.addAll(batch.map { it.id })
                    val accepted = filterLocalScanBatch(batch, existingFingerprints.keys, options)
                    skippedCount += batch.size - accepted.size
                    val classified = classifyScanBatch(
                        batch = accepted,
                        existingFingerprints = existingFingerprints,
                        editedTracks = editedTracks,
                        scanRunId = scanRunId,
                    )
                    seenIds.addAll(classified.seenIds)
                    emitProgress(phase = LibraryScanPhase.WritingDatabase)
                    writeClassifiedScanBatch(dao, classified)
                    if (documentFingerprints.isNotEmpty()) (classified.inserts + classified.updates).forEach { track ->
                        changedFingerprints[track.id] = TrackFingerprint(track.id, track.contentUri, track.sampleRateHz,
                            track.fingerprint, track.sizeBytes, track.dateModifiedSeconds, track.relativePath, track.durationMs)
                    }
                    insertedCount += classified.inserts.size
                    updatedCount += classified.updates.size
                    lastProgressEmitCount = scannedCount
                    lastProgressEmitAtMs = System.currentTimeMillis()
                    emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
                    yield()
                },
            )
            scannedCount = scanOutcome.scannedCount

            coroutineContext.ensureActive()
            emitProgress(phase = LibraryScanPhase.CleaningRemoved)
            val completeness = LibraryScanCompleteness(
                querySucceeded = scanOutcome.querySucceeded,
                scannedCount = scannedCount,
                existingCount = existingFingerprints.size,
                confirmedEmpty = scanOutcome.querySucceeded,
            )
            val deletion = deleteMissingIfComplete(
                dao = dao,
                completeness = completeness,
                missingIds = {
                    val existingRows = if (relativePathLike == null) {
                        dao.getIdPathsFromSource(source)
                    } else {
                        dao.getIdPathsFromRelativePath(source, relativePathLike)
                    }
                    // 只允许删"本次完整扫过的卷"里的行:SD 卡未挂载或该卷查询失败时,
                    // 其曲目保持原样,防止整卷误删(连带用户元数据编辑丢失)
                    val candidateIds = existingRows
                        .filter { LibraryScanPolicy.isMediaStoreNativeId(it.id) }
                        .filter { options.includesDirectory(it.relativePath) }
                        .filter {
                            LibraryScanPolicy.mediaStoreRowWithinVolumeScopes(
                                relativePath = it.relativePath,
                                scopes = scanOutcome.completeVolumeScopes,
                            )
                        }
                        .map { it.id }
                    LibraryScanPolicy.unseenIds(candidateIds, seenIds)
                },
            )
            if (scanOutcome.querySucceeded) {
                reconcileLocalDuplicates(dao, documentFingerprints,
                    existingFingerprints.values.asSequence().filter { it.id in seenIds && it.id !in changedFingerprints } +
                        changedFingerprints.values.asSequence(),
                )
                reconcileLocalAlbumGrouping(dao, dao.getLocalLibraryTracks())
            }
            emitProgress(
                phase = if (scanOutcome.querySucceeded) LibraryScanPhase.Completed else LibraryScanPhase.Error,
                error = if (scanOutcome.querySucceeded) null else echoText(
                    en = "Scan partially completed. Some storage volumes could not be fully read; existing songs were kept. Check the storage and retry.",
                    zh = "扫描部分完成：部分存储卷未能完整读取，已保留原有曲目。请检查存储设备后重试。",
                    ja = "スキャンは一部完了しました。読み取れないストレージがあります。既存の曲は保持しました。確認して再試行してください。",
                ),
                currentTitle = null,
                deletedCount = deletion.deletedCount,
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
                error = error.message ?: echoText(
                    en = "Library scan failed",
                    zh = "曲库扫描失败",
                    ja = "ライブラリのスキャンに失敗しました",
                ),
                isCompleted = true,
            )
        } finally {
            rejectedFiles.flush()
        }
    }.flowOn(LibraryScanDispatchers.Limited)

    fun refreshDocumentTreeSnapshot(
        treeUri: android.net.Uri,
        relativePathPrefix: String,
        batchSize: Int = DOCUMENT_TREE_SCAN_BATCH_SIZE,
        skipSampleRateRead: Boolean = false,
        options: LibraryScanOptions = LibraryScanOptions(),
    ): Flow<LibraryScanProgress> = flow {
        val dao = database.trackDao()
        val rejectedFiles = scanner.rejectedFileCache
        val source = LibraryScanPolicy.SafSourceId
        val normalizedRelativePath = normalizeRelativePathPrefix(relativePathPrefix)
            ?: error("Document tree scan requires a relative path")
        val relativePathLike = "${escapeSqlLikeArgument(normalizedRelativePath)}%"
        val scanRunId = System.currentTimeMillis()
        var progress = LibraryScanProgress(phase = LibraryScanPhase.Preparing)
        var insertedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var scannedCount = 0
        var deletedCount = 0
        var lastProgressEmitCount = 0
        var lastProgressEmitAtMs = 0L

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
                skippedCount = skippedCount,
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
                ) +
                    dao.getExistingMediaStoreFingerprintsInRelativePath(
                        source = source,
                        relativePathLike = relativePathLike,
                    )
                ).associateBy(TrackFingerprint::id)
            val editedTracks = (
                dao.getMetadataEditedTracksInRelativePath(
                    source = LibrarySource.MediaStore.id,
                    relativePathLike = relativePathLike,
                ) +
                    dao.getMetadataEditedTracksInRelativePath(
                        source = source,
                        relativePathLike = relativePathLike,
                    )
                ).associateBy(LibraryTrackEntity::id)
            val duplicateAliases = mutableMapOf<String, String>()
            val seenIds = HashSet<String>(existingFingerprints.size)
            val unresolvedMediaStoreIds = HashSet<String>()
            val mediaStoreDuplicateKeys = scanner.documentTreeDuplicateKeys(existingFingerprints.values) {
                unresolvedMediaStoreIds.addAll(it)
            }

            emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
            val scanOutcome = documentTreeScanner.scanAudioTree(
                treeUri = treeUri,
                relativePathPrefix = normalizedRelativePath,
                batchSize = batchSize,
                existingTracks = existingFingerprints,
                mediaStoreDuplicateKeys = mediaStoreDuplicateKeys,
                readSampleRate = !skipSampleRateRead,
                options = options,
                rejectedFiles = rejectedFiles,
                onSkipped = { skippedCount++ },
                onDuplicate = { oldId, targetId ->
                    if (oldId in existingFingerprints) duplicateAliases[oldId] = targetId
                },
                onProgress = { count, currentTrack ->
                    scannedCount = count
                    val now = System.currentTimeMillis()
                    if (
                        LibraryScanPolicy.shouldEmitScanProgress(
                            scannedCount = count,
                            lastEmittedCount = lastProgressEmitCount,
                            elapsedSinceEmitMs = now - lastProgressEmitAtMs,
                        )
                    ) {
                        lastProgressEmitCount = count
                        lastProgressEmitAtMs = now
                        emitProgress(
                            phase = LibraryScanPhase.QueryingMediaStore,
                            currentTitle = currentTrack?.title,
                        )
                    }
                },
                onBatch = { batch ->
                    coroutineContext.ensureActive()
                    seenIds.addAll(batch.map { it.id })
                    val recovered = retainMetadataOnFailedReads(dao, batch)
                    val accepted = filterLocalScanBatch(recovered, existingFingerprints.keys, options)
                    skippedCount += batch.size - accepted.size
                    val classified = classifyScanBatch(
                        batch = accepted,
                        existingFingerprints = existingFingerprints,
                        editedTracks = editedTracks,
                        scanRunId = scanRunId,
                    )
                    seenIds.addAll(classified.seenIds)
                    emitProgress(phase = LibraryScanPhase.WritingDatabase)
                    database.withTransaction {
                        writeClassifiedScanBatch(dao, classified)
                        duplicateAliases.forEach { (oldId, targetId) -> dao.mergeScanDuplicate(oldId, targetId) }
                    }
                    seenIds.addAll(duplicateAliases.keys)
                    duplicateAliases.clear()
                    insertedCount += classified.inserts.size
                    updatedCount += classified.updates.size
                    lastProgressEmitCount = scannedCount
                    lastProgressEmitAtMs = System.currentTimeMillis()
                    emitProgress(phase = LibraryScanPhase.QueryingMediaStore)
                    yield()
                },
            )
            scannedCount = scanOutcome.scannedCount

            coroutineContext.ensureActive()
            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            val deletion = deleteMissingIfComplete(
                dao = dao,
                completeness = LibraryScanCompleteness(
                    querySucceeded = scanOutcome.querySucceeded,
                    scannedCount = scannedCount,
                    existingCount = existingFingerprints.size,
                    confirmedEmpty = scanOutcome.querySucceeded,
                ),
                missingIds = {
                    LibraryScanPolicy.unseenIds(
                        existingFingerprints.keys.filter {
                            (LibraryScanPolicy.isSafTrackId(it) || LibraryScanPolicy.isMediaStoreNativeId(it)) &&
                                // A fully listed empty tree proves absence; otherwise unknown identities stay safe.
                                (scannedCount == 0 || it !in unresolvedMediaStoreIds) &&
                                options.includesDirectory(existingFingerprints[it]?.relativePath)
                        },
                        seenIds,
                    )
                },
            )
            deletedCount = deletion.deletedCount
            if (scanOutcome.querySucceeded) {
                reconcileLocalAlbumGrouping(dao, dao.getLocalLibraryTracks())
            }
            emitProgress(
                phase = if (scanOutcome.querySucceeded) LibraryScanPhase.Completed else LibraryScanPhase.Error,
                error = if (scanOutcome.querySucceeded) null else echoText(
                    en = "Scan partially completed: ${scanOutcome.failedReadCount} files or folders could not be read. Existing songs were kept; check the storage and retry.",
                    zh = "扫描部分完成：${scanOutcome.failedReadCount} 个文件或目录读取失败。已保留原有曲目，请检查存储设备后重试。",
                    ja = "スキャンは一部完了：${scanOutcome.failedReadCount} 件の読み取りに失敗しました。既存の曲は保持しました。ストレージを確認して再試行してください。",
                ),
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
        } finally {
            rejectedFiles.flush()
        }
    }.flowOn(LibraryScanDispatchers.Limited)

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
        var changedSummaries = LibrarySummaryKeySet()

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

            emitProgress(
                phase = LibraryScanPhase.Diffing,
                currentTitle = echoText(
                    en = "Reading the remote library index",
                    zh = "读取远程曲库索引",
                    ja = "リモートライブラリの索引を読み込み中",
                ),
            )
            val existingFingerprints = dao.getExistingMediaStoreFingerprints(source)
                .associateBy(TrackFingerprint::id)
            val editedTracks = dao.getMetadataEditedTracks(source).associateBy(LibraryTrackEntity::id)
            val seenIds = HashSet<String>(existingFingerprints.size)

            emitProgress(
                phase = LibraryScanPhase.QueryingMediaStore,
                currentTitle = echoText(
                    en = "Connecting to Navidrome/Subsonic",
                    zh = "连接 Navidrome/Subsonic",
                    ja = "Navidrome/Subsonic に接続中",
                ),
            )
            withContext(LibraryScanDispatchers.Remote) {
                client.ping()
            }
            val (albums, bulkSongs) = coroutineScope {
                val albumsDeferred = async(LibraryScanDispatchers.Remote) { client.fetchAlbums() }
                val bulkDeferred = async(LibraryScanDispatchers.Remote) {
                    try {
                        client.fetchSongsBySearch3()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
                albumsDeferred.await() to bulkDeferred.await()
            }
            val expectedSongCount = albums.sumOf { it.songCount.coerceAtLeast(0) }
            totalCount = expectedSongCount.takeIf { it > 0 } ?: albums.size
            emitProgress(
                phase = LibraryScanPhase.QueryingMediaStore,
                currentTitle = echoText(
                    en = "Found ${albums.size} remote albums",
                    zh = "发现 ${albums.size} 张远程专辑",
                    ja = "リモートアルバム ${albums.size} 枚を検出",
                ),
            )

            val pending = ArrayList<LibraryTrackEntity>(batchSize)
            suspend fun flushPending(title: String?) {
                if (pending.isEmpty()) return
                val written = writeRemoteBatch(dao, pending, existingFingerprints, editedTracks)
                insertedCount += written.insertedCount
                updatedCount += written.updatedCount
                seenIds.addAll(written.seenIds)
                changedSummaries += written.summaryKeys
                pending.clear()
                emitProgress(phase = LibraryScanPhase.WritingDatabase, currentTitle = title)
                yield()
            }

            suspend fun ingestSongs(songs: List<SubsonicSong>, title: String?) {
                for (song in songs) {
                    coroutineContext.ensureActive()
                    if (song.id.isBlank()) continue
                    scannedCount += 1
                    pending += song.toLibraryTrackEntity(endpoint, scanRunId)
                    if (pending.size >= batchSize) {
                        flushPending(title)
                    }
                }
            }

            val usedSearch3 = SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount, bulkSongs.size)
            if (usedSearch3) {
                emitProgress(
                    phase = LibraryScanPhase.QueryingMediaStore,
                    currentTitle = echoText(
                        en = "Read ${bulkSongs.size} remote tracks in bulk",
                        zh = "已批量读取 ${bulkSongs.size} 首远程歌曲",
                        ja = "リモート曲 ${bulkSongs.size} 曲を一括読み込み済み",
                    ),
                )
                ingestSongs(bulkSongs, title = "search3")
            } else {
                // 回退路径去 N+1:与本地库按 albumKey 比对,未变专辑跳过 getAlbum,
                // 只把其本地曲目标记为 seen(轮换窗口内的照常强刷)
                val localTrackIdsByAlbumKey = dao.getTrackAlbumKeys(source)
                    .groupBy({ it.albumKey }, { it.id })
                val fallbackPlan = SubsonicSyncPolicy.planAlbumFallbackSync(
                    albums = albums,
                    localTrackIdsByAlbumKey = localTrackIdsByAlbumKey,
                    refreshSalt = scanRunId,
                )
                if (fallbackPlan.skippedAlbumCount > 0) {
                    seenIds.addAll(fallbackPlan.seenTrackIds)
                    scannedCount += fallbackPlan.skippedTrackCount
                    emitProgress(
                        phase = LibraryScanPhase.QueryingMediaStore,
                        currentTitle = echoText(
                            en = "Skipped ${fallbackPlan.skippedAlbumCount} unchanged albums",
                            zh = "已跳过 ${fallbackPlan.skippedAlbumCount} 张未变专辑",
                            ja = "変更のないアルバム ${fallbackPlan.skippedAlbumCount} 枚をスキップ",
                        ),
                    )
                }
                for (chunk in fallbackPlan.albumsToFetch.chunked(SubsonicSyncPolicy.AlbumFetchConcurrency)) {
                    coroutineContext.ensureActive()
                    val chunkSongs = coroutineScope {
                        chunk.map { album ->
                            async(LibraryScanDispatchers.Remote) {
                                val songs = try {
                                    client.fetchAlbumSongs(album)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Throwable) {
                                    null
                                }
                                album to songs
                            }
                        }.awaitAll()
                    }
                    for ((album, songs) in chunkSongs) {
                        if (songs == null) {
                            val localIds = SubsonicSyncPolicy.localTrackIdsForAlbum(
                                album,
                                localTrackIdsByAlbumKey,
                            )
                            seenIds.addAll(localIds)
                            scannedCount += localIds.size
                            continue
                        }
                        ingestSongs(songs, album.name)
                    }
                    emitProgress(
                        phase = LibraryScanPhase.QueryingMediaStore,
                        currentTitle = chunk.lastOrNull()?.name,
                    )
                }
            }
            flushPending(title = null)
            syncSubsonicPlaylists(endpoint, client, source)

            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            val hitVisitCap = albums.size >= SubsonicClient.MaxAlbumsPerSync ||
                (usedSearch3 && bulkSongs.size >= SubsonicClient.MaxSongsPerSync)
            val deletion = deleteMissingIfComplete(
                dao = dao,
                completeness = LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = scannedCount,
                    existingCount = existingFingerprints.size,
                    hitVisitCap = hitVisitCap ||
                        !SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                            usedSearch3 = usedSearch3,
                            expectedSongCount = expectedSongCount,
                            bulkSongCount = bulkSongs.size,
                            existingRemoteCount = existingFingerprints.size,
                            hitVisitCap = hitVisitCap,
                        ),
                ),
                missingIds = { LibraryScanPolicy.unseenIds(existingFingerprints.keys, seenIds) },
            )
            deletedCount = deletion.deletedCount
            rebuildSummariesIfNeeded(dao, changedSummaries + deletion.summaryKeys)
            emitProgress(phase = LibraryScanPhase.Completed, currentTitle = null, isCompleted = true)
        } catch (error: CancellationException) {
            emitProgress(phase = LibraryScanPhase.Cancelled, currentTitle = null, isCompleted = true)
            throw error
        } catch (error: Throwable) {
            emitProgress(
                phase = LibraryScanPhase.Error,
                currentTitle = null,
                error = error.message ?: echoText(
                    en = "Remote library sync failed",
                    zh = "远程曲库同步失败",
                    ja = "リモートライブラリの同期に失敗しました",
                ),
                isCompleted = true,
            )
        }
    }.flowOn(LibraryScanDispatchers.Limited)

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
        var lastProgressEmitCount = 0
        var lastProgressEmitAtMs = 0L
        var changedSummaries = LibrarySummaryKeySet()
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
            emitProgress(
                phase = LibraryScanPhase.Diffing,
                currentTitle = echoText(
                    en = "Reading the WebDAV index",
                    zh = "读取 WebDAV 索引",
                    ja = "WebDAV 索引を読み込み中",
                ),
            )
            val existingFingerprints = dao.getExistingMediaStoreFingerprints(source)
                .associateBy(TrackFingerprint::id)
            val editedTracks = dao.getMetadataEditedTracks(source).associateBy(LibraryTrackEntity::id)
            val seenIds = HashSet<String>(existingFingerprints.size)

            emitProgress(
                phase = LibraryScanPhase.QueryingMediaStore,
                currentTitle = echoText(
                    en = "Scanning WebDAV folders",
                    zh = "扫描 WebDAV 目录",
                    ja = "WebDAV フォルダーをスキャン中",
                ),
            )
            val visit = client.scanAudioFiles { file ->
                coroutineContext.ensureActive()
                scannedCount += 1
                pending += file.toLibraryTrackEntity(endpoint, scanRunId)
                val now = System.currentTimeMillis()
                if (
                    LibraryScanPolicy.shouldEmitScanProgress(
                        scannedCount = scannedCount,
                        lastEmittedCount = lastProgressEmitCount,
                        elapsedSinceEmitMs = now - lastProgressEmitAtMs,
                    )
                ) {
                    lastProgressEmitCount = scannedCount
                    lastProgressEmitAtMs = now
                    emitProgress(
                        phase = LibraryScanPhase.QueryingMediaStore,
                        currentTitle = file.title,
                    )
                }
                if (pending.size >= batchSize) {
                    val written = writeRemoteBatch(dao, pending, existingFingerprints, editedTracks)
                    insertedCount += written.insertedCount
                    updatedCount += written.updatedCount
                    seenIds.addAll(written.seenIds)
                    changedSummaries += written.summaryKeys
                    pending.clear()
                    emitProgress(phase = LibraryScanPhase.WritingDatabase, currentTitle = file.title)
                    yield()
                }
            }
            if (pending.isNotEmpty()) {
                val written = writeRemoteBatch(dao, pending, existingFingerprints, editedTracks)
                insertedCount += written.insertedCount
                updatedCount += written.updatedCount
                seenIds.addAll(written.seenIds)
                changedSummaries += written.summaryKeys
                pending.clear()
            }

            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            val deletion = deleteMissingIfComplete(
                dao = dao,
                completeness = LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = scannedCount,
                    existingCount = existingFingerprints.size,
                    hitVisitCap = visit.incomplete,
                ),
                missingIds = { LibraryScanPolicy.unseenIds(existingFingerprints.keys, seenIds) },
            )
            deletedCount = deletion.deletedCount
            rebuildSummariesIfNeeded(dao, changedSummaries + deletion.summaryKeys)
            emitProgress(phase = LibraryScanPhase.Completed, currentTitle = null, isCompleted = true)
        } catch (error: CancellationException) {
            emitProgress(phase = LibraryScanPhase.Cancelled, currentTitle = null, isCompleted = true)
            throw error
        } catch (error: Throwable) {
            emitProgress(
                phase = LibraryScanPhase.Error,
                currentTitle = null,
                error = error.message ?: echoText(
                    en = "WebDAV library sync failed",
                    zh = "WebDAV 曲库同步失败",
                    ja = "WebDAV ライブラリの同期に失敗しました",
                ),
                isCompleted = true,
            )
        }
    }.flowOn(LibraryScanDispatchers.Limited)

    suspend fun authenticateJellyfin(endpoint: JellyfinEndpoint): JellyfinEndpoint =
        withContext(LibraryScanDispatchers.Remote) {
            val session = JellyfinClient(endpoint).authenticate()
            endpoint.copy(accessToken = session.accessToken, userId = session.userId)
        }

    fun refreshJellyfinSnapshot(
        endpoint: JellyfinEndpoint,
        batchSize: Int = SCAN_BATCH_SIZE,
        onAuthenticated: ((accessToken: String, userId: String) -> Unit)? = null,
    ): Flow<LibraryScanProgress> = flow {
        val client = JellyfinClient(endpoint)
        val dao = database.trackDao()
        val source = endpoint.sourceId
        val scanRunId = System.currentTimeMillis()
        var progress = LibraryScanProgress(phase = LibraryScanPhase.Preparing)
        var insertedCount = 0
        var updatedCount = 0
        var scannedCount = 0
        var totalCount: Int? = null
        var deletedCount = 0
        var changedSummaries = LibrarySummaryKeySet()
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
            val existingFingerprints = dao.getExistingMediaStoreFingerprints(source)
                .associateBy(TrackFingerprint::id)
            val editedTracks = dao.getMetadataEditedTracks(source).associateBy(LibraryTrackEntity::id)
            val seenIds = HashSet<String>(existingFingerprints.size)
            emitProgress(
                phase = LibraryScanPhase.QueryingMediaStore,
                currentTitle = echoText(
                    en = "Connecting to Jellyfin / Emby",
                    zh = "连接 Jellyfin / Emby",
                    ja = "Jellyfin / Emby に接続中",
                ),
            )
            val session = withContext(LibraryScanDispatchers.Remote) {
                if (endpoint.password.isNotBlank()) {
                    client.authenticate()
                } else {
                    val token = endpoint.accessToken?.trim().orEmpty()
                    val userId = endpoint.userId?.trim().orEmpty()
                    if (token.isNotEmpty() && userId.isNotEmpty()) {
                        JellyfinSession(token, userId)
                    } else {
                        client.authenticate()
                    }
                }
            }
            onAuthenticated?.invoke(session.accessToken, session.userId)
            var startIndex = 0
            var hitCap = false
            while (scannedCount < JellyfinClient.MaxTracksPerSync) {
                coroutineContext.ensureActive()
                val page = withContext(LibraryScanDispatchers.Remote) {
                    client.fetchAudioPage(session, startIndex)
                }
                totalCount = page.totalCount
                if (page.items.isEmpty()) break
                for (item in page.items) {
                    scannedCount += 1
                    pending += item.toLibraryTrackEntity(endpoint, scanRunId)
                    if (pending.size >= batchSize) {
                        val written = writeRemoteBatch(dao, pending, existingFingerprints, editedTracks)
                        insertedCount += written.insertedCount
                        updatedCount += written.updatedCount
                        seenIds.addAll(written.seenIds)
                        changedSummaries += written.summaryKeys
                        pending.clear()
                        emitProgress(phase = LibraryScanPhase.WritingDatabase, currentTitle = item.title)
                        yield()
                    }
                }
                startIndex += page.items.size
                emitProgress(
                    phase = LibraryScanPhase.QueryingMediaStore,
                    currentTitle = page.items.lastOrNull()?.title,
                )
                if (startIndex >= page.totalCount) break
                if (scannedCount >= JellyfinClient.MaxTracksPerSync) {
                    hitCap = true
                    break
                }
            }
            if (pending.isNotEmpty()) {
                val written = writeRemoteBatch(dao, pending, existingFingerprints, editedTracks)
                insertedCount += written.insertedCount
                updatedCount += written.updatedCount
                seenIds.addAll(written.seenIds)
                changedSummaries += written.summaryKeys
            }
            emitProgress(phase = LibraryScanPhase.CleaningRemoved, currentTitle = null)
            val deletion = deleteMissingIfComplete(
                dao = dao,
                completeness = LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = scannedCount,
                    existingCount = existingFingerprints.size,
                    hitVisitCap = hitCap,
                ),
                missingIds = { LibraryScanPolicy.unseenIds(existingFingerprints.keys, seenIds) },
            )
            deletedCount = deletion.deletedCount
            rebuildSummariesIfNeeded(dao, changedSummaries + deletion.summaryKeys)
            emitProgress(phase = LibraryScanPhase.Completed, currentTitle = null, isCompleted = true)
        } catch (error: CancellationException) {
            emitProgress(phase = LibraryScanPhase.Cancelled, currentTitle = null, isCompleted = true)
            throw error
        } catch (error: Throwable) {
            emitProgress(
                phase = LibraryScanPhase.Error,
                currentTitle = null,
                error = error.message ?: echoText(
                    en = "Jellyfin / Emby library sync failed",
                    zh = "Jellyfin / Emby 曲库同步失败",
                    ja = "Jellyfin / Emby ライブラリの同期に失敗しました",
                ),
                isCompleted = true,
            )
        }
    }.flowOn(LibraryScanDispatchers.Limited)

    suspend fun importM3uPlaylist(name: String, text: String): EchoPlaylist? {
        val entries = M3uPlaylistCodec.parse(text)
        if (entries.isEmpty()) return null
        val rows = database.trackDao().getLocalM3uMatchRows()
        val trackIds = entries.mapNotNull { M3uPlaylistCodec.matchTrackId(it, rows) }.distinct()
        if (trackIds.isEmpty()) return null
        val created = createLocalPlaylist(name) ?: return null
        trackIds.forEach { trackId -> addTrackToLocalPlaylist(created.id, trackId) }
        return database.playlistDao().getPlaylist(created.id)?.let { entity ->
            LibraryPlaylistRecord(
                id = entity.id,
                name = entity.name,
                trackIds = database.playlistDao().getPlaylistTrackIds(entity.id),
                artworkUri = entity.artworkUri,
                updatedAtEpochMs = entity.updatedAtEpochMs,
            ).toEchoPlaylist()
        } ?: created
    }

    suspend fun exportM3uPlaylist(playlistId: String): String? {
        val tracks = playlistTracksForPlayback(playlistId, limit = 2_000)
        if (tracks.isEmpty()) return null
        return M3uPlaylistCodec.write(
            tracks.map { track ->
                M3uExportTrack(
                    title = track.title,
                    artist = track.artist,
                    durationMs = track.durationMs,
                    location = track.relativePath
                        ?.takeIf { it.isNotBlank() }
                        ?.let { path ->
                            val fileName = track.contentUri.substringAfterLast('/').takeIf { it.isNotBlank() }
                            if (fileName != null && !path.endsWith(fileName)) "$path/$fileName" else path
                        }
                        ?: track.title,
                )
            },
        )
    }

    suspend fun countTracks(): Int = database.trackDao().countTracks()

    suspend fun countTracksFromSource(source: String): Int =
        database.trackDao().countTracksFromSource(source)

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
        selectedLibrarySource: String,
        limit: Int,
        sort: LibraryTrackSortMode,
    ): List<LibraryTrackEntity> {
        val trimmedQuery = query?.trim().orEmpty()
        val matchQuery = sanitizeFtsQuery(trimmedQuery)
        val rankQuery = ftsRankQuery(trimmedQuery)
        val useFts = matchQuery != null && canUseFts(dao, matchQuery, trimmedQuery)
        val sql = LibraryTrackQueryBuilder.buildTrackQueueSql(
            query = trimmedQuery,
            useFts = useFts,
            localSources = LibraryPlaybackQueuePolicy.usesLocalTrackQueue(selectedLibrarySource),
            limit = limit,
            sort = sort,
        )
        val args = mutableListOf<Any>()
        if (useFts && matchQuery != null) {
            args += matchQuery
            repeat(3) { args += rankQuery }
        } else if (trimmedQuery.isNotBlank()) {
            val likeQuery = "%${trimmedQuery.lowercase()}%"
            repeat(6) { args += likeQuery }
            repeat(3) { args += rankQuery }
        }
        return dao.queryTracks(SimpleSQLiteQuery(sql, args.toTypedArray()))
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
            val updated = staleTracks.map(LibraryPinyinBackfillPolicy::apply)
            val changed = updated.filterIndexed { index, next -> next != staleTracks[index] }
            if (changed.isEmpty()) break
            dao.upsertBatchWithFts(changed)
            backfilled = true
            yield()
        }
        if (backfilled) {
            dao.rebuildLibrarySummaries()
        }
    }

    private suspend fun backfillWavTags() = withContext(LibraryScanDispatchers.Limited) {
        if (scanner.isWavTagBackfillComplete()) return@withContext
        val dao = database.trackDao()
        val ids = dao.getLocalWavTrackIdsForTagBackfill()
        val pending = ArrayList<LibraryTrackEntity>()
        var changed = false
        suspend fun flushPending() {
            if (pending.isEmpty()) return
            dao.upsertBatchWithFts(pending.toList())
            pending.clear()
            changed = true
            yield()
        }
        for (chunk in ids.chunked(WAV_TAG_BACKFILL_BATCH_SIZE)) {
            coroutineContext.ensureActive()
            for (track in dao.getTracksByIds(chunk)) {
                coroutineContext.ensureActive()
                val tags = scanner.readAudioTagsFromUri(track.contentUri) ?: continue
                val next = track.withAudioTags(tags)
                if (next.hasSameUserMetadata(track)) continue
                pending += next.withScanMetadata()
                if (pending.size >= DATABASE_BATCH_SIZE) flushPending()
            }
        }
        flushPending()
        if (changed) dao.rebuildLibrarySummaries()
        scanner.markWavTagBackfillComplete()
    }

    private suspend fun backfillAggregationKeys() = withContext(LibraryScanDispatchers.Limited) {
        if (scanner.isAggregationKeyBackfillComplete()) return@withContext
        val dao = database.trackDao()
        val tracks = dao.getAllTracksForFtsRebuild()
        val pending = ArrayList<LibraryTrackEntity>()
        var changed = false
        suspend fun flushPending() {
            if (pending.isEmpty()) return
            dao.upsertBatchWithFts(pending.toList())
            pending.clear()
            changed = true
            yield()
        }
        for (track in tracks) {
            coroutineContext.ensureActive()
            val cleaned = track.copy(
                title = track.title.takeUnlessUnknownMetadata() ?: UnknownTrackTitle,
                artist = track.artist.takeUnlessUnknownMetadata() ?: canonicalUnknownArtist(),
                album = track.album.takeUnlessUnknownMetadata(),
                albumArtist = track.albumArtist.takeUnlessUnknownMetadata(),
            ).withScanMetadata()
            if (
                cleaned.title == track.title &&
                cleaned.artist == track.artist &&
                cleaned.album == track.album &&
                cleaned.albumArtist == track.albumArtist &&
                cleaned.albumKey == track.albumKey &&
                cleaned.artistKey == track.artistKey &&
                cleaned.normalizedTitle == track.normalizedTitle &&
                cleaned.normalizedArtist == track.normalizedArtist &&
                cleaned.normalizedAlbum == track.normalizedAlbum &&
                cleaned.normalizedAlbumArtist == track.normalizedAlbumArtist
            ) {
                continue
            }
            pending += cleaned
            if (pending.size >= DATABASE_BATCH_SIZE) flushPending()
        }
        flushPending()
        val regrouped = reconcileLocalAlbumGrouping(dao, dao.getLocalLibraryTracks())
        if (changed || regrouped.isNotEmpty()) dao.rebuildLibrarySummaries()
        scanner.markAggregationKeyBackfillComplete()
    }

    private suspend fun reconcileLocalAlbumGrouping(
        dao: LibraryTrackDao,
        tracks: List<LibraryTrackEntity>,
    ): List<LibraryTrackEntity> {
        val changed = LibraryAlbumGrouping.reconcile(tracks)
        if (changed.isEmpty()) return emptyList()
        val before = tracks.associateBy { it.id }
        var summaryKeys = LibrarySummaryKeySet()
        changed.forEach { next ->
            before[next.id]?.let { summaryKeys += it.toSummaryKeySet() }
            summaryKeys += next.toSummaryKeySet()
        }
        changed.chunked(DATABASE_BATCH_SIZE).forEach { chunk ->
            dao.upsertBatchWithFts(chunk)
            yield()
        }
        rebuildSummariesIfNeeded(dao, summaryKeys)
        return changed
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
            // Keep retained pages bounded when the UI leaves and re-enters the library.
            maxSize = 600,
            prefetchDistance = 20,
            enablePlaceholders = false,
        )

    private suspend fun retainMetadataOnFailedReads(dao: LibraryTrackDao, batch: List<LibraryTrackEntity>): List<LibraryTrackEntity> =
        batch.map { incoming ->
            if (incoming.fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint) incoming
            else dao.getTrackById(incoming.id)?.copy(
                contentUri = incoming.contentUri, relativePath = incoming.relativePath,
                sizeBytes = incoming.sizeBytes, dateModifiedSeconds = incoming.dateModifiedSeconds,
                fingerprint = LibraryScanPolicy.PendingDocumentMetadataFingerprint,
            ) ?: incoming
        }

    private suspend fun reconcileLocalDuplicates(
        dao: LibraryTrackDao,
        documents: List<TrackFingerprint>,
        snapshots: Sequence<TrackFingerprint>,
    ) {
        if (documents.isEmpty()) return
        val candidates = documentDuplicateCandidates(snapshots, documents)
        if (candidates.isEmpty()) return
        val native = scanner.documentTreeDuplicateKeys(candidates)
        if (native.isEmpty()) return
        for (document in documents) {
            coroutineContext.ensureActive()
            val name = runCatching {
                val uri = android.net.Uri.parse(document.contentUri)
                if (uri.authority != "com.android.externalstorage.documents") return@runCatching null
                android.provider.DocumentsContract.getDocumentId(uri).substringAfter(':').substringAfterLast('/')
            }.getOrNull() ?: continue
            val key = LibraryScanPolicy.localFileDuplicateKey(document.relativePath, document.sizeBytes, document.dateModifiedSeconds, name) ?: continue
            val target = native[key] ?: continue
            dao.mergeScanDuplicate(document.id, target.id)
            yield()
        }
    }

    private suspend fun deleteMissingIfComplete(
        dao: LibraryTrackDao,
        completeness: LibraryScanCompleteness,
        missingIds: suspend () -> List<String>,
    ): LibraryScanDeletion {
        if (!LibraryScanPolicy.shouldDeleteMissingLibraryRows(completeness)) {
            return LibraryScanDeletion()
        }
        val ids = missingIds()
        if (ids.isEmpty()) return LibraryScanDeletion()
        var summaryKeys = LibrarySummaryKeySet()
        ids.chunked(DATABASE_BATCH_SIZE).forEach { chunk ->
            dao.getSummaryKeyRows(chunk).forEach { row ->
                summaryKeys += row.toSummaryKeySet()
            }
            dao.deleteScanBatch(chunk)
            yield()
        }
        return LibraryScanDeletion(deletedCount = ids.size, summaryKeys = summaryKeys)
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
            dao.upsertScanBatch(chunk)
            yield()
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
        changedSummaries: LibrarySummaryKeySet,
    ) {
        if (changedSummaries.changedKeyCount <= 0) return
        val existingAlbumSummaries = dao.countAlbumSummaries()
        if (
            LibraryScanPolicy.shouldRebuildLibrarySummariesIncrementally(
                changedKeyCount = changedSummaries.changedKeyCount,
                existingAlbumSummaryCount = existingAlbumSummaries,
            )
        ) {
            dao.rebuildLibrarySummariesForKeys(
                albumKeys = changedSummaries.albumKeys,
                artistKeys = changedSummaries.artistKeys,
                folderKeys = changedSummaries.folderKeys,
                genreKeys = changedSummaries.genreKeys,
            )
        } else {
            dao.rebuildLibrarySummaries()
        }
    }

    private companion object {
        const val SCAN_BATCH_SIZE = 500
        const val DOCUMENT_TREE_SCAN_BATCH_SIZE = 200
        const val DATABASE_BATCH_SIZE = 500
        const val SAMPLE_RATE_BACKFILL_LIMIT = 400
        const val PINYIN_BACKFILL_BATCH_SIZE = 200
        const val PINYIN_BACKFILL_START_DELAY_MS = 750L
        const val WAV_TAG_BACKFILL_BATCH_SIZE = 50
        const val RECOMMENDED_TRACK_LIMIT = 8
        const val LISTEN_STATS_SEED_LIMIT = 256
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

private data class LibraryScanDeletion(
    val deletedCount: Int = 0,
    val summaryKeys: LibrarySummaryKeySet = LibrarySummaryKeySet(),
)

private data class ClassifiedScanBatch(
    val inserts: List<LibraryTrackEntity>,
    val updates: List<LibraryTrackEntity>,
    val seenIds: List<String>,
) {
    fun summaryKeys(): LibrarySummaryKeySet = (inserts + updates).toSummaryKeySet()
}

private data class RemoteBatchWriteResult(
    val insertedCount: Int,
    val updatedCount: Int,
    val seenIds: List<String>,
    val summaryKeys: LibrarySummaryKeySet = LibrarySummaryKeySet(),
)

private suspend fun writeRemoteBatch(
    dao: LibraryTrackDao,
    tracks: List<LibraryTrackEntity>,
    existingFingerprints: Map<String, TrackFingerprint>,
    editedTracks: Map<String, LibraryTrackEntity>,
): RemoteBatchWriteResult {
    val inserts = ArrayList<LibraryTrackEntity>(tracks.size)
    val updates = ArrayList<LibraryTrackEntity>(tracks.size)
    val seenIds = ArrayList<String>(tracks.size)
    val existingByContentUri = HashMap<String, LibraryTrackEntity>()
    tracks.map { it.contentUri }.distinct().chunked(500).forEach { chunk ->
        dao.getTracksByContentUris(chunk).forEach { row ->
            existingByContentUri[row.contentUri] = row
        }
    }
    tracks.forEach { track ->
        val remapped = remapRemoteTrackIdentity(track, existingByContentUri)
        val preserved = remapped.prepareRemoteSyncTrack(editedTracks[remapped.id])
        seenIds += preserved.id
        val existingFingerprint = existingFingerprints[preserved.id]?.fingerprint
            ?: existingByContentUri[preserved.contentUri]?.fingerprint
        val action = if (remapped.id != track.id) {
            LibraryScanRowAction.Update
        } else {
            LibraryScanPolicy.scanRowAction(
                existingFingerprint = existingFingerprint,
                incomingFingerprint = preserved.fingerprint,
            )
        }
        when (action) {
            LibraryScanRowAction.Insert -> inserts += preserved
            LibraryScanRowAction.Update -> updates += preserved
            LibraryScanRowAction.RememberSeen -> Unit
        }
    }
    val mutated = inserts + updates
    mutated.chunked(500).forEach { chunk -> dao.upsertBatchWithFts(chunk) }
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
        summaryKeys = mutated.toSummaryKeySet(),
    )
}

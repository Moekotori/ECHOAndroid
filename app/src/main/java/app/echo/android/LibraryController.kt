package app.echo.android

import app.echo.android.model.library.LibraryScanOptions
import android.content.ContentResolver
import android.net.Uri
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.EmbeddedTagWriteResult
import app.echo.android.data.LibraryFolderWatchPolicy
import app.echo.android.data.WatchedLibraryTree
import app.echo.android.data.TrackMetadataUpdateResult
import app.echo.android.data.LibraryScanPolicy
import app.echo.android.data.LibraryHomeRecommendationPolicy
import app.echo.android.data.LocalLibrarySearchResults
import app.echo.android.data.MediaStoreAudioFolder
import app.echo.android.data.JellyfinEndpoint
import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.WebDavEndpoint
import app.echo.android.data.toAlbumSummary
import app.echo.android.data.toEchoTrack
import app.echo.android.data.toListenSeed
import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.ArtistSortMode
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.FolderSortMode
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.i18n.echoText
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Suppress("SpellCheckingInspection")
internal class LibraryController(
    private val repository: EchoLibraryRepository,
    private val scope: CoroutineScope,
    private val settingsStore: EchoSettingsStore,
    private val resolver: ContentResolver,
) {
    private val listSharingStarted = SharingStarted.WhileSubscribed(5_000L)
    private val _libraryQuery = MutableStateFlow("")
    val libraryQuery: StateFlow<String> = _libraryQuery.asStateFlow()
    private val _trackSortMode = MutableStateFlow(LibraryTrackSortMode.Title)
    val trackSortMode: StateFlow<LibraryTrackSortMode> = _trackSortMode.asStateFlow()
    private val _albumSortMode = MutableStateFlow(AlbumSortMode.Title)
    val albumSortMode: StateFlow<AlbumSortMode> = _albumSortMode.asStateFlow()
    private val _artistSortMode = MutableStateFlow(ArtistSortMode.Name)
    val artistSortMode: StateFlow<ArtistSortMode> = _artistSortMode.asStateFlow()
    private val _folderSortMode = MutableStateFlow(FolderSortMode.Path)
    val folderSortMode: StateFlow<FolderSortMode> = _folderSortMode.asStateFlow()
    private val _scanState = MutableStateFlow(LibraryScanProgress())
    val scanState: StateFlow<LibraryScanProgress> = _scanState.asStateFlow()
    private val _remoteScanState = MutableStateFlow(LibraryScanProgress())
    val remoteScanState: StateFlow<LibraryScanProgress> = _remoteScanState.asStateFlow()

    private var playbackOccupiesStorage: () -> Boolean = { false }
    private var mediaStoreObserver: app.echo.android.data.MediaStoreLibraryObserver? = null

    private val libraryMutationInProgress: Flow<Boolean> =
        combine(_scanState, _remoteScanState) { local, remote ->
            local.isScanning || remote.isScanning
        }.distinctUntilChanged()

    private val debouncedLibraryQuery: Flow<String> =
        _libraryQuery
            .map(String::trim)
            .debounce(300.milliseconds)
            .distinctUntilChanged()

    val tracks: Flow<PagingData<EchoTrack>> =
        combine(debouncedLibraryQuery, _trackSortMode) { query, sort -> query to sort }
            .flatMapLatest { (query, sort) -> repository.pagedTracks(query, sort) }
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(scope)

    val albums: Flow<PagingData<AlbumSummary>> =
        combine(debouncedLibraryQuery, _albumSortMode) { query, sort -> query to sort }
            .flatMapLatest { (query, sort) -> repository.pagedAlbums(query, sort) }
            .cachedIn(scope)

    val remoteAlbums: Flow<PagingData<AlbumSummary>> =
        combine(debouncedLibraryQuery, _albumSortMode) { query, sort -> query to sort }
            .flatMapLatest { (query, sort) -> repository.pagedRemoteAlbums(query, sort) }
            .cachedIn(scope)

    val artists: Flow<PagingData<ArtistSummary>> =
        combine(debouncedLibraryQuery, _artistSortMode) { query, sort -> query to sort }
            .flatMapLatest { (query, sort) -> repository.pagedArtists(query, sort) }
            .cachedIn(scope)

    val genres: Flow<PagingData<app.echo.android.model.library.GenreSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedGenres(query) }
            .cachedIn(scope)

    val folders: Flow<PagingData<FolderSummary>> =
        combine(debouncedLibraryQuery, _folderSortMode) { query, sort -> query to sort }
            .flatMapLatest { (query, sort) -> repository.pagedFolders(query, sort) }
            .cachedIn(scope)

    val localPlaylists: StateFlow<List<EchoPlaylist>> =
        repository.observeLocalPlaylists()
            .stateIn(scope, listSharingStarted, emptyList())

    val favoriteTrackIds: StateFlow<Set<String>> =
        repository.observeFavoriteTrackIds()
            .stateIn(scope, listSharingStarted, emptySet())

    val favoriteAlbums: StateFlow<List<AlbumSummary>> =
        repository.observeFavoriteAlbums()
            .holdDuringLibraryMutation()
            .stateIn(scope, listSharingStarted, emptyList())

    val libraryStats: StateFlow<LibraryStats> =
        repository.observeLibraryStats()
            .debounce(400.milliseconds)
            .distinctUntilChanged()
            .stateIn(scope, listSharingStarted, LibraryStats())

    val recommendedTracks: StateFlow<List<EchoTrack>> =
        repository.observeRecommendedTracks()
            .holdDuringLibraryMutation()
            .map { tracks -> tracks.map { it.toEchoTrack() } }
            .stateIn(scope, listSharingStarted, emptyList())

    val recentlyAddedAlbums: StateFlow<List<AlbumSummary>> =
        repository.observeRecentlyAddedAlbums()
            .stateIn(scope, listSharingStarted, emptyList())

    private val recommendationSalt = MutableStateFlow(0)
    private var lastRecommendationSalt = 0
    private var lastRecommendedKeys: List<String> = emptyList()
    val recommendedAlbums: StateFlow<List<AlbumSummary>> =
        combine(
            repository.observeAlbumListenStats()
                .debounce(400.milliseconds)
                .holdDuringLibraryMutation(),
            recommendationSalt,
        ) { rows, salt ->
            val keys = LibraryHomeRecommendationPolicy.resolveAlbumKeys(
                seeds = rows.map { it.toListenSeed() },
                nowEpochMs = System.currentTimeMillis(),
                refreshSalt = salt,
                previousSalt = lastRecommendationSalt,
                previousKeys = lastRecommendedKeys,
            )
            lastRecommendationSalt = salt
            lastRecommendedKeys = keys
            val byKey = rows.associateBy { it.albumKey }
            keys.mapNotNull { key -> byKey[key]?.toAlbumSummary() }
        }
            .stateIn(scope, listSharingStarted, emptyList())

    fun refreshHomeRecommendations() {
        recommendationSalt.value += 1
    }

    fun setPlaybackOccupiesStorage(check: () -> Boolean) {
        playbackOccupiesStorage = check
    }

    private var scanJob: Job? = null
    private var remoteScanJob: Job? = null
    private var sampleRateBackfillJob: Job? = null
    private var foregroundWatchJob: Job? = null
    private var autoWatchRunning = false
    private var pendingAutoWatch = false
    private var pendingMediaStoreRefresh = false
    private var effectivePerformanceMode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced

    val currentQuery: String
        get() = _libraryQuery.value

    fun albumTrackPaging(albumKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedAlbumTracks(albumKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }

    fun artistAlbumPaging(artistKey: String): Flow<PagingData<AlbumSummary>> =
        repository.pagedArtistAlbums(artistKey)

    fun observeArtistSummary(artistKey: String): Flow<ArtistSummary?> =
        repository.observeArtistSummary(artistKey)

    fun artistTrackPaging(artistKey: String, query: String? = null, sort: LibraryTrackSortMode = LibraryTrackSortMode.Album): Flow<PagingData<EchoTrack>> =
        repository.pagedArtistTracks(artistKey, query, sort)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }

    fun genreTrackPaging(genreKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedGenreTracks(genreKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }

    fun folderTrackPaging(folderKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedFolderTracks(folderKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }

    fun playlistTrackPaging(playlistId: String): Flow<PagingData<EchoTrack>> =
        repository.pagedPlaylistTracks(playlistId)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }

    fun updateLibraryQuery(query: String) {
        _libraryQuery.value = query
    }

    fun updateTrackSortMode(sortMode: LibraryTrackSortMode) {
        _trackSortMode.value = sortMode
    }

    fun updateAlbumSortMode(sortMode: AlbumSortMode) {
        _albumSortMode.value = sortMode
    }

    fun updateArtistSortMode(sortMode: ArtistSortMode) {
        _artistSortMode.value = sortMode
    }

    fun updateFolderSortMode(sortMode: FolderSortMode) {
        _folderSortMode.value = sortMode
    }

    fun setEffectivePerformanceMode(mode: EchoEffectivePerformanceMode) {
        val previous = effectivePerformanceMode
        if (previous == mode) return
        effectivePerformanceMode = mode
        if (
            LibraryScanPolicy.shouldBackfillMissingSampleRates(
                wasLightweight = previous.isLightweight,
                isLightweight = mode.isLightweight,
            )
        ) {
            startMissingSampleRateBackfill()
        }
    }

    fun refreshLibrary(options: LibraryScanOptions = LibraryScanOptions()) {
        refreshLibrary(relativePathPrefix = null, options = options)
    }

    fun startWatchingMediaStore(resolver: android.content.ContentResolver) {
        if (mediaStoreObserver != null) return
        mediaStoreObserver = app.echo.android.data.MediaStoreLibraryObserver(
            resolver = resolver,
            scope = scope,
            onChanged = {
                when {
                    playbackOccupiesStorage() -> Unit
                    scanJob?.isActive == true -> pendingMediaStoreRefresh = true
                    else -> refreshLibrary()
                }
            },
        ).also { it.start() }
        refreshLibraryIfEmpty()
    }

    fun onForeground() {
        foregroundWatchJob?.cancel()
        foregroundWatchJob = scope.launch {
            delay(LibraryFolderWatchPolicy.ForegroundDebounceMs)
            if (!settingsStore.watchedFolderRescanEnabled()) return@launch
            refreshWatchedTreesIfDue()
        }
    }

    fun cancelWatchedFolderRescan() {
        foregroundWatchJob?.cancel()
        pendingAutoWatch = false
        if (autoWatchRunning) scanJob?.cancel()
    }

    fun refreshLibraryIfEmpty() {
        if (scanJob?.isActive == true) return
        scope.launch {
            val localMediaStoreCount = withContext(Dispatchers.IO) {
                repository.countTracksFromSource(LibrarySource.MediaStore.id)
            }
            if (!LibraryScanPolicy.shouldRefreshLocalLibraryAfterPermissionGrant(localMediaStoreCount)) {
                return@launch
            }
            refreshLibrary()
        }
    }

    fun refreshLibraryFolder(treeUri: Uri, options: LibraryScanOptions = LibraryScanOptions()) {
        val folder = MediaStoreAudioFolder.fromTreeUri(treeUri)
        if (folder == null) {
            val message = "Unsupported folder source. Please choose a local music folder or scan all audio."
            _scanState.value = LibraryScanProgress(
                phase = LibraryScanPhase.Error,
                error = message,
                isCompleted = true,
            )
            EchoErrorLog.record(EchoErrorSource.Library, message)
            return
        }
        rememberWatchedTree(treeUri, folder, options)
        if (folder.treeUri == null) {
            refreshLibrary(relativePathPrefix = folder.relativePathPrefix, options = options)
        } else {
            startScanJob(auto = false) {
                scanDocumentTree(folder, options, quiet = false)
            }
        }
    }

    private fun refreshLibrary(relativePathPrefix: String?, options: LibraryScanOptions) {
        startScanJob(auto = false) {
            try {
                repository.refreshMediaStoreSnapshot(
                    relativePathPrefix = relativePathPrefix,
                    skipSampleRateRead = skipSampleRateRead(),
                    options = options,
                )
                    .collect { progress -> publishScanProgress(_scanState, progress, "Library scan failed") }
            } catch (error: CancellationException) {
                _scanState.value = _scanState.value.copy(
                    phase = LibraryScanPhase.Cancelled,
                    currentTitle = null,
                    error = null,
                    isCompleted = true,
                )
                throw error
            } catch (error: Throwable) {
                publishScanFailure(_scanState, error.message ?: "Library scan failed", error)
            }
        }
    }

    private fun startScanJob(auto: Boolean, block: suspend () -> Unit) {
        val current = scanJob
        if (current?.isActive == true) {
            if (auto) {
                pendingAutoWatch = true
                return
            }
            if (autoWatchRunning) {
                current.cancel()
            } else {
                return
            }
        }
        val job = scope.launch {
            autoWatchRunning = auto
            try {
                block()
            } finally {
                autoWatchRunning = false
            }
        }
        scanJob = job
        job.invokeOnCompletion {
            scope.launch {
                if (scanJob === job) scanJob = null
                drainPendingScans()
            }
        }
    }

    private fun drainPendingScans() {
        if (scanJob?.isActive == true) return
        if (pendingAutoWatch) {
            pendingAutoWatch = false
            refreshWatchedTreesIfDue()
            return
        }
        if (pendingMediaStoreRefresh) {
            pendingMediaStoreRefresh = false
            if (!playbackOccupiesStorage()) refreshLibrary()
        }
    }

    private fun rememberWatchedTree(
        treeUri: Uri,
        folder: MediaStoreAudioFolder,
        options: LibraryScanOptions,
    ) {
        val documentId = folder.documentId?.takeIf { it.isNotBlank() } ?: return
        if (folder.treeUri == null) return
        scope.launch(Dispatchers.IO) {
            val incoming = WatchedLibraryTree(
                uri = treeUri.toString(),
                documentId = documentId,
                lastScanEpochMs = System.currentTimeMillis(),
                minDurationMs = options.minDurationMs,
                minSizeBytes = options.minSizeBytes,
                excludeNonMusicFolders = options.excludeNonMusicFolders,
                excludeHiddenFolders = options.excludeHiddenFolders,
            )
            val current = settingsStore.watchedLibraryTrees()
            settingsStore.setWatchedLibraryTrees(LibraryFolderWatchPolicy.remember(current, incoming))
        }
    }

    private fun refreshWatchedTreesIfDue() {
        if (playbackOccupiesStorage()) return
        startScanJob(auto = true) {
            val granted = resolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
            val stored = withContext(Dispatchers.IO) { settingsStore.watchedLibraryTrees() }
            val pruned = LibraryFolderWatchPolicy.pruneRevoked(stored, granted)
            if (pruned != stored) {
                withContext(Dispatchers.IO) { settingsStore.setWatchedLibraryTrees(pruned) }
            }
            val due = LibraryFolderWatchPolicy.treesDueForAutoScan(
                trees = pruned,
                nowEpochMs = System.currentTimeMillis(),
                storageBusy = playbackOccupiesStorage(),
                lightweight = effectivePerformanceMode.isLightweight,
                enabled = settingsStore.watchedFolderRescanEnabled(),
            )
            var watched = pruned
            for (tree in due) {
                if (playbackOccupiesStorage()) break
                val uri = runCatching { Uri.parse(tree.uri) }.getOrNull() ?: continue
                val folder = MediaStoreAudioFolder.fromTreeUri(uri) ?: continue
                if (folder.treeUri == null) continue
                scanDocumentTree(folder, tree.scanOptions(), quiet = true)
                val now = System.currentTimeMillis()
                watched = LibraryFolderWatchPolicy.markScanned(watched, tree.uri, now)
                withContext(Dispatchers.IO) { settingsStore.setWatchedLibraryTrees(watched) }
            }
        }
    }

    private suspend fun scanDocumentTree(
        folder: MediaStoreAudioFolder,
        options: LibraryScanOptions,
        quiet: Boolean,
    ) {
        val treeUri = folder.treeUri ?: return
        try {
            repository.refreshDocumentTreeSnapshot(
                treeUri = treeUri,
                relativePathPrefix = folder.relativePathPrefix,
                skipSampleRateRead = skipSampleRateRead(),
                options = options,
            ).collect { progress ->
                if (!quiet) {
                    publishScanProgress(_scanState, progress, "Document tree scan failed")
                } else if (
                    progress.isCompleted &&
                    LibraryFolderWatchPolicy.scanMadeLibraryChanges(
                        inserted = progress.insertedCount,
                        updated = progress.updatedCount,
                        deleted = progress.deletedCount,
                    )
                ) {
                    publishScanProgress(_scanState, progress, "Document tree scan failed")
                } else if (progress.phase == LibraryScanPhase.Error) {
                    EchoErrorLog.record(
                        EchoErrorSource.Library,
                        progress.error ?: "Document tree scan failed",
                    )
                }
            }
        } catch (error: CancellationException) {
            if (!quiet) {
                _scanState.value = _scanState.value.copy(
                    phase = LibraryScanPhase.Cancelled,
                    currentTitle = null,
                    error = null,
                    isCompleted = true,
                )
            }
            throw error
        } catch (error: Throwable) {
            if (quiet) {
                EchoErrorLog.record(
                    EchoErrorSource.Library,
                    error.message ?: "Document tree scan failed",
                    throwable = error,
                )
            } else {
                publishScanFailure(_scanState, error.message ?: "Document tree scan failed", error)
            }
        }
    }

    fun cancelScan() {
        foregroundWatchJob?.cancel()
        pendingAutoWatch = false
        val job = scanJob
        if (job?.isActive == true) {
            job.cancel()
            _scanState.value = _scanState.value.copy(
                phase = LibraryScanPhase.Cancelled,
                currentTitle = null,
                error = null,
                isCompleted = true,
            )
        }
    }

    fun refreshSubsonic(endpoint: SubsonicEndpoint, onSucceeded: (() -> Unit)? = null) {
        startRemoteSync(
            fallbackError = echoText(
                en = "Subsonic / Navidrome sync failed",
                zh = "Subsonic / Navidrome 同步失败",
                ja = "Subsonic / Navidrome の同期に失敗しました",
            ),
            onSucceeded = onSucceeded,
        ) {
            repository.refreshSubsonicSnapshot(endpoint)
        }
    }

    fun deleteRemoteSource(source: String) {
        scope.launch(Dispatchers.IO) {
            repository.deleteRemoteLibrarySource(source)
        }
    }

    fun refreshWebDav(endpoint: WebDavEndpoint) {
        startRemoteSync(
            fallbackError = echoText(
                en = "WebDAV sync failed",
                zh = "WebDAV 同步失败",
                ja = "WebDAV の同期に失敗しました",
            ),
        ) {
            repository.refreshWebDavSnapshot(endpoint)
        }
    }

    fun refreshJellyfin(
        endpoint: JellyfinEndpoint,
        onSucceeded: ((accessToken: String, userId: String) -> Unit)? = null,
    ) {
        var accessToken: String? = null
        var userId: String? = null
        startRemoteSync(
            fallbackError = echoText(
                en = "Jellyfin / Emby sync failed",
                zh = "Jellyfin / Emby 同步失败",
                ja = "Jellyfin / Emby の同期に失敗しました",
            ),
            onSucceeded = {
                val token = accessToken
                val id = userId
                if (!token.isNullOrBlank() && !id.isNullOrBlank()) {
                    onSucceeded?.invoke(token, id)
                }
            },
        ) {
            repository.refreshJellyfinSnapshot(endpoint) { token, id ->
                accessToken = token
                userId = id
            }
        }
    }

    suspend fun authenticateJellyfin(endpoint: JellyfinEndpoint): JellyfinEndpoint =
        withContext(Dispatchers.IO) {
            repository.authenticateJellyfin(endpoint)
        }

    suspend fun importM3uPlaylist(name: String, text: String): EchoPlaylist? =
        withContext(Dispatchers.IO) {
            repository.importM3uPlaylist(name, text)
        }

    suspend fun exportM3uPlaylist(playlistId: String): String? =
        withContext(Dispatchers.IO) {
            repository.exportM3uPlaylist(playlistId)
        }

    private fun startRemoteSync(
        fallbackError: String,
        onSucceeded: (() -> Unit)? = null,
        progressFlow: () -> Flow<LibraryScanProgress>,
    ) {
        if (remoteScanJob?.isActive == true) {
            _remoteScanState.value = _remoteScanState.value.copy(
                currentTitle = echoText(
                    en = "A remote library sync is already running. Cancel it or wait for it to finish",
                    zh = "已有远程曲库同步正在进行，请先取消或等待完成",
                    ja = "リモートライブラリの同期が実行中です。キャンセルするか完了を待ってください",
                ),
                error = echoText(
                    en = "A remote library sync is already running. Cancel it or wait for it to finish",
                    zh = "已有远程曲库同步正在进行，请先取消或等待完成",
                    ja = "リモートライブラリの同期が実行中です。キャンセルするか完了を待ってください",
                ),
            )
            return
        }
        _remoteScanState.value = LibraryScanProgress(phase = LibraryScanPhase.Preparing)
        remoteScanJob = scope.launch {
            try {
                progressFlow().collect { progress ->
                    publishScanProgress(_remoteScanState, progress, fallbackError)
                    if (progress.phase == LibraryScanPhase.Completed && progress.isCompleted) {
                        onSucceeded?.invoke()
                    }
                }
            } catch (error: CancellationException) {
                _remoteScanState.value = _remoteScanState.value.copy(
                    phase = LibraryScanPhase.Cancelled,
                    currentTitle = null,
                    error = null,
                    isCompleted = true,
                )
                throw error
            } catch (error: Throwable) {
                publishScanFailure(_remoteScanState, error.message ?: fallbackError, error)
            }
        }
    }

    fun cancelRemoteScan() {
        val job = remoteScanJob
        if (job?.isActive == true) {
            job.cancel()
            _remoteScanState.value = _remoteScanState.value.copy(
                phase = LibraryScanPhase.Cancelled,
                currentTitle = null,
                error = null,
                isCompleted = true,
            )
        }
    }

    suspend fun queueAroundTrack(
        trackId: String,
        selectedLibrarySource: String,
    ): List<EchoTrack> =
        withContext(Dispatchers.IO) {
            repository.queueAroundTrack(
                query = currentQuery,
                anchorTrackId = trackId,
                selectedLibrarySource = selectedLibrarySource,
                sort = _trackSortMode.value,
            ).map { it.toEchoTrack() }
        }

    suspend fun albumSummaryForTrack(trackId: String): AlbumSummary? =
        withContext(Dispatchers.IO) {
            repository.albumSummaryForTrack(trackId)
        }

    suspend fun artistSummaryForTrack(trackId: String): ArtistSummary? =
        withContext(Dispatchers.IO) {
            repository.artistSummaryForTrack(trackId)
        }

    suspend fun albumTracksForPlayback(albumKey: String): List<EchoTrack> =
        withContext(Dispatchers.IO) {
            repository.albumTracksForPlayback(albumKey).map { it.toEchoTrack() }
        }

    suspend fun artistTracksForPlayback(artistKey: String): List<EchoTrack> =
        withContext(Dispatchers.IO) {
            repository.artistTracksForPlayback(artistKey).map { it.toEchoTrack() }
        }

    suspend fun genreTracksForPlayback(genreKey: String): List<EchoTrack> =
        withContext(Dispatchers.IO) {
            repository.genreTracksForPlayback(genreKey).map { it.toEchoTrack() }
        }

    suspend fun folderTracksForPlayback(folderKey: String): List<EchoTrack> =
        withContext(Dispatchers.IO) {
            repository.folderTracksForPlayback(folderKey).map { it.toEchoTrack() }
        }

    suspend fun playlistTracksForPlayback(playlistId: String): List<EchoTrack> =
        withContext(Dispatchers.IO) {
            repository.playlistTracksForPlayback(playlistId).map { it.toEchoTrack() }
        }

    suspend fun trackById(trackId: String): EchoTrack? =
        withContext(Dispatchers.IO) {
            repository.trackById(trackId)?.toEchoTrack()
        }

    suspend fun toggleFavorite(trackId: String): Boolean =
        withContext(Dispatchers.IO) {
            repository.toggleFavorite(trackId)
        }

    suspend fun exportBackupPlaylists() =
        withContext(Dispatchers.IO) {
            repository.exportBackupPlaylists()
        }

    suspend fun exportBackupFavorites() =
        withContext(Dispatchers.IO) {
            repository.exportBackupFavorites()
        }

    suspend fun restoreBackupCatalog(
        playlists: List<app.echo.android.model.backup.EchoBackupPlaylist>,
        favorites: List<app.echo.android.model.backup.EchoBackupTrackRef>,
    ) = withContext(Dispatchers.IO) {
        repository.restoreBackupCatalog(playlists, favorites)
    }

    suspend fun createLocalPlaylist(name: String): EchoPlaylist? =
        withContext(Dispatchers.IO) {
            repository.createLocalPlaylist(name)
        }

    suspend fun renameLocalPlaylist(playlistId: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            repository.renameLocalPlaylist(playlistId, name)
        }

    suspend fun deleteLocalPlaylist(playlistId: String): Boolean =
        withContext(Dispatchers.IO) {
            repository.deleteLocalPlaylist(playlistId)
        }

    suspend fun addTrackToLocalPlaylist(playlistId: String, trackId: String): Boolean =
        withContext(Dispatchers.IO) {
            repository.addTrackToLocalPlaylist(playlistId, trackId)
        }

    suspend fun removeTrackFromLocalPlaylist(playlistId: String, trackId: String): Boolean =
        withContext(Dispatchers.IO) {
            repository.removeTrackFromLocalPlaylist(playlistId, trackId)
        }

    suspend fun reorderLocalPlaylistTracks(
        playlistId: String,
        fromIndex: Int,
        toIndex: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            repository.reorderLocalPlaylistTracks(playlistId, fromIndex, toIndex)
        }

    suspend fun searchLocalLibrary(query: String): LocalLibrarySearchResults =
        withContext(Dispatchers.IO) {
            repository.searchLocalLibrary(query)
        }

    suspend fun writeReplayGainTrackGain(trackId: String, gainDb: Float) =
        withContext(Dispatchers.IO) {
            repository.writeReplayGainTrackGain(trackId, gainDb)
        }

    suspend fun updateTrackMetadata(update: EchoTrackMetadataUpdate): TrackMetadataUpdateResult =
        withContext(Dispatchers.IO) {
            repository.updateTrackMetadata(update)
        }

    suspend fun writeEmbeddedTagsForTrack(
        trackId: String,
        lyricsText: String? = null,
        artworkUri: String? = null,
    ): EmbeddedTagWriteResult =
        withContext(Dispatchers.IO) {
            repository.writeEmbeddedTagsForTrack(trackId, lyricsText, artworkUri)
        }

    suspend fun updateTrackArtwork(trackId: String, artworkUri: Uri): TrackMetadataUpdateResult =
        withContext(Dispatchers.IO) {
            repository.updateTrackArtwork(trackId, artworkUri.toString())
        }

    suspend fun writeEmbeddedLyrics(trackId: String, lyricsText: String): TrackMetadataUpdateResult =
        withContext(Dispatchers.IO) {
            repository.writeEmbeddedLyrics(trackId, lyricsText)
        }

    fun clear() {
        mediaStoreObserver?.stop()
        mediaStoreObserver = null
        foregroundWatchJob?.cancel()
        pendingAutoWatch = false
        pendingMediaStoreRefresh = false
        scanJob?.cancel()
        remoteScanJob?.cancel()
        sampleRateBackfillJob?.cancel()
    }

    private fun startMissingSampleRateBackfill() {
        if (scanJob?.isActive == true) return
        if (sampleRateBackfillJob?.isActive == true) return
        if (playbackOccupiesStorage()) return
        sampleRateBackfillJob = scope.launch {
            runCatching { repository.backfillMissingSampleRates() }
        }
    }

    private fun skipSampleRateRead(): Boolean =
        LibraryScanPolicy.shouldSkipSampleRateRead(
            lightweight = effectivePerformanceMode.isLightweight,
            storageBusy = playbackOccupiesStorage(),
        )

    private fun <T> Flow<T>.holdDuringLibraryMutation(): Flow<T> =
        libraryMutationInProgress.flatMapLatest { mutating ->
            if (mutating) {
                flow { awaitCancellation() }
            } else {
                this@holdDuringLibraryMutation
            }
        }

    private fun publishScanProgress(
        target: MutableStateFlow<LibraryScanProgress>,
        progress: LibraryScanProgress,
        fallback: String,
    ) {
        target.value = progress
        if (progress.phase == LibraryScanPhase.Error) {
            EchoErrorLog.record(EchoErrorSource.Library, progress.error ?: fallback)
        }
    }

    private fun publishScanFailure(
        target: MutableStateFlow<LibraryScanProgress>,
        message: String,
        error: Throwable,
    ) {
        target.value = target.value.copy(
            phase = LibraryScanPhase.Error,
            currentTitle = null,
            error = message,
            isCompleted = true,
        )
        EchoErrorLog.record(EchoErrorSource.Library, message, throwable = error)
    }
}

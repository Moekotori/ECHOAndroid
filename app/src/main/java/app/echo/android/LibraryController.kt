package app.echo.android

import android.net.Uri
import androidx.paging.PagingData
import androidx.paging.map
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.LibraryScanPolicy
import app.echo.android.data.LibraryHomeRecommendationPolicy
import app.echo.android.data.LocalLibrarySearchResults
import app.echo.android.data.MediaStoreAudioFolder
import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.WebDavEndpoint
import app.echo.android.data.toAlbumSummary
import app.echo.android.data.toEchoTrack
import app.echo.android.data.toListenSeed
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Suppress("SpellCheckingInspection")
internal class LibraryController(
    private val repository: EchoLibraryRepository,
    private val scope: CoroutineScope,
) {
    private val _libraryQuery = MutableStateFlow("")
    val libraryQuery: StateFlow<String> = _libraryQuery.asStateFlow()
    private val _trackSortMode = MutableStateFlow(LibraryTrackSortMode.Title)
    val trackSortMode: StateFlow<LibraryTrackSortMode> = _trackSortMode.asStateFlow()
    private val _scanState = MutableStateFlow(LibraryScanProgress())
    val scanState: StateFlow<LibraryScanProgress> = _scanState.asStateFlow()
    private val _remoteScanState = MutableStateFlow(LibraryScanProgress())
    val remoteScanState: StateFlow<LibraryScanProgress> = _remoteScanState.asStateFlow()

    private var playbackOccupiesStorage: () -> Boolean = { false }

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

    val albums: Flow<PagingData<AlbumSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedAlbums(query) }

    val remoteAlbums: Flow<PagingData<AlbumSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedRemoteAlbums(query) }

    val artists: Flow<PagingData<ArtistSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedArtists(query) }

    val folders: Flow<PagingData<FolderSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedFolders(query) }

    val localPlaylists: Flow<List<EchoPlaylist>> =
        repository.observeLocalPlaylists()

    val favoriteTrackIds: Flow<Set<String>> =
        repository.observeFavoriteTrackIds()

    val favoriteAlbums: Flow<List<AlbumSummary>> =
        repository.observeFavoriteAlbums()
            .holdDuringLibraryMutation()

    val libraryStats: Flow<LibraryStats> =
        repository.observeLibraryStats()
            .debounce(400.milliseconds)
            .distinctUntilChanged()

    val recommendedTracks: Flow<List<EchoTrack>> =
        repository.observeRecommendedTracks()
            .holdDuringLibraryMutation()
            .map { tracks -> tracks.map { it.toEchoTrack() } }

    val recentlyAddedAlbums: Flow<List<AlbumSummary>> =
        repository.observeRecentlyAddedAlbums()

    private val recommendationSalt = MutableStateFlow(0)
    private var lastRecommendationSalt = 0
    private var lastRecommendedKeys: List<String> = emptyList()
    val recommendedAlbums: Flow<List<AlbumSummary>> =
        combine(
            repository.observeAlbumListenStats().holdDuringLibraryMutation(),
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

    fun refreshHomeRecommendations() {
        recommendationSalt.value += 1
    }

    fun setPlaybackOccupiesStorage(check: () -> Boolean) {
        playbackOccupiesStorage = check
    }

    private var scanJob: Job? = null
    private var remoteScanJob: Job? = null
    private var effectivePerformanceMode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced

    val currentQuery: String
        get() = _libraryQuery.value

    fun albumTrackPaging(albumKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedAlbumTracks(albumKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }

    fun artistTrackPaging(artistKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedArtistTracks(artistKey)
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

    fun setEffectivePerformanceMode(mode: EchoEffectivePerformanceMode) {
        effectivePerformanceMode = mode
    }

    fun refreshLibrary() {
        refreshLibrary(relativePathPrefix = null)
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

    fun refreshLibraryFolder(treeUri: Uri) {
        val folder = MediaStoreAudioFolder.fromTreeUri(treeUri)
        if (folder == null) {
            _scanState.value = LibraryScanProgress(
                phase = LibraryScanPhase.Error,
                error = "Unsupported folder source. Please choose a local music folder or scan all audio.",
                isCompleted = true,
            )
            return
        }
        if (folder.treeUri == null) {
            refreshLibrary(relativePathPrefix = folder.relativePathPrefix)
        } else {
            refreshDocumentTree(folder)
        }
    }

    private fun refreshLibrary(relativePathPrefix: String?) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            try {
                repository.refreshMediaStoreSnapshot(
                    relativePathPrefix = relativePathPrefix,
                    skipSampleRateRead = skipSampleRateRead(),
                )
                    .collect { progress -> _scanState.value = progress }
            } catch (error: CancellationException) {
                _scanState.value = _scanState.value.copy(
                    phase = LibraryScanPhase.Cancelled,
                    currentTitle = null,
                    error = null,
                    isCompleted = true,
                )
                throw error
            } catch (error: Throwable) {
                _scanState.value = _scanState.value.copy(
                    phase = LibraryScanPhase.Error,
                    currentTitle = null,
                    error = error.message ?: "Library scan failed",
                    isCompleted = true,
                )
            }
        }
    }

    private fun refreshDocumentTree(folder: MediaStoreAudioFolder) {
        val treeUri = folder.treeUri ?: return
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            try {
                repository.refreshDocumentTreeSnapshot(
                    treeUri = treeUri,
                    relativePathPrefix = folder.relativePathPrefix,
                    skipSampleRateRead = skipSampleRateRead(),
                )
                    .collect { progress -> _scanState.value = progress }
            } catch (error: CancellationException) {
                _scanState.value = _scanState.value.copy(
                    phase = LibraryScanPhase.Cancelled,
                    currentTitle = null,
                    error = null,
                    isCompleted = true,
                )
                throw error
            } catch (error: Throwable) {
                _scanState.value = _scanState.value.copy(
                    phase = LibraryScanPhase.Error,
                    currentTitle = null,
                    error = error.message ?: "Document tree scan failed",
                    isCompleted = true,
                )
            }
        }
    }

    fun cancelScan() {
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

    fun refreshSubsonic(endpoint: SubsonicEndpoint) {
        startRemoteSync(
            fallbackError = echoText(
                en = "Subsonic / Navidrome sync failed",
                zh = "Subsonic / Navidrome 同步失败",
                ja = "Subsonic / Navidrome の同期に失敗しました",
            ),
        ) {
            repository.refreshSubsonicSnapshot(endpoint)
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

    private fun startRemoteSync(
        fallbackError: String,
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
                progressFlow().collect { progress -> _remoteScanState.value = progress }
            } catch (error: CancellationException) {
                _remoteScanState.value = _remoteScanState.value.copy(
                    phase = LibraryScanPhase.Cancelled,
                    currentTitle = null,
                    error = null,
                    isCompleted = true,
                )
                throw error
            } catch (error: Throwable) {
                _remoteScanState.value = _remoteScanState.value.copy(
                    phase = LibraryScanPhase.Error,
                    currentTitle = null,
                    error = error.message ?: fallbackError,
                    isCompleted = true,
                )
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

    suspend fun updateTrackMetadata(update: EchoTrackMetadataUpdate): Boolean =
        withContext(Dispatchers.IO) {
            repository.updateTrackMetadata(update)
        }

    suspend fun updateTrackArtwork(trackId: String, artworkUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            repository.updateTrackArtwork(trackId, artworkUri.toString())
        }

    fun clear() {
        scanJob?.cancel()
        remoteScanJob?.cancel()
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
}

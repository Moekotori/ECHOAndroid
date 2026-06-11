package app.echo.android

import android.net.Uri
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.MediaStoreAudioFolder
import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.WebDavEndpoint
import app.echo.android.data.toEchoTrack
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.library.LibraryTrackSortMode
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class LibraryController(
    private val repository: EchoLibraryRepository,
    private val scope: CoroutineScope,
) {
    private val _libraryQuery = MutableStateFlow("")
    val libraryQuery: StateFlow<String> = _libraryQuery.asStateFlow()
    private val _trackSortMode = MutableStateFlow(LibraryTrackSortMode.Title)
    val trackSortMode: StateFlow<LibraryTrackSortMode> = _trackSortMode.asStateFlow()

    private val debouncedLibraryQuery: Flow<String> =
        _libraryQuery
            .map(String::trim)
            .debounce(300L)
            .distinctUntilChanged()

    val tracks: Flow<PagingData<EchoTrack>> =
        combine(debouncedLibraryQuery, _trackSortMode) { query, sort -> query to sort }
            .flatMapLatest { (query, sort) -> repository.pagedTracks(query, sort) }
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(scope)

    val albums: Flow<PagingData<AlbumSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedAlbums(query) }
            .cachedIn(scope)

    val remoteAlbums: Flow<PagingData<AlbumSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedRemoteAlbums(query) }
            .cachedIn(scope)

    val artists: Flow<PagingData<ArtistSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedArtists(query) }
            .cachedIn(scope)

    val folders: Flow<PagingData<FolderSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedFolders(query) }
            .cachedIn(scope)

    val neteasePlaylists: Flow<List<EchoPlaylist>> =
        repository.observeNeteasePlaylists()

    val libraryStats: Flow<LibraryStats> = repository.observeLibraryStats()

    val recommendedTracks: Flow<List<EchoTrack>> =
        repository.observeRecommendedTracks()
            .map { tracks -> tracks.map { it.toEchoTrack() } }

    val recentlyAddedAlbums: Flow<List<AlbumSummary>> =
        repository.observeRecentlyAddedAlbums()

    private val _scanState = MutableStateFlow(LibraryScanProgress())
    val scanState: StateFlow<LibraryScanProgress> = _scanState.asStateFlow()
    private val _remoteScanState = MutableStateFlow(LibraryScanProgress())
    val remoteScanState: StateFlow<LibraryScanProgress> = _remoteScanState.asStateFlow()

    private var scanJob: Job? = null
    private var remoteScanJob: Job? = null

    val currentQuery: String
        get() = _libraryQuery.value

    fun albumTrackPaging(albumKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedAlbumTracks(albumKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(scope)

    fun artistTrackPaging(artistKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedArtistTracks(artistKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(scope)

    fun folderTrackPaging(folderKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedFolderTracks(folderKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(scope)

    fun playlistTrackPaging(playlistId: String): Flow<PagingData<EchoTrack>> =
        repository.pagedPlaylistTracks(playlistId)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(scope)

    fun updateLibraryQuery(query: String) {
        _libraryQuery.value = query
    }

    fun updateTrackSortMode(sortMode: LibraryTrackSortMode) {
        _trackSortMode.value = sortMode
    }

    fun refreshLibrary() {
        refreshLibrary(relativePathPrefix = null)
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
        refreshLibrary(relativePathPrefix = folder.relativePathPrefix)
    }

    private fun refreshLibrary(relativePathPrefix: String?) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            try {
                repository.refreshMediaStoreSnapshot(relativePathPrefix = relativePathPrefix)
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
            duplicateMessage = "已有远程曲库同步正在进行，请先取消或等待完成",
            fallbackError = "Subsonic / Navidrome 同步失败",
        ) {
            repository.refreshSubsonicSnapshot(endpoint)
        }
    }

    fun refreshWebDav(endpoint: WebDavEndpoint) {
        startRemoteSync(
            duplicateMessage = "已有远程曲库同步正在进行，请先取消或等待完成",
            fallbackError = "WebDAV 同步失败",
        ) {
            repository.refreshWebDavSnapshot(endpoint)
        }
    }

    private fun startRemoteSync(
        duplicateMessage: String,
        fallbackError: String,
        progressFlow: () -> Flow<LibraryScanProgress>,
    ) {
        if (remoteScanJob?.isActive == true) {
            _remoteScanState.value = _remoteScanState.value.copy(
                currentTitle = duplicateMessage,
                error = duplicateMessage,
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

    suspend fun queueAroundTrack(trackId: String): List<EchoTrack> =
        withContext(Dispatchers.IO) {
            repository.queueAroundTrack(
                query = currentQuery,
                anchorTrackId = trackId,
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

    fun clear() {
        scanJob?.cancel()
        remoteScanJob?.cancel()
    }
}

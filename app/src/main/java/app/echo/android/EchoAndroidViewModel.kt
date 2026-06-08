package app.echo.android

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.MediaStoreAudioFolder
import app.echo.android.data.MediaStoreTrackScanner
import app.echo.android.data.toEchoTrack
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackError
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.toEchoPlaybackStatus
import app.echo.android.playback.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@UnstableApi
class EchoAndroidViewModel(application: Application) : AndroidViewModel(application) {
    private val database = EchoLibraryDatabase.create(application)
    private val repository = EchoLibraryRepository(
        database = database,
        scanner = MediaStoreTrackScanner(application.contentResolver),
    )

    private val _libraryQuery = MutableStateFlow("")
    val libraryQuery: StateFlow<String> = _libraryQuery.asStateFlow()

    private val debouncedLibraryQuery: Flow<String> =
        _libraryQuery
            .map(String::trim)
            .debounce(300L)
            .distinctUntilChanged()

    val tracks: Flow<PagingData<EchoTrack>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedTracks(query) }
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(viewModelScope)

    val albums: Flow<PagingData<AlbumSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedAlbums(query) }
            .cachedIn(viewModelScope)

    val artists: Flow<PagingData<ArtistSummary>> =
        debouncedLibraryQuery
            .flatMapLatest { query -> repository.pagedArtists(query) }
            .cachedIn(viewModelScope)

    val libraryStats: Flow<LibraryStats> = repository.observeLibraryStats()

    val recommendedTracks: Flow<List<EchoTrack>> =
        repository.observeRecommendedTracks()
            .map { tracks -> tracks.map { it.toEchoTrack() } }

    fun albumTrackPaging(albumKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedAlbumTracks(albumKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(viewModelScope)

    fun artistTrackPaging(artistKey: String): Flow<PagingData<EchoTrack>> =
        repository.pagedArtistTracks(artistKey)
            .map { pagingData -> pagingData.map { it.toEchoTrack() } }
            .cachedIn(viewModelScope)

    private val _scanState = MutableStateFlow(LibraryScanProgress())
    val scanState: StateFlow<LibraryScanProgress> = _scanState.asStateFlow()

    private val _playbackStatus = MutableStateFlow(EchoPlaybackStatus())
    val playbackStatus: StateFlow<EchoPlaybackStatus> = _playbackStatus.asStateFlow()

    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var scanJob: Job? = null

    init {
        connectController()
    }

    fun refreshLibrary() {
        refreshLibrary(relativePathPrefix = null)
    }

    fun refreshLibraryFolder(treeUri: Uri) {
        val folder = MediaStoreAudioFolder.fromTreeUri(treeUri)
        if (folder == null) {
            _scanState.value = LibraryScanProgress(
                phase = LibraryScanPhase.Error,
                error = "暂不支持这个目录来源。请选择内部存储中的音乐文件夹，或使用全盘扫描。",
                isCompleted = true,
            )
            return
        }
        refreshLibrary(relativePathPrefix = folder.relativePathPrefix)
    }

    private fun refreshLibrary(relativePathPrefix: String?) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
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
                    error = error.message ?: "曲库扫描失败",
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

    fun updateLibraryQuery(query: String) {
        _libraryQuery.value = query
    }

    fun play(track: EchoTrack) {
        controller?.run {
            setMediaItem(track.toMediaItem())
            prepare()
            play()
        }
    }

    fun playQueue(queue: List<EchoTrack>, startIndex: Int) {
        if (queue.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(0, queue.lastIndex)
        controller?.run {
            setMediaItems(queue.map { it.toMediaItem() }, safeStartIndex, 0L)
            prepare()
            play()
        }
    }

    fun playTrackFromLibrary(trackId: String) {
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) {
                repository.queueAroundTrack(
                    query = _libraryQuery.value,
                    anchorTrackId = trackId,
                ).map { it.toEchoTrack() }
            }
            val startIndex = queue.indexOfFirst { it.id == trackId }.takeIf { it >= 0 } ?: 0
            if (queue.isNotEmpty()) playQueue(queue, startIndex)
        }
    }

    fun playAlbum(albumKey: String) {
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) {
                repository.albumTracksForPlayback(albumKey).map { it.toEchoTrack() }
            }
            if (queue.isNotEmpty()) playQueue(queue, 0)
        }
    }

    fun playArtist(artistKey: String) {
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) {
                repository.artistTracksForPlayback(artistKey).map { it.toEchoTrack() }
            }
            if (queue.isNotEmpty()) playQueue(queue, 0)
        }
    }

    fun playPause() {
        controller?.run {
            if (isPlaying) pause() else play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun cycleRepeatMode() {
        controller?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            updatePlaybackStatus(this)
        }
    }

    fun toggleShuffle() {
        controller?.run {
            shuffleModeEnabled = !shuffleModeEnabled
            updatePlaybackStatus(this)
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        progressJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        database.close()
        super.onCleared()
    }

    private fun connectController() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, EchoPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    future.get()
                }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    updatePlaybackStatus(mediaController)
                    startProgressUpdates()
                }.onFailure { error ->
                    _playbackStatus.value = EchoPlaybackStatus(
                        state = EchoPlaybackState.Error,
                        diagnostics = EchoPlaybackDiagnostics(
                            lastError = EchoPlaybackError(
                                kind = EchoAudioErrorKind.Unknown,
                                message = error.message ?: "媒体控制器连接失败",
                                recoverable = true,
                            ),
                        ),
                    )
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updatePlaybackStatus(player)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            updatePlaybackStatus(controller ?: return)
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                controller?.let(::updatePlaybackStatus)
                delay(500L)
            }
        }
    }

    private fun updatePlaybackStatus(player: Player) {
        _playbackStatus.value = player.toEchoPlaybackStatus()
    }
}

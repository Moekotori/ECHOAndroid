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
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.lyrics.LocalLyricsResolver
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsLoadState
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
    private val lyricsResolver = LocalLyricsResolver(application.contentResolver)
    private val importedLyricsStore = ImportedLyricsStore(application)

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

    val recentlyAddedAlbums: Flow<List<AlbumSummary>> =
        repository.observeRecentlyAddedAlbums()

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
    private val _lyricsState = MutableStateFlow<EchoLyricsLoadState>(EchoLyricsLoadState.Idle)
    val lyricsState: StateFlow<EchoLyricsLoadState> = _lyricsState.asStateFlow()
    private val _recentPlaybackAlbums = MutableStateFlow<List<AlbumSummary>>(emptyList())
    val recentPlaybackAlbums: StateFlow<List<AlbumSummary>> = _recentPlaybackAlbums.asStateFlow()
    private val _recentPlaybackArtists = MutableStateFlow<List<ArtistSummary>>(emptyList())
    val recentPlaybackArtists: StateFlow<List<ArtistSummary>> = _recentPlaybackArtists.asStateFlow()

    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var scanJob: Job? = null
    private var lyricsJob: Job? = null
    private var lastLyricsTrackId: String? = null
    private var currentLyricsUserOffsetMs: Long = 0L
    private var lastRecentPlaybackTrackId: String? = null
    private val albumPlaybackCounts = mutableMapOf<String, Int>()
    private val artistPlaybackCounts = mutableMapOf<String, Int>()

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

    fun openCurrentPlaybackAlbum(onFound: (AlbumSummary) -> Unit) {
        val trackId = _playbackStatus.value.track?.id ?: return
        viewModelScope.launch {
            val album = withContext(Dispatchers.IO) {
                repository.albumSummaryForTrack(trackId)
            }
            album?.let(onFound)
        }
    }

    fun openCurrentPlaybackArtist(onFound: (ArtistSummary) -> Unit) {
        val trackId = _playbackStatus.value.track?.id ?: return
        viewModelScope.launch {
            val artist = withContext(Dispatchers.IO) {
                repository.artistSummaryForTrack(trackId)
            }
            artist?.let(onFound)
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

    fun shuffleAlbum(albumKey: String) {
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) {
                repository.albumTracksForPlayback(albumKey).map { it.toEchoTrack() }.shuffled()
            }
            if (queue.isNotEmpty()) {
                playQueue(queue, 0)
                controller?.shuffleModeEnabled = true
            }
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

    fun shuffleArtist(artistKey: String) {
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) {
                repository.artistTracksForPlayback(artistKey).map { it.toEchoTrack() }.shuffled()
            }
            if (queue.isNotEmpty()) {
                playQueue(queue, 0)
                controller?.shuffleModeEnabled = true
            }
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

    fun cyclePlayMode() {
        controller?.run {
            shuffleModeEnabled = false
            repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) {
                Player.REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ONE
            }
            updatePlaybackStatus(this)
        }
    }

    fun importLyrics(uri: Uri) {
        val trackIdAtImport = _playbackStatus.value.track?.id
        lastLyricsTrackId = trackIdAtImport
        lyricsJob?.cancel()
        _lyricsState.value = EchoLyricsLoadState.Loading
        lyricsJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val userOffsetMs = trackIdAtImport?.let { importedLyricsStore.lyricsOffsetForTrack(it) } ?: 0L
                    lyricsResolver.loadFromUri(uri)
                        ?.takeIf { it.lines.isNotEmpty() }
                        ?.also {
                            if (trackIdAtImport != null) {
                                runCatching { importedLyricsStore.bindLyrics(trackIdAtImport, uri) }
                            }
                        }
                        ?.withUserOffset(userOffsetMs)
                        ?.let(EchoLyricsLoadState::Ready)
                        ?.let { LyricsLoadResult(state = it, userOffsetMs = userOffsetMs) }
                        ?: EchoLyricsLoadState.Error("歌词文件为空，或不是支持的文本歌词格式")
                            .let(::LyricsLoadResult)
                }.getOrElse { error ->
                    LyricsLoadResult(EchoLyricsLoadState.Error(error.message ?: "歌词导入失败"))
                }
            }
            if (_playbackStatus.value.track?.id == trackIdAtImport) {
                currentLyricsUserOffsetMs = result.userOffsetMs
                _lyricsState.value = result.state
            }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        val trackId = _playbackStatus.value.track?.id ?: return
        val ready = _lyricsState.value as? EchoLyricsLoadState.Ready ?: return
        val targetOffset = (currentLyricsUserOffsetMs + deltaMs).coerceIn(-30_000L, 30_000L)
        val actualDelta = targetOffset - currentLyricsUserOffsetMs
        if (actualDelta == 0L) return

        currentLyricsUserOffsetMs = targetOffset
        _lyricsState.value = EchoLyricsLoadState.Ready(ready.lyrics.shiftedBy(actualDelta, userOffsetMs = targetOffset))
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { importedLyricsStore.setLyricsOffset(trackId, targetOffset) }
        }
    }

    fun resetLyricsOffset() {
        val trackId = _playbackStatus.value.track?.id ?: return
        val ready = _lyricsState.value as? EchoLyricsLoadState.Ready ?: return
        val actualDelta = -currentLyricsUserOffsetMs
        if (actualDelta == 0L) return

        currentLyricsUserOffsetMs = 0L
        _lyricsState.value = EchoLyricsLoadState.Ready(ready.lyrics.shiftedBy(actualDelta, userOffsetMs = 0L))
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { importedLyricsStore.setLyricsOffset(trackId, 0L) }
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        lyricsJob?.cancel()
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
        val status = player.toEchoPlaybackStatus()
        _playbackStatus.value = status
        val trackId = status.track?.id
        updateLyricsForTrack(trackId)
        if (trackId != null && trackId != lastRecentPlaybackTrackId && (status.isPlaying || status.positionMs > 0L)) {
            lastRecentPlaybackTrackId = trackId
            viewModelScope.launch {
                val album = withContext(Dispatchers.IO) {
                    repository.albumSummaryForTrack(trackId)
                }
                val artist = withContext(Dispatchers.IO) {
                    repository.artistSummaryForTrack(trackId)
                }
                album?.let { summary ->
                    albumPlaybackCounts[summary.albumKey] = (albumPlaybackCounts[summary.albumKey] ?: 0) + 1
                    _recentPlaybackAlbums.value = (listOf(summary) + _recentPlaybackAlbums.value)
                        .distinctBy { it.albumKey }
                        .sortedByDescending { albumPlaybackCounts[it.albumKey] ?: 0 }
                        .take(12)
                }
                artist?.let { summary ->
                    artistPlaybackCounts[summary.artistKey] = (artistPlaybackCounts[summary.artistKey] ?: 0) + 1
                    _recentPlaybackArtists.value = (listOf(summary) + _recentPlaybackArtists.value)
                        .distinctBy { it.artistKey }
                        .sortedByDescending { artistPlaybackCounts[it.artistKey] ?: 0 }
                        .take(8)
                }
            }
        }
    }

    private fun updateLyricsForTrack(trackId: String?) {
        if (trackId == lastLyricsTrackId) return
        lastLyricsTrackId = trackId
        lyricsJob?.cancel()
        if (trackId == null) {
            currentLyricsUserOffsetMs = 0L
            _lyricsState.value = EchoLyricsLoadState.Idle
            return
        }

        _lyricsState.value = EchoLyricsLoadState.Loading
        lyricsJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val track = repository.trackForLyrics(trackId)
                    if (track == null) {
                        LyricsLoadResult(EchoLyricsLoadState.Missing)
                    } else {
                        val userOffsetMs = importedLyricsStore.lyricsOffsetForTrack(trackId)
                        val importedLyrics = importedLyricsStore.lyricsUriForTrack(trackId)
                            ?.let(lyricsResolver::loadFromUri)
                        (importedLyrics ?: lyricsResolver.loadForTrack(track))
                            ?.takeIf { it.lines.isNotEmpty() }
                            ?.withUserOffset(userOffsetMs)
                            ?.let(EchoLyricsLoadState::Ready)
                            ?.let { LyricsLoadResult(state = it, userOffsetMs = userOffsetMs) }
                            ?: LyricsLoadResult(EchoLyricsLoadState.Missing)
                    }
                }.getOrElse { error ->
                    LyricsLoadResult(EchoLyricsLoadState.Error(error.message ?: "歌词读取失败"))
                }
            }
            currentLyricsUserOffsetMs = result.userOffsetMs
            _lyricsState.value = result.state
        }
    }

    private data class LyricsLoadResult(
        val state: EchoLyricsLoadState,
        val userOffsetMs: Long = 0L,
    )

    private fun EchoLyrics.withUserOffset(userOffsetMs: Long): EchoLyrics =
        if (userOffsetMs == 0L) {
            this
        } else {
            shiftedBy(userOffsetMs, userOffsetMs = userOffsetMs)
        }

    private fun EchoLyrics.shiftedBy(deltaMs: Long, userOffsetMs: Long): EchoLyrics =
        copy(
            lines = lines.map { it.shiftedBy(deltaMs) },
            offsetMs = offsetMs + deltaMs,
            metadata = metadata + ("user_offset_ms" to userOffsetMs.toString()),
        )

    private fun EchoLyricLine.shiftedBy(deltaMs: Long): EchoLyricLine =
        copy(
            startMs = shiftTimestamp(startMs, deltaMs),
            endMs = endMs?.let { shiftTimestamp(it, deltaMs) },
            words = words.map { it.shiftedBy(deltaMs) },
        )

    private fun EchoLyricWord.shiftedBy(deltaMs: Long): EchoLyricWord =
        copy(
            startMs = shiftTimestamp(startMs, deltaMs),
            endMs = endMs?.let { shiftTimestamp(it, deltaMs) },
        )

    private fun shiftTimestamp(valueMs: Long, deltaMs: Long): Long =
        if (valueMs < 0L) valueMs else (valueMs + deltaMs).coerceAtLeast(0L)
}

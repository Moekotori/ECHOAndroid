package app.echo.android

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.data.MediaStoreTrackScanner
import app.echo.android.playback.EchoPlaybackDiagnostics
import app.echo.android.playback.EchoPlaybackError
import app.echo.android.playback.EchoRepeatMode
import app.echo.android.playback.EchoPlaybackState
import app.echo.android.playback.EchoPlaybackStatus
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.EchoTrackRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryScanUiState(
    val isScanning: Boolean = false,
    val lastScanCount: Int? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class EchoAndroidViewModel(application: Application) : AndroidViewModel(application) {
    private val database = EchoLibraryDatabase.create(application)
    private val repository = EchoLibraryRepository(
        database = database,
        scanner = MediaStoreTrackScanner(application.contentResolver),
    )

    private val _libraryQuery = MutableStateFlow("")
    val libraryQuery: StateFlow<String> = _libraryQuery.asStateFlow()

    val tracks: Flow<PagingData<LibraryTrackEntity>> =
        _libraryQuery
            .flatMapLatest { query -> repository.pagedTracks(query) }
            .cachedIn(viewModelScope)

    private val _scanState = MutableStateFlow(LibraryScanUiState())
    val scanState: StateFlow<LibraryScanUiState> = _scanState.asStateFlow()

    private val _playbackStatus = MutableStateFlow(EchoPlaybackStatus())
    val playbackStatus: StateFlow<EchoPlaybackStatus> = _playbackStatus.asStateFlow()

    private var controller: MediaController? = null
    private var progressJob: Job? = null

    init {
        connectController()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _scanState.value = LibraryScanUiState(isScanning = true)
            runCatching {
                withContext(Dispatchers.IO) { repository.refreshMediaStoreSnapshot() }
            }.onSuccess { count ->
                _scanState.value = LibraryScanUiState(lastScanCount = count)
            }.onFailure { error ->
                _scanState.value = LibraryScanUiState(error = error.message ?: "曲库扫描失败")
            }
        }
    }

    fun updateLibraryQuery(query: String) {
        _libraryQuery.value = query
    }

    fun play(track: LibraryTrackEntity) {
        controller?.run {
            setMediaItem(track.toMediaItem())
            prepare()
            play()
        }
    }

    fun playQueue(queue: List<LibraryTrackEntity>, startIndex: Int) {
        if (queue.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(0, queue.lastIndex)
        controller?.run {
            setMediaItems(queue.map { it.toMediaItem() }, safeStartIndex, 0L)
            prepare()
            play()
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
                                kind = app.echo.android.playback.EchoAudioErrorKind.Unknown,
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
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val track = item?.let {
            EchoTrackRef(
                id = it.mediaId,
                uri = Uri.parse(it.localConfiguration?.uri?.toString().orEmpty()),
                title = metadata?.title?.toString().orEmpty().ifBlank { "未知曲目" },
                artist = metadata?.artist?.toString().orEmpty().ifBlank { "未知艺术家" },
                album = metadata?.albumTitle?.toString(),
                artworkUri = metadata?.artworkUri,
                durationMs = player.duration.takeIf { duration -> duration > 0L } ?: 0L,
            )
        }
        val duration = player.duration.takeIf { it > 0L } ?: track?.durationMs ?: 0L
        _playbackStatus.value = EchoPlaybackStatus(
            state = player.toEchoState(),
            track = track,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            isPlaying = player.isPlaying,
            repeatMode = player.repeatMode.toEchoRepeatMode(),
            shuffleEnabled = player.shuffleModeEnabled,
            diagnostics = EchoPlaybackDiagnostics(
                outputRoute = "Media3 / AudioTrack",
                bufferedMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L),
                requestToken = item?.mediaId?.hashCode()?.toLong() ?: 0L,
                lastCommand = if (player.isPlaying) "play" else "idle",
            ),
        )
    }

    private fun Player.toEchoState(): EchoPlaybackState = when {
        playbackState == Player.STATE_IDLE && currentMediaItem == null -> EchoPlaybackState.Idle
        playbackState == Player.STATE_IDLE -> EchoPlaybackState.Stopped
        playbackState == Player.STATE_BUFFERING -> EchoPlaybackState.Buffering
        playbackState == Player.STATE_ENDED -> EchoPlaybackState.Ended
        isPlaying -> EchoPlaybackState.Playing
        playWhenReady -> EchoPlaybackState.Loading
        else -> EchoPlaybackState.Paused
    }

    private fun Int.toEchoRepeatMode(): EchoRepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> EchoRepeatMode.All
        Player.REPEAT_MODE_ONE -> EchoRepeatMode.One
        else -> EchoRepeatMode.Off
    }

    private fun LibraryTrackEntity.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setAlbumArtist(albumArtist)
                    .setArtworkUri(artworkUri?.let(Uri::parse))
                    .build(),
            )
            .build()
}

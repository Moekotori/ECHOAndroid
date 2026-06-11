package app.echo.android

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackError
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackControlsState
import app.echo.android.model.playback.PlaybackDiagnosticsState
import app.echo.android.model.playback.PlaybackMetadataState
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.playback.PlaybackQueueState
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.playback.EchoEqualizerController
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.EchoPlaybackRuntimeOptionsStore
import app.echo.android.playback.EchoUsbExclusiveDriverTester
import app.echo.android.playback.EchoUsbAudioMonitor
import app.echo.android.playback.toEchoPlaybackStatus
import app.echo.android.playback.toMediaItem
import app.echo.android.playback.toPlaybackControlsState
import app.echo.android.playback.toPlaybackDiagnosticsState
import app.echo.android.playback.toPlaybackMetadataState
import app.echo.android.playback.toPlaybackPositionState
import app.echo.android.playback.toPlaybackQueueState
import app.echo.android.playback.toEchoPlaybackError
import app.echo.android.playback.withUsbAudioStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

internal enum class PlaybackProgressUiVisibility {
    NowPlayingExpanded,
    MiniPlayer,
    Background,
}

@UnstableApi
internal class PlaybackController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val onTrackChanged: (String?) -> Unit,
    private val onTrackActivated: (String) -> Unit,
) {
    private val _playbackStatus = MutableStateFlow(EchoPlaybackStatus())
    val playbackStatus: StateFlow<EchoPlaybackStatus> = _playbackStatus.asStateFlow()

    private val _playbackMetadata = MutableStateFlow(PlaybackMetadataState())
    val playbackMetadata: StateFlow<PlaybackMetadataState> = _playbackMetadata.asStateFlow()

    private val _playbackPosition = MutableStateFlow(PlaybackPositionState())
    val playbackPosition: StateFlow<PlaybackPositionState> = _playbackPosition.asStateFlow()

    private val _playbackControls = MutableStateFlow(PlaybackControlsState())
    val playbackControls: StateFlow<PlaybackControlsState> = _playbackControls.asStateFlow()

    private val _playbackQueue = MutableStateFlow(PlaybackQueueState())
    val playbackQueue: StateFlow<PlaybackQueueState> = _playbackQueue.asStateFlow()

    private val _playbackDiagnostics = MutableStateFlow(PlaybackDiagnosticsState())
    val playbackDiagnostics: StateFlow<PlaybackDiagnosticsState> = _playbackDiagnostics.asStateFlow()

    private val equalizerController = EchoEqualizerController(scope)
    val equalizerState: StateFlow<EchoEqualizerState> = equalizerController.state

    private val usbAudioMonitor = EchoUsbAudioMonitor(application)
    private val usbExclusiveDriverTester = EchoUsbExclusiveDriverTester(application)
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var progressUpdateIntervalMs: Long? = MiniPlayerProgressIntervalMs
    private var usbAudioJob: Job? = null
    private var usbTransitionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var replayGainJob: Job? = null
    private var lastTrackId: String? = null
    private var lastActivatedTrackId: String? = null
    private var activeReplayGainTrackId: String? = null
    private var activeReplayGainTrackGainDb: Float? = null
    private var sleepTimerEndTimeEpochMs: Long? = null
    private var replayGainEnabled: Boolean = false
    private var replayGainPreampDb: Float = 0f
    private var skipSilenceEnabled: Boolean = false
    private val sampleRatesByMediaId = mutableMapOf<String, Int?>()
    private val replayGainUrisByMediaId = mutableMapOf<String, String>()
    private val replayGainTrackGainsByMediaId = mutableMapOf<String, Float?>()

    val currentTrackId: String?
        get() = _playbackMetadata.value.track?.id

    init {
        usbAudioMonitor.start()
        startUsbAudioUpdates()
        connectController()
    }

    fun setUsbExclusiveEnabled(enabled: Boolean) {
        usbAudioMonitor.setExclusiveEnabled(enabled)
        if (enabled) {
            usbAudioMonitor.prepareForTrack(_playbackDiagnostics.value.diagnostics.sampleRateHz)
        }
    }

    fun setEqualizerConfig(enabled: Boolean, presetId: String, gainsDb: List<Float>) {
        equalizerController.setConfig(enabled, presetId, gainsDb)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerController.setEnabled(enabled)
    }

    fun setEqualizerPreset(presetId: String) {
        equalizerController.setPreset(presetId)
    }

    fun setEqualizerBandGain(index: Int, gainDb: Float) {
        equalizerController.setBandGain(index, gainDb)
    }

    fun resetEqualizer() {
        equalizerController.reset()
    }

    fun applyOpraPreset(preset: OpraHeadphoneCorrectionPreset): List<Float> =
        equalizerController.applyOpraPreset(preset)

    fun testUsbExclusiveDriver(): String =
        usbExclusiveDriverTester.testOpen(_playbackDiagnostics.value.diagnostics)

    fun play(track: EchoTrack) {
        sampleRatesByMediaId.clear()
        replayGainUrisByMediaId.clear()
        replayGainTrackGainsByMediaId.clear()
        sampleRatesByMediaId[track.id] = track.sampleRateHz
        replayGainUrisByMediaId[track.id] = track.uri
        usbAudioMonitor.prepareForTrack(track.sampleRateHz)
        controller?.run {
            setMediaItem(track.toMediaItem())
            prepare()
            play()
        }
    }

    fun playQueue(queue: List<EchoTrack>, startIndex: Int) {
        if (queue.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(0, queue.lastIndex)
        sampleRatesByMediaId.clear()
        replayGainUrisByMediaId.clear()
        replayGainTrackGainsByMediaId.clear()
        queue.forEach { track -> sampleRatesByMediaId[track.id] = track.sampleRateHz }
        queue.forEach { track -> replayGainUrisByMediaId[track.id] = track.uri }
        usbAudioMonitor.prepareForTrack(queue[safeStartIndex].sampleRateHz)
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
        controller?.let(::updatePlaybackPosition)
    }

    fun setProgressUpdatePolicy(
        effectivePerformanceMode: EchoEffectivePerformanceMode,
        uiVisibility: PlaybackProgressUiVisibility,
    ) {
        val nextInterval = resolveProgressUpdateIntervalMs(effectivePerformanceMode, uiVisibility)
        if (progressUpdateIntervalMs == nextInterval) return
        progressUpdateIntervalMs = nextInterval
        controller?.let(::updatePlaybackPosition)
        startProgressUpdates()
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun playQueueItem(index: Int) {
        controller?.run {
            if (index !in 0 until mediaItemCount) return
            seekTo(index, 0L)
            prepare()
            play()
            updatePlaybackCore(this)
        }
    }

    fun removeQueueItem(index: Int) {
        controller?.run {
            if (index !in 0 until mediaItemCount) return
            removeMediaItem(index)
            updatePlaybackCore(this)
        }
    }

    fun clearQueue() {
        controller?.run {
            if (mediaItemCount <= 0) return
            removeMediaItems(0, mediaItemCount)
            updatePlaybackCore(this)
        }
    }

    fun cycleRepeatMode() {
        controller?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            updatePlaybackCore(this)
        }
    }

    fun toggleShuffle() {
        controller?.run {
            shuffleModeEnabled = !shuffleModeEnabled
            updatePlaybackCore(this)
        }
    }

    fun setPlaybackSpeed(speed: Float, nightcore: Boolean) {
        val safeSpeed = speed.coerceIn(MinPlaybackSpeed, MaxPlaybackSpeed)
        val pitch = if (nightcore) safeSpeed else 1f
        controller?.run {
            setPlaybackParameters(PlaybackParameters(safeSpeed, pitch))
            updatePlaybackCore(this)
        }
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerEndTimeEpochMs = System.currentTimeMillis() + minutes.coerceAtMost(MaxSleepTimerMinutes) * 60_000L
        updatePlaybackStatusOptions()
        startSleepTimerUpdates()
    }

    fun cancelSleepTimer() {
        sleepTimerEndTimeEpochMs = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        updatePlaybackStatusOptions()
    }

    fun setReplayGain(enabled: Boolean, preampDb: Float = replayGainPreampDb) {
        replayGainEnabled = enabled
        replayGainPreampDb = preampDb.coerceIn(MinReplayGainPreampDb, MaxReplayGainPreampDb)
        if (enabled) {
            loadReplayGainForTrack(activeReplayGainTrackId ?: currentTrackId)
        }
        applyReplayGainVolume()
        updatePlaybackStatusOptions()
    }

    fun adjustReplayGainPreamp(deltaDb: Float) {
        setReplayGain(enabled = true, preampDb = replayGainPreampDb + deltaDb)
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        skipSilenceEnabled = enabled
        EchoPlaybackRuntimeOptionsStore.setSkipSilenceEnabled(enabled)
        updatePlaybackStatusOptions()
    }

    fun cyclePlayMode() {
        controller?.run {
            shuffleModeEnabled = false
            repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) {
                Player.REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ONE
            }
            updatePlaybackCore(this)
        }
    }

    fun enableShuffle() {
        controller?.run {
            shuffleModeEnabled = true
            updatePlaybackCore(this)
        }
    }

    fun clear() {
        progressJob?.cancel()
        usbAudioJob?.cancel()
        usbTransitionJob?.cancel()
        sleepTimerJob?.cancel()
        replayGainJob?.cancel()
        usbAudioMonitor.setExclusiveEnabled(false)
        EchoPlaybackRuntimeOptionsStore.setSkipSilenceEnabled(false)
        usbAudioMonitor.stop()
        equalizerController.release()
        controller?.removeListener(playerListener)
        controller?.release()
    }

    private fun connectController() {
        val token = SessionToken(application, ComponentName(application, EchoPlaybackService::class.java))
        val future = MediaController.Builder(application, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    future.get()
                }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    applyReplayGainVolume()
                    updatePlaybackCore(mediaController)
                    startProgressUpdates()
                }.onFailure { error ->
                    val diagnostics = EchoPlaybackDiagnostics(
                        lastError = EchoPlaybackError(
                            kind = EchoAudioErrorKind.Unknown,
                            message = "Media controller connection failed.",
                            recoverable = true,
                        ),
                    )
                    updateState(_playbackDiagnostics, PlaybackDiagnosticsState(diagnostics, diagnostics.lastError))
                    updateState(
                        _playbackControls,
                        PlaybackControlsState(state = EchoPlaybackState.Error),
                    )
                    updateState(
                        _playbackStatus,
                        EchoPlaybackStatus(
                            state = EchoPlaybackState.Error,
                            diagnostics = diagnostics,
                        ).withPlaybackOptions(),
                    )
                }
            },
            ContextCompat.getMainExecutor(application),
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            prepareUsbForMediaItemTransition(mediaItem)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            updatePlaybackCore(player)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            updatePlaybackCore(controller ?: return, error.toEchoPlaybackError())
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        val intervalMs = progressUpdateIntervalMs ?: return
        progressJob = scope.launch {
            while (true) {
                controller?.let(::updatePlaybackPosition)
                delay(intervalMs)
            }
        }
    }

    private fun startUsbAudioUpdates() {
        usbAudioJob?.cancel()
        usbAudioJob = scope.launch {
            usbAudioMonitor.status.collect { status ->
                controller?.let(::updatePlaybackCore) ?: updateUsbDiagnostics(status)
            }
        }
    }

    private fun updatePlaybackCore(player: Player, playbackError: EchoPlaybackError? = null) {
        equalizerController.syncToAudioSession(player.audioSessionId)
        val metadata = player.toPlaybackMetadataState()
        val activePlaybackError = playbackError ?: player.playerError?.toEchoPlaybackError()
        val controls = player.toPlaybackControlsState().let { state ->
            if (activePlaybackError == null) {
                state
            } else {
                state.copy(
                    state = EchoPlaybackState.Error,
                    isPlaying = false,
                )
            }
        }
        val sourceSampleRateHz = metadata.mediaId?.let(sampleRatesByMediaId::get)
        val diagnostics = player.toPlaybackDiagnosticsState(
            usbAudioStatus = usbAudioMonitor.status.value,
            sourceSampleRateHz = sourceSampleRateHz,
        ).withPlaybackError(activePlaybackError)
        val position = player.toPlaybackPositionState()
        val queue = player.toPlaybackQueueState()
        updateActiveReplayGainTrack(metadata.mediaId)
        val status = player.toEchoPlaybackStatus(diagnostics.diagnostics).let { state ->
            if (activePlaybackError == null) {
                state
            } else {
                state.copy(
                    state = EchoPlaybackState.Error,
                    isPlaying = false,
                    diagnostics = diagnostics.diagnostics,
                )
            }
        }

        updateState(_playbackMetadata, metadata)
        updateState(_playbackControls, controls)
        updateState(_playbackDiagnostics, diagnostics)
        updateState(_playbackPosition, position)
        updateState(_playbackQueue, queue)
        updateState(_playbackStatus, status.withPlaybackOptions())

        val trackId = metadata.track?.id
        if (trackId != lastTrackId) {
            lastTrackId = trackId
            loadReplayGainForTrack(trackId)
            onTrackChanged(trackId)
        }
        if (trackId != null && trackId != lastActivatedTrackId && (controls.isPlaying || position.positionMs > 0L)) {
            lastActivatedTrackId = trackId
            onTrackActivated(trackId)
        }
    }

    private fun updateUsbDiagnostics(status: app.echo.android.playback.EchoUsbAudioStatus) {
        val diagnostics = _playbackDiagnostics.value.diagnostics.withUsbAudioStatus(status)
        updateState(_playbackDiagnostics, PlaybackDiagnosticsState(diagnostics, diagnostics.lastError))
        updateState(_playbackStatus, _playbackStatus.value.copy(diagnostics = diagnostics).withPlaybackOptions())
    }

    private fun prepareUsbForMediaItemTransition(mediaItem: MediaItem?) {
        val sampleRateHz = mediaItem?.mediaId?.let(sampleRatesByMediaId::get)
        if (!usbAudioMonitor.status.value.exclusiveEnabled) {
            usbAudioMonitor.prepareForTrack(sampleRateHz)
            return
        }
        val mediaController = controller ?: return
        usbTransitionJob?.cancel()
        usbTransitionJob = scope.launch {
            val previousVolume = mediaController.volume
            mediaController.volume = 0f
            usbAudioMonitor.prepareForTrack(sampleRateHz)
            delay(90L)
            if (controller === mediaController) {
                mediaController.volume = previousVolume
            }
        }
    }

    private fun updatePlaybackPosition(player: Player) {
        updateState(_playbackPosition, player.toPlaybackPositionState())
    }

    private fun startSleepTimerUpdates() {
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            while (true) {
                val remainingMs = sleepTimerRemainingMs()
                if (remainingMs <= 0L) {
                    sleepTimerEndTimeEpochMs = null
                    controller?.pause()
                    controller?.let(::updatePlaybackCore) ?: updatePlaybackStatusOptions()
                    return@launch
                }
                updatePlaybackStatusOptions()
                delay(SleepTimerTickMs)
            }
        }
    }

    private fun updatePlaybackStatusOptions() {
        updateState(_playbackStatus, _playbackStatus.value.withPlaybackOptions())
    }

    private fun EchoPlaybackStatus.withPlaybackOptions(): EchoPlaybackStatus =
        copy(
            sleepTimerRemainingMs = sleepTimerRemainingMs(),
            replayGainEnabled = replayGainEnabled,
            replayGainPreampDb = replayGainPreampDb,
            replayGainTrackGainDb = activeReplayGainTrackGainDb,
            skipSilenceEnabled = skipSilenceEnabled,
        )

    private fun sleepTimerRemainingMs(): Long {
        val endTime = sleepTimerEndTimeEpochMs ?: return 0L
        return (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun applyReplayGainVolume() {
        controller?.volume = replayGainVolume()
    }

    private fun replayGainVolume(): Float =
        if (replayGainEnabled) {
            val gainDb = replayGainPreampDb + (activeReplayGainTrackGainDb ?: 0f)
            10.0.pow(gainDb / 20.0).toFloat().coerceIn(MinReplayGainVolume, MaxReplayGainVolume)
        } else {
            1f
        }

    private fun updateActiveReplayGainTrack(trackId: String?) {
        if (activeReplayGainTrackId == trackId) return
        activeReplayGainTrackId = trackId
        activeReplayGainTrackGainDb = trackId?.let(replayGainTrackGainsByMediaId::get)
        applyReplayGainVolume()
    }

    private fun loadReplayGainForTrack(trackId: String?) {
        if (trackId == null || replayGainTrackGainsByMediaId.containsKey(trackId)) return
        val uri = replayGainUrisByMediaId[trackId] ?: return
        replayGainJob?.cancel()
        replayGainJob = scope.launch {
            val gainDb = withContext(Dispatchers.IO) {
                runCatching {
                    application.contentResolver.openInputStream(Uri.parse(uri))?.use(ReplayGainReader::readTrackGainDb)
                }.getOrNull()
            }
            replayGainTrackGainsByMediaId[trackId] = gainDb
            if (activeReplayGainTrackId == trackId) {
                activeReplayGainTrackGainDb = gainDb
                applyReplayGainVolume()
                updatePlaybackStatusOptions()
            }
        }
    }

    private fun PlaybackDiagnosticsState.withPlaybackError(error: EchoPlaybackError?): PlaybackDiagnosticsState {
        if (error == null) return this
        val diagnosticsWithError = diagnostics.copy(
            lastCommand = "error",
            lastError = error,
        )
        return copy(
            diagnostics = diagnosticsWithError,
            lastError = error,
        )
    }

    private fun <T> updateState(flow: MutableStateFlow<T>, value: T) {
        if (flow.value != value) {
            flow.value = value
        }
    }

    private fun resolveProgressUpdateIntervalMs(
        effectivePerformanceMode: EchoEffectivePerformanceMode,
        uiVisibility: PlaybackProgressUiVisibility,
    ): Long? =
        when (uiVisibility) {
            PlaybackProgressUiVisibility.Background -> null
            PlaybackProgressUiVisibility.NowPlayingExpanded -> NowPlayingProgressIntervalMs
            PlaybackProgressUiVisibility.MiniPlayer -> {
                if (effectivePerformanceMode.isLightweight) {
                    LightweightProgressIntervalMs
                } else {
                    MiniPlayerProgressIntervalMs
                }
            }
        }

    private companion object {
        const val NowPlayingProgressIntervalMs = 500L
        const val MiniPlayerProgressIntervalMs = 1_000L
        const val LightweightProgressIntervalMs = 1_000L
        const val MinPlaybackSpeed = 0.5f
        const val MaxPlaybackSpeed = 2.0f
        const val SleepTimerTickMs = 1_000L
        const val MaxSleepTimerMinutes = 180
        const val MinReplayGainPreampDb = -12f
        const val MaxReplayGainPreampDb = 6f
        const val MinReplayGainVolume = 0.25f
        const val MaxReplayGainVolume = 1.4f
    }
}

package app.echo.android

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.echo.android.data.EchoSavedPlaybackSession
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackError
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import app.echo.android.model.playback.PlaybackControlsState
import app.echo.android.model.playback.PlaybackDiagnosticsState
import app.echo.android.model.playback.PlaybackMetadataState
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.playback.PlaybackQueueState
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoPlaybackRuntimeOptionsStore
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.EchoUsbAudioMonitor
import app.echo.android.playback.EchoUsbAudioStatus
import app.echo.android.playback.EchoUsbExclusiveDriverTester
import app.echo.android.playback.echoReplayGainOutput
import app.echo.android.playback.PlaybackSessionPolicy
import app.echo.android.playback.nextIndexAfterPlaybackError
import app.echo.android.playback.pendingPlayPauseShouldPlay
import app.echo.android.playback.shouldAbandonSavedSessionRestoreForPendingQueueReplace
import app.echo.android.playback.shouldAutoSkipTrack
import app.echo.android.playback.shouldMarkSavedSessionRestoreComplete
import app.echo.android.playback.shouldQueueControllerActionUntilSessionReady
import app.echo.android.playback.shouldRestoreSavedSessionBeforeFlushingPending
import app.echo.android.playback.toEchoPlaybackError
import app.echo.android.playback.toEchoPlaybackStatus
import app.echo.android.playback.toEchoRepeatMode
import app.echo.android.playback.toEchoTrackRef
import app.echo.android.playback.toMediaItem
import app.echo.android.playback.toPlaybackControlsState
import app.echo.android.playback.toPlaybackDiagnosticsState
import app.echo.android.playback.toPlaybackMetadataState
import app.echo.android.playback.playbackSessionPersistSignature
import app.echo.android.playback.toPlaybackPositionState
import app.echo.android.playback.toPlaybackQueueState
import app.echo.android.playback.withUsbAudioStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

internal enum class PlaybackProgressUiVisibility {
    NowPlayingExpanded,
    MiniPlayer,
    Background,
}

private data class PendingControllerAction(
    val replacesQueue: Boolean,
    val action: MediaController.() -> Unit,
)

@UnstableApi
@Suppress("SpellCheckingInspection")
internal class PlaybackController(
    private val application: Application,
    private val settingsStore: EchoSettingsStore,
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

    private val equalizerController = EchoPlaybackProcessRuntime.equalizerController()
    val equalizerState: StateFlow<EchoEqualizerState> = equalizerController.state

    private val usbAudioMonitor = EchoUsbAudioMonitor(application)
    private val usbExclusiveDriverTester = EchoUsbExclusiveDriverTester(application)
    private var controller: MediaController? = null
    private var controllerConnectionCancel: (() -> Unit)? = null
    private var controllerSetupJob: Job? = null
    private var controllerRetryJob: Job? = null
    private var controllerGeneration = 0L
    private var cleared = false
    private var progressJob: Job? = null
    private var progressUpdateIntervalMs: Long? = MINI_PLAYER_PROGRESS_INTERVAL_MS
    private var usbAudioJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var usbTransitionJob: Job? = null
    private val pendingControllerActions = ArrayDeque<PendingControllerAction>()
    private var replayGainJob: Job? = null
    private var lastTrackId: String? = null
    private var lastActivatedTrackId: String? = null
    private var activeReplayGainTrackId: String? = null
    private var activeReplayGainTrackGainDb: Float? = null
    private var replayGainEnabled: Boolean = EchoPlaybackProcessRuntime.replayGainEnabled
    private var replayGainPreampDb: Float = EchoPlaybackProcessRuntime.replayGainPreampDb
    private var skipSilenceEnabled: Boolean = EchoPlaybackRuntimeOptionsStore.options.value.skipSilenceEnabled
    private var restoredPlaybackSession = false
    private var restoreCompleted = false
    private var sessionReadyForCommands = false
    private var consecutiveErrorSkips = 0
    private var stickyPlaybackError: EchoPlaybackError? = null
    private var stickyErrorMediaId: String? = null
    private var stickyErrorAutoSkipped = false
    private var lastPersistedSessionSignature: String? = null
    private var lastPersistedPositionBucket: Long = -1L
    private var pendingPersistSessionSignature: String? = null
    private var pendingPersistPositionBucket: Long? = null
    private var persistenceGeneration = 0L
    private var persistenceJob: Job? = null
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

    fun isUsbExclusiveEnabled(): Boolean = usbAudioMonitor.status.value.exclusiveEnabled

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

    fun testUsbExclusiveDriver(): String {
        val playingToUsb = _playbackControls.value.isPlaying &&
            usbAudioMonitor.status.value.exclusiveEnabled
        if (!PlaybackSessionPolicy.shouldClaimUsbInterfaceForDriverTest(playingToUsb)) {
            return "Skipped: stop playback before claiming the USB interface"
        }
        return usbExclusiveDriverTester.testOpen(_playbackDiagnostics.value.diagnostics)
    }

    fun play(track: EchoTrack) {
        resetStickyPlaybackError()
        usbAudioMonitor.prepareForTrack(track.sampleRateHz)
        withController(replacesQueue = true) {
            replaceQueueLookups(listOf(track))
            setMediaItem(track.toMediaItem())
            prepare()
            play()
        }
    }

    fun playQueue(queue: List<EchoTrack>, startIndex: Int) {
        if (queue.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(0, queue.lastIndex)
        resetStickyPlaybackError()
        val mediaItems = ArrayList<MediaItem>(queue.size)
        queue.forEach { track ->
            mediaItems += track.toMediaItem()
        }
        usbAudioMonitor.prepareForTrack(queue[safeStartIndex].sampleRateHz)
        withController(replacesQueue = true) {
            replaceQueueLookups(queue)
            setMediaItems(mediaItems, safeStartIndex, 0L)
            prepare()
            play()
        }
    }

    fun playPause() {
        val currentlyPlaying = controller
            ?.takeIf { !shouldQueueControllerActionUntilSessionReady(sessionReadyForCommands) }
            ?.isPlaying
            ?: _playbackControls.value.isPlaying
        val shouldPlay = pendingPlayPauseShouldPlay(currentlyPlaying)
        withController {
            if (shouldPlay) {
                recoverAndPlay()
            } else {
                pause()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        withController {
            val recover = PlaybackSessionPolicy.shouldPrepareBeforePlay(
                hasPlayerError = playerError != null,
                playbackStateIdle = playbackState == Player.STATE_IDLE,
            )
            if (recover) {
                prepare()
            }
            seekTo(positionMs)
            if (recover) {
                play()
            }
            updatePlaybackPosition(this)
        }
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
        resetStickyPlaybackError()
        withController {
            seekToNextMediaItem()
            recoverAndPlay()
        }
    }

    fun skipPrevious() {
        resetStickyPlaybackError()
        withController {
            seekToPrevious()
            if (
                PlaybackSessionPolicy.shouldPrepareBeforePlay(
                    hasPlayerError = playerError != null,
                    playbackStateIdle = playbackState == Player.STATE_IDLE,
                )
            ) {
                recoverAndPlay()
            }
        }
    }

    fun playQueueItem(index: Int) {
        resetStickyPlaybackError()
        withController {
            if (index !in 0 until mediaItemCount) return@withController
            seekTo(index, 0L)
            recoverAndPlay()
            updatePlaybackCore(this)
        }
    }

    fun removeQueueItem(index: Int) {
        withController {
            if (index !in 0 until mediaItemCount) return@withController
            removeMediaItem(index)
            updatePlaybackCore(this)
        }
    }

    fun clearQueue() {
        withController {
            if (mediaItemCount <= 0) return@withController
            removeMediaItems(0, mediaItemCount)
            updatePlaybackCore(this)
        }
    }

    fun cycleRepeatMode() {
        withController {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            updatePlaybackCore(this, remapQueue = false)
        }
    }

    fun toggleShuffle() {
        withController {
            shuffleModeEnabled = !shuffleModeEnabled
            updatePlaybackCore(this, remapQueue = false)
        }
    }

    fun setPlaybackSpeed(speed: Float, nightcore: Boolean) {
        val safeSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        val pitch = if (nightcore) safeSpeed else 1f
        withController {
            setPlaybackParameters(PlaybackParameters(safeSpeed, pitch))
            updatePlaybackCore(this, remapQueue = false)
        }
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        EchoPlaybackProcessRuntime.setSleepTimerMinutes(minutes, MAX_SLEEP_TIMER_MINUTES)
        updatePlaybackStatusOptions()
        startSleepTimerUiUpdates()
    }

    fun cancelSleepTimer() {
        EchoPlaybackProcessRuntime.cancelSleepTimer()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        updatePlaybackStatusOptions()
    }

    fun setReplayGain(enabled: Boolean, preampDb: Float = replayGainPreampDb) {
        replayGainEnabled = enabled
        replayGainPreampDb = preampDb.coerceIn(MIN_REPLAY_GAIN_PREAMP_DB, MAX_REPLAY_GAIN_PREAMP_DB)
        EchoPlaybackProcessRuntime.setReplayGain(replayGainEnabled, replayGainPreampDb)
        if (enabled) {
            loadReplayGainForTrack(activeReplayGainTrackId ?: currentTrackId)
        }
        applyReplayGain()
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
        withController {
            shuffleModeEnabled = false
            repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) {
                Player.REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ONE
            }
            updatePlaybackCore(this, remapQueue = false)
        }
    }

    fun enableShuffle() {
        withController {
            shuffleModeEnabled = true
            updatePlaybackCore(this, remapQueue = false)
        }
    }

    fun clear() {
        if (cleared) return
        persistPlaybackSession(force = true)
        cleared = true
        controllerGeneration += 1
        controllerConnectionCancel?.invoke()
        controllerConnectionCancel = null
        controllerSetupJob?.cancel()
        controllerRetryJob?.cancel()
        pendingControllerActions.clear()
        sessionReadyForCommands = false
        progressJob?.cancel()
        usbAudioJob?.cancel()
        sleepTimerJob?.cancel()
        usbTransitionJob?.cancel()
        replayGainJob?.cancel()
        controller?.let { mediaController ->
            mediaController.removeListener(playerListener)
            startDetachedSessionPersistence(mediaController)
        }
        controller = null
        usbAudioMonitor.stop()
    }

    private fun connectController(attempt: Int = 0) {
        if (cleared) return
        val generation = ++controllerGeneration
        val token = SessionToken(application, ComponentName(application, EchoPlaybackService::class.java))
        val future = MediaController.Builder(application, token).buildAsync()
        controllerConnectionCancel = { future.cancel(true) }
        future.addListener(
            {
                if (generation == controllerGeneration) {
                    controllerConnectionCancel = null
                }
                runCatching {
                    future.get()
                }.onSuccess success@{ mediaController ->
                    if (cleared || generation != controllerGeneration) {
                        runCatching { mediaController.release() }
                        return@success
                    }
                    sessionReadyForCommands = false
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    EchoPlaybackProcessRuntime.bindPlayer(mediaController)
                    controllerSetupJob?.cancel()
                    controllerSetupJob = scope.launch {
                        if (cleared || controller !== mediaController) return@launch
                        val pendingReplacesQueue = pendingControllerActions.map { it.replacesQueue }
                        if (shouldRestoreSavedSessionBeforeFlushingPending(pendingReplacesQueue)) {
                            restorePlaybackSessionIfNeeded(mediaController)
                        } else {
                            restoredPlaybackSession = true
                        }
                        if (cleared || controller !== mediaController) return@launch
                        flushPendingControllerActions(mediaController)
                        sessionReadyForCommands = true
                        flushPendingControllerActions(mediaController)
                        if (restoredPlaybackSession) {
                            restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
                        }
                        applyReplayGain()
                        updatePlaybackCore(mediaController)
                        startProgressUpdates()
                        if (EchoPlaybackProcessRuntime.sleepTimerEndTimeEpochMs != null) {
                            startSleepTimerUiUpdates()
                        }
                    }
                }.onFailure failure@{ _ ->
                    if (cleared || generation != controllerGeneration) return@failure
                    if (attempt < CONTROLLER_CONNECT_MAX_RETRIES) {
                        controllerRetryJob?.cancel()
                        controllerRetryJob = scope.launch {
                            delay(CONTROLLER_CONNECT_RETRY_MS * (attempt + 1))
                            if (!cleared && generation == controllerGeneration) {
                                connectController(attempt + 1)
                            }
                        }
                        return@failure
                    }
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
            if (events.containsAny(*PlaybackCoreEvents)) {
                updatePlaybackCore(
                    player = player,
                    remapQueue = PlaybackSessionPolicy.shouldRemapFullQueue(
                        timelineChanged = events.contains(Player.EVENT_TIMELINE_CHANGED),
                        mediaItemTransitioned = events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION),
                        isPlayingChanged = events.contains(Player.EVENT_IS_PLAYING_CHANGED),
                        tracksChanged = events.contains(Player.EVENT_TRACKS_CHANGED),
                        playWhenReadyChanged = events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED),
                    ),
                )
            } else if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                updatePlaybackPosition(player)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val player = controller ?: return
            val mapped = error.toEchoPlaybackError()
            stickyPlaybackError = mapped
            stickyErrorMediaId = player.currentMediaItem?.mediaId
            stickyErrorAutoSkipped = false
            updatePlaybackCore(player, mapped)
            if (mapped.shouldAutoSkipTrack()) {
                skipToNextAfterError(player)
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        val intervalMs = progressUpdateIntervalMs ?: return
        val mediaController = controller ?: return
        var elapsedSincePersistMs = 0L
        progressJob = scope.launch {
            while (isActive && !cleared && controller === mediaController) {
                updatePlaybackPosition(mediaController)
                elapsedSincePersistMs += intervalMs
                if (elapsedSincePersistMs >= PERSIST_POSITION_BUCKET_MS) {
                    elapsedSincePersistMs = 0L
                    persistPlaybackSession()
                }
                delay(intervalMs.milliseconds)
            }
        }
    }

    private fun startUsbAudioUpdates() {
        usbAudioJob?.cancel()
        usbAudioJob = scope.launch {
            usbAudioMonitor.status.collect { status ->
                updateUsbDiagnostics(status)
            }
        }
    }

    private fun updatePlaybackCore(
        player: Player,
        playbackError: EchoPlaybackError? = null,
        remapQueue: Boolean = true,
    ) {
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
        if (playbackError != null) {
            stickyPlaybackError = playbackError
            stickyErrorMediaId = metadata.mediaId ?: stickyErrorMediaId
        } else if (
            controls.isPlaying &&
            metadata.mediaId != null &&
            metadata.mediaId != stickyErrorMediaId
        ) {
            resetStickyPlaybackError()
        }
        val displayedError = activePlaybackError ?: stickyPlaybackError
        val sourceSampleRateHz = metadata.mediaId?.let(sampleRatesByMediaId::get)
        val diagnostics = player.toPlaybackDiagnosticsState(
            usbAudioStatus = usbAudioMonitor.status.value,
            sourceSampleRateHz = sourceSampleRateHz,
        ).withPlaybackError(displayedError).let { state ->
            if (displayedError != null && stickyErrorAutoSkipped && activePlaybackError == null) {
                state.copy(
                    diagnostics = state.diagnostics.copy(lastCommand = PlaybackErrorSkipCommand),
                )
            } else {
                state
            }
        }
        val position = player.toPlaybackPositionState()
        if (remapQueue) {
            updateState(_playbackQueue, player.toPlaybackQueueState())
        } else {
            val currentQueue = _playbackQueue.value
            val newIndex = player.currentMediaItemIndex.takeIf { it in 0 until player.mediaItemCount } ?: -1
            if (currentQueue.currentIndex != newIndex) {
                updateState(_playbackQueue, currentQueue.copy(currentIndex = newIndex))
            }
        }
        updateActiveReplayGainTrack(metadata.mediaId)
        val status = player.toEchoPlaybackStatus(diagnostics.diagnostics).let { state ->
            if (activePlaybackError == null) {
                state.copy(diagnostics = diagnostics.diagnostics)
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
        persistPlaybackSession(force = player.mediaItemCount <= 0)
    }

    private fun updateUsbDiagnostics(status: EchoUsbAudioStatus) {
        val diagnostics = _playbackDiagnostics.value.diagnostics.withUsbAudioStatus(status)
        updateState(_playbackDiagnostics, PlaybackDiagnosticsState(diagnostics, diagnostics.lastError))
        updateState(_playbackStatus, _playbackStatus.value.copy(diagnostics = diagnostics).withPlaybackOptions())
    }

    private fun prepareUsbForMediaItemTransition(mediaItem: MediaItem?) {
        val sampleRateHz = mediaItem?.mediaId?.let(sampleRatesByMediaId::get)
            ?: _playbackDiagnostics.value.diagnostics.sampleRateHz
            ?: _playbackDiagnostics.value.diagnostics.decodedSampleRateHz
        if (!usbAudioMonitor.status.value.exclusiveEnabled) {
            usbAudioMonitor.prepareForTrack(sampleRateHz)
            return
        }
        val mediaController = controller ?: return
        usbTransitionJob?.cancel()
        usbTransitionJob = EchoPlaybackProcessRuntime.scope.launch {
            val fallbackVolume = echoReplayGainOutput(
                enabled = replayGainEnabled,
                preampDb = replayGainPreampDb,
                trackGainDb = activeReplayGainTrackGainDb,
            ).playerVolume
            val captured = PlaybackSessionPolicy.restoredVolumeAfterUsbMute(
                capturedVolume = mediaController.volume,
                fallbackVolume = fallbackVolume,
            )
            mediaController.volume = 0f
            usbAudioMonitor.prepareForTrack(sampleRateHz)
            delay(90.milliseconds)
            if (controller === mediaController) {
                val restored = PlaybackSessionPolicy.restoredVolumeAfterUsbMute(
                    capturedVolume = captured,
                    fallbackVolume = echoReplayGainOutput(
                        enabled = replayGainEnabled,
                        preampDb = replayGainPreampDb,
                        trackGainDb = activeReplayGainTrackGainDb,
                    ).playerVolume,
                )
                mediaController.volume = restored
                applyReplayGain()
            }
        }
    }

    private fun withController(
        replacesQueue: Boolean = false,
        action: MediaController.() -> Unit,
    ) {
        val mediaController = controller
        if (
            mediaController != null &&
            !shouldQueueControllerActionUntilSessionReady(sessionReadyForCommands)
        ) {
            mediaController.action()
        } else {
            pendingControllerActions.add(PendingControllerAction(replacesQueue, action))
        }
    }

    private fun flushPendingControllerActions(mediaController: MediaController) {
        while (pendingControllerActions.isNotEmpty()) {
            val pending = pendingControllerActions.removeFirst()
            mediaController.run(pending.action)
        }
    }

    private fun updatePlaybackPosition(player: Player) {
        updateState(_playbackPosition, player.toPlaybackPositionState())
    }

    private fun startSleepTimerUiUpdates() {
        sleepTimerJob?.cancel()
        if (EchoPlaybackProcessRuntime.sleepTimerEndTimeEpochMs == null) return
        sleepTimerJob = EchoPlaybackProcessRuntime.scope.launch {
            while (EchoPlaybackProcessRuntime.sleepTimerEndTimeEpochMs != null) {
                updatePlaybackStatusOptions()
                delay(SLEEP_TIMER_TICK_MS.milliseconds)
            }
            controller?.let(::updatePlaybackCore) ?: updatePlaybackStatusOptions()
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

    private fun sleepTimerRemainingMs(): Long =
        EchoPlaybackProcessRuntime.sleepTimerRemainingMs()

    private fun applyReplayGain() {
        val output = echoReplayGainOutput(
            enabled = replayGainEnabled,
            preampDb = replayGainPreampDb,
            trackGainDb = activeReplayGainTrackGainDb,
        )
        controller?.volume = output.playerVolume
        EchoPlaybackProcessRuntime.syncLoudnessEnhancer(
            audioSessionId = controller?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET,
            enhancerGainMb = output.enhancerGainMb,
        )
    }

    private fun updateActiveReplayGainTrack(trackId: String?) {
        if (activeReplayGainTrackId == trackId) return
        activeReplayGainTrackId = trackId
        activeReplayGainTrackGainDb = trackId?.let(replayGainTrackGainsByMediaId::get)
        applyReplayGain()
    }

    private fun loadReplayGainForTrack(trackId: String?) {
        if (!replayGainEnabled) return
        if (trackId == null || replayGainTrackGainsByMediaId.containsKey(trackId)) return
        val uri = replayGainUrisByMediaId[trackId] ?: return
        replayGainJob?.cancel()
        replayGainJob = EchoPlaybackProcessRuntime.scope.launch {
            val gainDb = withContext(Dispatchers.IO) {
                runCatching {
                    application.contentResolver.openInputStream(uri.toUri())?.use(ReplayGainReader::readTrackGainDb)
                }.getOrNull()
            }
            replayGainTrackGainsByMediaId[trackId] = gainDb
            if (activeReplayGainTrackId == trackId) {
                activeReplayGainTrackGainDb = gainDb
                applyReplayGain()
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

    private fun MediaController.recoverAndPlay() {
        if (
            PlaybackSessionPolicy.shouldPrepareBeforePlay(
                hasPlayerError = playerError != null,
                playbackStateIdle = playbackState == Player.STATE_IDLE,
            )
        ) {
            prepare()
        }
        play()
    }

    private fun <T> updateState(flow: MutableStateFlow<T>, value: T) {
        if (flow.value != value) {
            flow.value = value
        }
    }

    private suspend fun restorePlaybackSessionIfNeeded(mediaController: MediaController) {
        if (restoredPlaybackSession) {
            restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
            return
        }
        restoredPlaybackSession = true
        val session = try {
            withContext(Dispatchers.IO) {
                settingsStore.getSavedPlaybackSession()
            }
        } catch (cancelled: CancellationException) {
            restoredPlaybackSession = false
            throw cancelled
        } catch (_: Exception) {
            restoredPlaybackSession = false
            return
        }
        if (session == null) {
            restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
            return
        }
        if (mediaController.mediaItemCount > 0 || mediaController.currentMediaItem != null) {
            restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
            return
        }
        if (
            shouldAbandonSavedSessionRestoreForPendingQueueReplace(
                pendingControllerActions.map { it.replacesQueue },
            )
        ) {
            return
        }
        try {
            sampleRatesByMediaId.clear()
            replayGainUrisByMediaId.clear()
            replayGainTrackGainsByMediaId.clear()
            session.queue.forEach { track ->
                replayGainUrisByMediaId[track.id] = track.uri
            }
            mediaController.setMediaItems(
                session.queue.map { it.toMediaItem() },
                session.currentIndex,
                session.positionMs,
            )
            mediaController.shuffleModeEnabled = session.shuffleEnabled
            mediaController.repeatMode = session.repeatMode.toPlayerRepeatMode()
            mediaController.setPlaybackParameters(
                PlaybackParameters(session.playbackSpeed, session.playbackPitch),
            )
            mediaController.prepare()
            if (session.playWhenReady) {
                mediaController.play()
            } else {
                mediaController.pause()
            }
            restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
        } catch (cancelled: CancellationException) {
            restoredPlaybackSession = false
            throw cancelled
        } catch (_: Exception) {
            restoredPlaybackSession = false
        }
    }

    private fun persistPlaybackSession(force: Boolean = false) {
        val mediaController = controller ?: return
        val playerMediaIds = (0 until mediaController.mediaItemCount).map { index ->
            mediaController.getMediaItemAt(index).mediaId
        }
        val currentIndex = mediaController.currentMediaItemIndex
            .takeIf { it in 0 until mediaController.mediaItemCount }
            ?: -1
        val signature = playbackSessionPersistSignature(
            currentIndex = currentIndex,
            playWhenReady = mediaController.playWhenReady,
            mediaIds = playerMediaIds,
            shuffleEnabled = mediaController.shuffleModeEnabled,
            repeatMode = mediaController.repeatMode,
            playbackSpeed = mediaController.playbackParameters.speed,
            playbackPitch = mediaController.playbackParameters.pitch,
        )
        val positionMs = mediaController.currentPosition.coerceAtLeast(0L)
        val positionBucket = positionMs / PERSIST_POSITION_BUCKET_MS
        if (
            PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = force,
                signature = signature,
                lastSignature = lastPersistedSessionSignature,
                positionBucket = positionBucket,
                lastPositionBucket = lastPersistedPositionBucket,
            ) || PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = force,
                signature = signature,
                lastSignature = pendingPersistSessionSignature,
                positionBucket = positionBucket,
                lastPositionBucket = pendingPersistPositionBucket,
            )
        ) {
            return
        }
        val cachedQueue = _playbackQueue.value
        val queue = if (
            PlaybackSessionPolicy.shouldReuseCachedQueueSnapshot(
                cachedMediaIds = cachedQueue.items.map { it.id },
                playerMediaIds = playerMediaIds,
            )
        ) {
            cachedQueue.copy(currentIndex = currentIndex)
        } else {
            mediaController.toPlaybackQueueState()
        }
        val currentTrack = mediaController.currentMediaItem?.toEchoTrackRef(
            durationMs = mediaController.duration.takeIf { it > 0L } ?: 0L,
        )
        val session = if (queue.items.isEmpty() || currentTrack == null || queue.currentIndex !in queue.items.indices) {
            null
        } else {
            EchoSavedPlaybackSession(
                queue = queue.items,
                currentIndex = queue.currentIndex,
                positionMs = positionMs,
                playWhenReady = mediaController.playWhenReady,
                shuffleEnabled = mediaController.shuffleModeEnabled,
                repeatMode = mediaController.repeatMode.toEchoRepeatMode(),
                playbackSpeed = mediaController.playbackParameters.speed,
                playbackPitch = mediaController.playbackParameters.pitch,
            )
        }
        if (
            session == null &&
            !PlaybackSessionPolicy.shouldPersistNullSavedSession(mediaController.mediaItemCount)
        ) {
            return
        }
        if (
            !PlaybackSessionPolicy.shouldPersistSavedSession(
                restoreCompleted = restoreCompleted,
                hasPendingPlay = pendingControllerActions.any { it.replacesQueue },
                queueEmpty = session == null,
            )
        ) {
            return
        }
        val generation = ++persistenceGeneration
        persistenceJob?.cancel()
        pendingPersistSessionSignature = signature
        pendingPersistPositionBucket = positionBucket
        persistenceJob = EchoPlaybackProcessRuntime.launchIo {
            var saved = false
            try {
                settingsStore.savePlaybackSession(session)
                saved = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A later playback tick retries because the persisted markers are not advanced.
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (generation == persistenceGeneration) {
                        if (saved) {
                            lastPersistedSessionSignature = signature
                            lastPersistedPositionBucket = positionBucket
                        }
                        pendingPersistSessionSignature = null
                        pendingPersistPositionBucket = null
                    }
                }
            }
        }
    }

    private fun replaceQueueLookups(tracks: List<EchoTrack>) {
        sampleRatesByMediaId.clear()
        replayGainUrisByMediaId.clear()
        replayGainTrackGainsByMediaId.clear()
        tracks.forEach { track ->
            sampleRatesByMediaId[track.id] = track.sampleRateHz
            replayGainUrisByMediaId[track.id] = track.uri
        }
    }

    private fun startDetachedSessionPersistence(mediaController: MediaController) {
        val detachedSettingsStore = settingsStore
        var lastSignature: String? = null
        var lastPositionBucket: Long? = null
        var cachedMediaIds: List<String> = emptyList()
        var cachedQueue = PlaybackQueueState()
        EchoPlaybackProcessRuntime.startProgress(PERSIST_POSITION_BUCKET_MS) { player ->
            if (player !== mediaController) return@startProgress
            val mediaIds = (0 until player.mediaItemCount).map { index -> player.getMediaItemAt(index).mediaId }
            val currentIndex = player.currentMediaItemIndex.takeIf { it in 0 until player.mediaItemCount } ?: -1
            val signature = playbackSessionPersistSignature(
                currentIndex = currentIndex,
                playWhenReady = player.playWhenReady,
                mediaIds = mediaIds,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                playbackSpeed = player.playbackParameters.speed,
                playbackPitch = player.playbackParameters.pitch,
            )
            val positionBucket = player.currentPosition.coerceAtLeast(0L) / PERSIST_POSITION_BUCKET_MS
            if (signature == lastSignature && positionBucket == lastPositionBucket) return@startProgress
            val queue = if (PlaybackSessionPolicy.shouldReuseCachedQueueSnapshot(cachedMediaIds, mediaIds)) {
                cachedQueue.copy(currentIndex = currentIndex)
            } else {
                player.toPlaybackQueueState().also {
                    cachedMediaIds = mediaIds
                    cachedQueue = it
                }
            }
            val session = player.toSavedPlaybackSession(queue)
            withContext(Dispatchers.IO) {
                detachedSettingsStore.savePlaybackSession(session)
            }
            lastSignature = signature
            lastPositionBucket = positionBucket
        }
    }

    private fun EchoRepeatMode.toPlayerRepeatMode(): Int = when (this) {
        EchoRepeatMode.All -> Player.REPEAT_MODE_ALL
        EchoRepeatMode.One -> Player.REPEAT_MODE_ONE
        EchoRepeatMode.Off -> Player.REPEAT_MODE_OFF
    }

    private fun skipToNextAfterError(player: Player) {
        val shuffledNextIndex = if (player.shuffleModeEnabled) {
            shuffledNextIndex(player)
        } else {
            null
        }
        val targetIndex = nextIndexAfterPlaybackError(
            currentIndex = player.currentMediaItemIndex,
            mediaItemCount = player.mediaItemCount,
            repeatAll = player.repeatMode == Player.REPEAT_MODE_ALL,
            consecutiveErrorSkips = consecutiveErrorSkips,
            shuffledNextIndex = shuffledNextIndex,
        ) ?: return
        consecutiveErrorSkips += 1
        stickyErrorAutoSkipped = true
        player.seekTo(targetIndex, 0L)
        player.prepare()
        player.play()
    }

    private fun shuffledNextIndex(player: Player): Int? {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        val nextOff = timeline.getNextWindowIndex(
            player.currentMediaItemIndex,
            Player.REPEAT_MODE_OFF,
            true,
        )
        if (nextOff != C.INDEX_UNSET) return nextOff
        if (player.repeatMode != Player.REPEAT_MODE_ALL) return null
        val nextAll = timeline.getNextWindowIndex(
            player.currentMediaItemIndex,
            Player.REPEAT_MODE_ALL,
            true,
        )
        return nextAll.takeIf { it != C.INDEX_UNSET }
    }

    private fun resetStickyPlaybackError() {
        consecutiveErrorSkips = 0
        stickyPlaybackError = null
        stickyErrorMediaId = null
        stickyErrorAutoSkipped = false
    }

    private fun resolveProgressUpdateIntervalMs(
        effectivePerformanceMode: EchoEffectivePerformanceMode,
        uiVisibility: PlaybackProgressUiVisibility,
    ): Long =
        when (uiVisibility) {
            PlaybackProgressUiVisibility.Background -> {
                if (effectivePerformanceMode.isLightweight) {
                    LIGHTWEIGHT_BACKGROUND_PROGRESS_INTERVAL_MS
                } else {
                    BACKGROUND_PROGRESS_INTERVAL_MS
                }
            }
            PlaybackProgressUiVisibility.NowPlayingExpanded -> {
                when {
                    effectivePerformanceMode.isLightweight -> LIGHTWEIGHT_NOW_PLAYING_PROGRESS_INTERVAL_MS
                    effectivePerformanceMode.isHighPerformance -> HIGH_PERFORMANCE_NOW_PLAYING_PROGRESS_INTERVAL_MS
                    else -> NOW_PLAYING_PROGRESS_INTERVAL_MS
                }
            }
            PlaybackProgressUiVisibility.MiniPlayer -> {
                when {
                    effectivePerformanceMode.isLightweight -> LIGHTWEIGHT_PROGRESS_INTERVAL_MS
                    effectivePerformanceMode.isHighPerformance -> HIGH_PERFORMANCE_PROGRESS_INTERVAL_MS
                    else -> MINI_PLAYER_PROGRESS_INTERVAL_MS
                }
            }
        }

    private companion object {
        const val NOW_PLAYING_PROGRESS_INTERVAL_MS = 500L
        const val HIGH_PERFORMANCE_NOW_PLAYING_PROGRESS_INTERVAL_MS = 250L
        const val LIGHTWEIGHT_NOW_PLAYING_PROGRESS_INTERVAL_MS = 1_000L
        const val HIGH_PERFORMANCE_PROGRESS_INTERVAL_MS = 500L
        const val MINI_PLAYER_PROGRESS_INTERVAL_MS = 1_000L
        const val LIGHTWEIGHT_PROGRESS_INTERVAL_MS = 2_000L
        const val BACKGROUND_PROGRESS_INTERVAL_MS = 5_000L
        const val LIGHTWEIGHT_BACKGROUND_PROGRESS_INTERVAL_MS = 10_000L
        const val PlaybackErrorSkipCommand = "skip_error"
        const val MIN_PLAYBACK_SPEED = 0.5f
        const val MAX_PLAYBACK_SPEED = 2.0f
        const val SLEEP_TIMER_TICK_MS = 1_000L
        const val MAX_SLEEP_TIMER_MINUTES = 180
        const val MIN_REPLAY_GAIN_PREAMP_DB = -12f
        const val MAX_REPLAY_GAIN_PREAMP_DB = 6f
        const val PERSIST_POSITION_BUCKET_MS = 15_000L
        const val CONTROLLER_CONNECT_MAX_RETRIES = 3
        const val CONTROLLER_CONNECT_RETRY_MS = 400L
        val PlaybackCoreEvents = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_MEDIA_METADATA_CHANGED,
            Player.EVENT_TRACKS_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
            Player.EVENT_PLAYER_ERROR,
            Player.EVENT_AUDIO_SESSION_ID,
        )
    }
}

private fun Player.toSavedPlaybackSession(queue: PlaybackQueueState): EchoSavedPlaybackSession? {
    if (queue.items.isEmpty() || queue.currentIndex !in queue.items.indices || currentMediaItem == null) return null
    return EchoSavedPlaybackSession(
        queue = queue.items,
        currentIndex = queue.currentIndex,
        positionMs = currentPosition.coerceAtLeast(0L),
        playWhenReady = playWhenReady,
        shuffleEnabled = shuffleModeEnabled,
        repeatMode = repeatMode.toEchoRepeatMode(),
        playbackSpeed = playbackParameters.speed,
        playbackPitch = playbackParameters.pitch,
    )
}

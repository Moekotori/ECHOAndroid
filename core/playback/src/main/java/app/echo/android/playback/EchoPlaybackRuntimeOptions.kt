package app.echo.android.playback

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.echo.android.model.playback.EchoSleepTimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EchoPlaybackRuntimeOptions(
    val skipSilenceEnabled: Boolean = false,
)

object EchoPlaybackRuntimeOptionsStore {
    private val _options = MutableStateFlow(EchoPlaybackRuntimeOptions())
    val options: StateFlow<EchoPlaybackRuntimeOptions> = _options.asStateFlow()

    fun setSkipSilenceEnabled(enabled: Boolean) {
        _options.value = _options.value.copy(skipSilenceEnabled = enabled)
    }
}

@UnstableApi
object EchoPlaybackProcessRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var sleepTimerEndTimeEpochMs: Long? = null
        private set

    @Volatile
    var sleepTimerMode: EchoSleepTimerMode = EchoSleepTimerMode.Off
        private set

    @Volatile
    var sleepTimerRequestedMinutes: Int? = null
        private set

    @Volatile
    var replayGainEnabled: Boolean = false
        private set

    @Volatile
    var replayGainPreampDb: Float = 0f
        private set

    @Volatile
    var usbExclusiveEnabled: Boolean = false
        private set

    @Volatile
    var usbExclusiveSinkStatus: EchoUsbExclusiveSinkStatus? = null
        private set

    @Volatile
    var exclusiveMakeupGain: Float = 1f
        private set

    @Volatile
    private var equalizer: EchoEqualizerController? = null

    @Volatile
    private var mediaController: Player? = null

    @Volatile
    private var usbMonitor: EchoUsbAudioMonitor? = null

    @Volatile
    private var enginePolicy: EchoPlaybackEnginePolicy? = null

    @Volatile
    private var catalog: EchoPlaybackCatalog = EchoPlaybackCatalog.Empty

    @Volatile
    private var sessionStore: EchoPlaybackSessionStore = EchoPlaybackSessionStore.Empty

    @Volatile
    var surfaceSnapshot: EchoPlaybackSurfaceSnapshot = EchoPlaybackSurfaceSnapshot()
        private set

    @Volatile
    private var surfaceListener: ((EchoPlaybackSurfaceSnapshot) -> Unit)? = null

    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessAudioSessionId: Int = AUDIO_SESSION_UNSET

    fun equalizerController(): EchoEqualizerController =
        synchronized(this) {
            equalizer ?: EchoEqualizerController().also { equalizer = it }
        }

    fun setUsbExclusiveEnabled(enabled: Boolean) {
        usbExclusiveEnabled = enabled
        if (!enabled) {
            usbExclusiveSinkStatus = null
            exclusiveMakeupGain = 1f
        }
    }

    fun setUsbExclusiveSinkStatus(status: EchoUsbExclusiveSinkStatus?) {
        usbExclusiveSinkStatus = status
        if (status?.streaming != true) {
            exclusiveMakeupGain = 1f
        }
    }

    fun setExclusiveMakeupGain(gain: Float) {
        exclusiveMakeupGain = gain.coerceAtLeast(0f)
    }

    fun reconfigureAudioPipeline() {
        val player = mediaController ?: return
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return
        player.seekTo(player.currentPosition)
    }

    fun usbAudioMonitor(context: Context): EchoUsbAudioMonitor =
        synchronized(this) {
            usbMonitor ?: EchoUsbAudioMonitor(context.applicationContext).also { monitor ->
                usbMonitor = monitor
                monitor.start()
            }
        }

    fun enginePolicy(context: Context): EchoPlaybackEnginePolicy =
        synchronized(this) {
            enginePolicy ?: EchoPlaybackEnginePolicy(
                context = context.applicationContext,
                usbAudioMonitor = usbAudioMonitor(context),
            ).also { enginePolicy = it }
        }

    fun enginePolicyOrNull(): EchoPlaybackEnginePolicy? = enginePolicy

    fun setCatalog(catalog: EchoPlaybackCatalog) {
        this.catalog = catalog
    }

    fun catalog(): EchoPlaybackCatalog = catalog

    fun setSessionStore(store: EchoPlaybackSessionStore) {
        sessionStore = store
    }

    fun sessionStore(): EchoPlaybackSessionStore = sessionStore

    fun setSurfaceListener(listener: ((EchoPlaybackSurfaceSnapshot) -> Unit)?) {
        surfaceListener = listener
    }

    fun publishSurface(snapshot: EchoPlaybackSurfaceSnapshot) {
        surfaceSnapshot = snapshot
        surfaceListener?.invoke(snapshot)
    }

    fun bindPlayer(player: Player) {
        progressJob?.cancel()
        val previous = mediaController
        mediaController = player
        if (previous !== null && previous !== player) {
            runCatching { previous.release() }
        }
        val pauseAtEnd = EchoSleepTimerPolicy.shouldPauseAtEndOfMediaItem(sleepTimerMode)
        (player as? ExoPlayer)?.pauseAtEndOfMediaItems = pauseAtEnd
        enginePolicy?.setPauseAtEndOfMediaItems(pauseAtEnd)
        ensureSleepTimer()
    }

    fun startProgress(intervalMs: Long, onTick: suspend (Player) -> Unit) {
        progressJob?.cancel()
        if (intervalMs <= 0L) return
        progressJob = scope.launch {
            while (true) {
                delay(intervalMs)
                mediaController?.let { player -> onTick(player) }
            }
        }
    }

    fun launchIo(block: suspend () -> Unit): Job =
        scope.launch(Dispatchers.IO) { block() }

    fun setSleepTimerMinutes(minutes: Int, maxMinutes: Int = 180) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerMode = EchoSleepTimerMode.Timed
        sleepTimerRequestedMinutes = minutes.coerceAtMost(maxMinutes)
        sleepTimerEndTimeEpochMs =
            System.currentTimeMillis() + minutes.coerceAtMost(maxMinutes) * 60_000L
        (mediaController as? ExoPlayer)?.pauseAtEndOfMediaItems = false
        enginePolicy?.setPauseAtEndOfMediaItems(false)
        ensureSleepTimer()
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerMode = EchoSleepTimerMode.EndOfTrack
        sleepTimerRequestedMinutes = null
        sleepTimerEndTimeEpochMs = null
        (mediaController as? ExoPlayer)?.pauseAtEndOfMediaItems = true
        enginePolicy?.setPauseAtEndOfMediaItems(true)
        ensureSleepTimer()
    }

    fun setReplayGain(enabled: Boolean, preampDb: Float) {
        replayGainEnabled = enabled
        replayGainPreampDb = preampDb
    }

    fun syncLoudnessEnhancer(audioSessionId: Int, enhancerGainMb: Int) {
        if (audioSessionId != loudnessAudioSessionId) {
            runCatching {
                loudnessEnhancer?.enabled = false
                loudnessEnhancer?.release()
            }
            loudnessEnhancer = null
            loudnessAudioSessionId = audioSessionId
            if (audioSessionId != AUDIO_SESSION_UNSET) {
                runCatching { LoudnessEnhancer(audioSessionId) }.onSuccess { loudnessEnhancer = it }
            }
        }
        val enhancer = loudnessEnhancer ?: return
        runCatching {
            if (enhancerGainMb > 0) {
                enhancer.setTargetGain(enhancerGainMb)
                enhancer.enabled = true
            } else {
                enhancer.enabled = false
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerMode = EchoSleepTimerMode.Off
        sleepTimerRequestedMinutes = null
        sleepTimerEndTimeEpochMs = null
        (mediaController as? ExoPlayer)?.pauseAtEndOfMediaItems = false
        enginePolicy?.setPauseAtEndOfMediaItems(false)
        sleepJob?.cancel()
        sleepJob = null
    }

    fun sleepTimerRemainingMs(
        nowEpochMs: Long = System.currentTimeMillis(),
        trackRemainingMs: Long = 0L,
        trackDurationKnown: Boolean = false,
    ): Long =
        EchoSleepTimerPolicy.remainingMs(
            mode = sleepTimerMode,
            nowEpochMs = nowEpochMs,
            timedEndEpochMs = sleepTimerEndTimeEpochMs,
            trackRemainingMs = trackRemainingMs,
            trackDurationKnown = trackDurationKnown,
        )

    private fun ensureSleepTimer() {
        sleepJob?.cancel()
        if (sleepTimerMode == EchoSleepTimerMode.Off) return
        sleepJob = scope.launch {
            while (sleepTimerMode != EchoSleepTimerMode.Off) {
                val player = enginePolicyOrNull()?.boundPlayer() ?: mediaController
                val durationMs = player?.duration?.takeIf { it > 0L } ?: 0L
                val remainingMs = sleepTimerRemainingMs(
                    trackRemainingMs = (durationMs - (player?.currentPosition ?: 0L)).coerceAtLeast(0L),
                    trackDurationKnown = durationMs > 0L,
                )
                enginePolicyOrNull()?.applyReplayGain()
                if (EchoSleepTimerPolicy.shouldPause(sleepTimerMode, remainingMs)) {
                    player?.pause()
                    mediaController?.pause()
                    cancelSleepTimer()
                    enginePolicyOrNull()?.applyReplayGain()
                    return@launch
                }
                delay(EchoSleepTimerPolicy.tickMs(remainingMs))
            }
        }
    }
}

private const val AUDIO_SESSION_UNSET = 0

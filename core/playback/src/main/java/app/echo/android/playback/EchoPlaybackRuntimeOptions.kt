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
    private var equalizer: EchoEqualizerController? = null

    @Volatile
    private var mediaController: Player? = null

    @Volatile
    private var usbMonitor: EchoUsbAudioMonitor? = null

    @Volatile
    private var enginePolicy: EchoPlaybackEnginePolicy? = null

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
        }
    }

    fun setUsbExclusiveSinkStatus(status: EchoUsbExclusiveSinkStatus?) {
        usbExclusiveSinkStatus = status
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
        sleepTimerEndTimeEpochMs =
            System.currentTimeMillis() + minutes.coerceAtMost(maxMinutes) * 60_000L
        (mediaController as? ExoPlayer)?.pauseAtEndOfMediaItems = false
        enginePolicy?.setPauseAtEndOfMediaItems(false)
        ensureSleepTimer()
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerMode = EchoSleepTimerMode.EndOfTrack
        sleepTimerEndTimeEpochMs = null
        (mediaController as? ExoPlayer)?.pauseAtEndOfMediaItems = true
        enginePolicy?.setPauseAtEndOfMediaItems(true)
        sleepJob?.cancel()
        sleepJob = null
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
        val endTime = sleepTimerEndTimeEpochMs ?: return
        sleepJob = scope.launch {
            while (true) {
                val remainingMs = (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
                if (remainingMs <= 0L) {
                    sleepTimerEndTimeEpochMs = null
                    mediaController?.pause()
                    return@launch
                }
                delay(1_000)
            }
        }
    }
}

private const val AUDIO_SESSION_UNSET = 0

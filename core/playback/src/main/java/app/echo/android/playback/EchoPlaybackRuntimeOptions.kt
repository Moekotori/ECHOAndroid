package app.echo.android.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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

object EchoPlaybackProcessRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var sleepTimerEndTimeEpochMs: Long? = null
        private set

    @Volatile
    var replayGainEnabled: Boolean = false
        private set

    @Volatile
    var replayGainPreampDb: Float = 0f
        private set

    @Volatile
    private var equalizer: EchoEqualizerController? = null

    @Volatile
    private var mediaController: Player? = null

    private var progressJob: Job? = null
    private var sleepJob: Job? = null

    fun equalizerController(): EchoEqualizerController =
        synchronized(this) {
            equalizer ?: EchoEqualizerController(scope).also { equalizer = it }
        }

    fun bindPlayer(player: Player) {
        progressJob?.cancel()
        val previous = mediaController
        mediaController = player
        if (previous !== null && previous !== player) {
            runCatching { previous.release() }
        }
        ensureSleepTimer()
    }

    fun startProgress(intervalMs: Long, onTick: (Player) -> Unit) {
        progressJob?.cancel()
        if (intervalMs <= 0L) return
        progressJob = scope.launch {
            while (true) {
                mediaController?.let(onTick)
                delay(intervalMs.milliseconds)
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
        sleepTimerEndTimeEpochMs =
            System.currentTimeMillis() + minutes.coerceAtMost(maxMinutes) * 60_000L
        ensureSleepTimer()
    }

    fun setReplayGain(enabled: Boolean, preampDb: Float) {
        replayGainEnabled = enabled
        replayGainPreampDb = preampDb
    }

    fun cancelSleepTimer() {
        sleepTimerEndTimeEpochMs = null
        sleepJob?.cancel()
        sleepJob = null
    }

    fun sleepTimerRemainingMs(): Long {
        val endTime = sleepTimerEndTimeEpochMs ?: return 0L
        return (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
    }

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

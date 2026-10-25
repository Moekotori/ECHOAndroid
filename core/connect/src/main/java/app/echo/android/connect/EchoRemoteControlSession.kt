package app.echo.android.connect

import app.echo.android.model.connect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One bounded, session-owned intent per absolute control. Wire commands remain unchanged. */
internal class EchoRemoteControlSession(
    private val scope: CoroutineScope,
    private val transport: EchoLinkTransport,
    private val onSnapshot: (EchoRemoteEndpoint, EchoLinkStatusResponse) -> Unit,
    private val onChanged: () -> Unit,
    private val onFailure: (EchoRemoteEndpoint, Throwable) -> Unit,
    private val onNotice: (String) -> Unit,
    private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val retryDelays: List<Long> = listOf(500, 1_000, 2_000),
    private val lifetimeMs: Long = 10_000,
    private val commandMutex: Mutex = Mutex(),
) {
    private data class Desired<T>(val value: T, val revision: Long, val success: () -> Unit)
    private var target: EchoRemoteEndpoint? = null
    private var confirmed = EchoRemotePlaybackSnapshot()
    private var play: Desired<Boolean>? = null
    private var seek: Desired<Long>? = null
    private var volume: Desired<Float>? = null
    private var identity: String? = null
    private var revision = 0L
    private var deadline = 0L
    private var active = true
    private var job: Job? = null
    private var toggleIssued = -1L
    private var toggleInFlight = false
    private var seekIssued = -1L
    private var seekAttemptRevision = -1L
    private var seekIssuedAt = 0L
    private var volumeIssued = -1L
    val confirmedSnapshot get() = confirmed
    val pending: Boolean get() = play != null || seek != null || volume != null
    private fun source(s: EchoRemotePlaybackSnapshot) = s.queue.currentQueueId ?: s.track?.id

    fun bind(endpoint: EchoRemoteEndpoint?) {
        clear(); target = endpoint; confirmed = EchoRemotePlaybackSnapshot(); identity = null
    }
    fun setActive(value: Boolean) {
        active = value
        if (!value) {
            job?.cancel(); job = null
            if (toggleInFlight || play?.revision == toggleIssued) play = null
            toggleInFlight = false
            // Keep only value intent in the background, never a callback capturing a closed UI.
            play = play?.copy(success = {}); seek = seek?.copy(success = {}); volume = volume?.copy(success = {})
        } else start()
    }
    fun clear() {
        job?.cancel(); job = null; play = null; seek = null; volume = null
        toggleIssued = -1; toggleInFlight = false; seekIssued = -1; volumeIssued = -1; revision++; onChanged()
    }
    fun observe(snapshot: EchoRemotePlaybackSnapshot): EchoRemotePlaybackSnapshot {
        // Server timestamps, when supplied, also protect against reordered SSE/status replies.
        if (snapshot.updatedAtEpochMs > 0 && snapshot.updatedAtEpochMs < confirmed.updatedAtEpochMs) return projected()
        if (identity != null && identity != source(snapshot)) { play = null; seek = null; toggleIssued = -1 }
        confirmed = snapshot; identity = source(snapshot)
        if (snapshot.state == EchoRemotePlaybackState.Error) { play = null; seek = null }
        if (!snapshot.volumeControlEnabled) volume = null
        play?.takeIf { (!toggleInFlight || it.revision == toggleIssued) && snapshot.state != EchoRemotePlaybackState.Loading && it.value == (snapshot.state == EchoRemotePlaybackState.Playing) }?.let { play = null; it.success() }
        seek?.takeIf {
            val advance = if (it.revision == seekAttemptRevision && snapshot.state == EchoRemotePlaybackState.Playing)
                (now() - seekIssuedAt).coerceIn(0, lifetimeMs) else 0L
            val distance = snapshot.positionMs.coerceAtLeast(0) - it.value
            distance >= -750 && distance <= advance + 750
        }?.let { seek = null; it.success() }
        volume?.takeIf { kotlin.math.abs(snapshot.volume - it.value) <= .005f }?.let { volume = null; it.success() }
        return projected()
    }
    fun projected(): EchoRemotePlaybackSnapshot = confirmed.copy(
        state = play?.let { if (it.value) EchoRemotePlaybackState.Playing else EchoRemotePlaybackState.Paused } ?: confirmed.state,
        positionMs = seek?.value ?: confirmed.positionMs,
        volume = volume?.value ?: confirmed.volume,
    )
    fun request(command: EchoRemoteCommand, success: () -> Unit): Boolean {
        if (target == null) return false
        when (command) {
            EchoRemoteCommand.PlayPause -> {
                if (identity == null || confirmed.state == EchoRemotePlaybackState.Loading || confirmed.state == EchoRemotePlaybackState.Error) return false
                play = Desired(!(projected().state == EchoRemotePlaybackState.Playing), ++revision, success)
            }
            is EchoRemoteCommand.SeekTo -> {
                if (identity == null || confirmed.durationMs <= 0) return false
                seek = Desired(command.positionMs.coerceIn(0, confirmed.durationMs), ++revision, success)
            }
            is EchoRemoteCommand.SetVolume -> {
                if (!confirmed.volumeControlEnabled) return false
                volume = Desired(command.volume.coerceIn(0f, 1f), ++revision, success)
            }
            else -> return false
        }
        deadline = now() + lifetimeMs
        onChanged(); start(force = true); return true
    }
    private fun start(force: Boolean = false) {
        if ((!active && !force) || !pending || job?.isActive == true) return
        if (now() >= deadline) { clear(); onNotice("expired"); return }
        val endpoint = target ?: return
        // Assign ownership before any transport or acknowledgement callback can run inline.
        val worker = scope.launch(start = CoroutineStart.LAZY) {
            var failures = 0
            try {
                while (isActive && target == endpoint && pending && now() < deadline) {
                    var sentToggle = false
                    try {
                        commandMutex.withLock {
                            if (now() >= deadline) { clearPending(); onNotice("expired"); return@withLock }
                            val fresh = withTimeout(4_000) { transport.fetchStatus(endpoint) }
                            if (target != endpoint) return@launch
                            onSnapshot(endpoint, fresh)
                            if (fresh.playback == null) { clearPending(); onNotice("unsupported"); return@withLock }
                            if (!pending || fresh.playback.state == EchoRemotePlaybackState.Loading) return@withLock
                            if (now() >= deadline) { clearPending(); onNotice("expired"); return@withLock }
                            val p = play; val s = seek; val v = volume
                            val command = when {
                                p != null && toggleIssued != p.revision -> EchoRemoteCommand.PlayPause
                                s != null && seekIssued != s.revision -> EchoRemoteCommand.SeekTo(s.value)
                                v != null && volumeIssued != v.revision -> EchoRemoteCommand.SetVolume(v.value)
                                else -> null
                            }
                            if (command != null) {
                                if (command == EchoRemoteCommand.PlayPause) { toggleIssued = p!!.revision; sentToggle = true; toggleInFlight = true }
                                if (command is EchoRemoteCommand.SeekTo) {
                                    seekIssued = s!!.revision; seekAttemptRevision = s.revision; seekIssuedAt = now()
                                }
                                if (command is EchoRemoteCommand.SetVolume) volumeIssued = v!!.revision
                                val response = withTimeout(4_000) { transport.sendCommand(endpoint, command) }
                                if (target != endpoint) return@launch
                                // A command ACK is not a playback confirmation. Read actual state if absent.
                                val acknowledged = response ?: withTimeout(4_000) { transport.fetchStatus(endpoint) }
                                onSnapshot(endpoint, acknowledged)
                                toggleInFlight = false
                            }
                        }
                        failures = 0
                        if (pending) delay(250)
                    } catch (cancelled: CancellationException) {
                        if (cancelled !is TimeoutCancellationException) throw cancelled
                        onFailure(endpoint, cancelled)
                        toggleInFlight = false; seekIssued = -1; volumeIssued = -1
                        if (sentToggle) { play = null; onNotice("uncertain") }
                        failures++
                        if (failures > retryDelays.size) break
                        delay(retryDelays[failures - 1])
                    } catch (error: Exception) {
                        toggleInFlight = false; seekIssued = -1; volumeIssued = -1
                        if (sentToggle) { play = null; onNotice("uncertain") }
                        onFailure(endpoint, error)
                        if (error is EchoLinkHttpException && error.statusCode in listOf(400,401,403,404,405,409,422,501)) { clearPending(); break }
                        failures++
                        if (failures > retryDelays.size) break
                        onNotice("queued")
                        delay(retryDelays[failures - 1])
                    }
                    onChanged()
                }
                if (target == endpoint && pending) { clearPending(); onNotice("expired") }
            } finally { onChanged() }
        }
        job = worker
        worker.start()
    }
    private fun clearPending() { play = null; seek = null; volume = null; toggleIssued = -1 }
}

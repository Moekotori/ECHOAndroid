package app.echo.android

import androidx.annotation.StringRes
import app.echo.android.data.EchoAppSettings
import app.echo.android.data.PendingScrobble
import app.echo.android.data.ScrobbleOutboxStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ListenBrainzUiState(
    val pendingCount: Int = 0,
    val isConnecting: Boolean = false,
    @get:StringRes val lastMessageRes: Int = R.string.listenbrainz_disconnected,
    val lastMessageArg: String? = null,
    val lastError: String? = null,
    val userName: String? = null,
)

internal data class ListenBrainzTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
)

private data class ActiveListenBrainzListen(
    val track: ListenBrainzTrack,
    val startedAtEpochSeconds: Long,
    val accumulatedPlayMs: Long = 0L,
    val lastTickEpochMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val wasPlaying: Boolean = false,
    val nowPlayingSent: Boolean = false,
    val submitted: Boolean = false,
    val lastSubmitAttemptEpochMs: Long = 0L,
    val lastNowPlayingAttemptEpochMs: Long = 0L,
)

internal class ListenBrainzScrobbleController(
    private val scope: CoroutineScope,
    store: ScrobbleOutboxStore,
    private val client: ListenBrainzClient = ListenBrainzClient(),
) {
    private val _uiState = MutableStateFlow(ListenBrainzUiState())
    val uiState: StateFlow<ListenBrainzUiState> = _uiState.asStateFlow()

    private val outbox = EchoScrobbleOutbox<String>(
        scope = scope,
        store = store,
        submit = { credentials, listen ->
            client.submitListen(credentials, ListenBrainzTrack(
                listen.trackId, listen.title, listen.artist, listen.album, listen.durationMs,
            ), listen.startedAtEpochSeconds)
        },
        status = { listen, pending, error ->
            val current = _uiState.value
            _uiState.value = current.copy(
                pendingCount = pending.coerceAtLeast(0),
                lastMessageRes = when {
                    error != null -> R.string.listenbrainz_submit_failed
                    pending > 0 -> R.string.scrobble_pending
                    listen != null -> R.string.listenbrainz_recorded
                    else -> current.lastMessageRes
                },
                lastMessageArg = if (pending > 0) pending.toString() else listen?.title ?: current.lastMessageArg,
                lastError = error?.message,
            )
        },
    )
    private var accountFingerprint: String? = null
    private var accountToken: String = ""
    private var nowPlayingJob: Job? = null

    fun retryPendingWhenConnected() = outbox.retryWhenConnected()

    private var settings: EchoAppSettings = EchoAppSettings()
    private var active: ActiveListenBrainzListen? = null
    private var collectJob: Job? = null

    fun start(
        settingsFlow: Flow<EchoAppSettings>,
        playback: Flow<EchoScrobblePlayback>,
    ) {
        collectJob?.cancel()
        collectJob = scope.launch {
            combine(
                settingsFlow.distinctUntilChanged(),
                playback,
            ) { appSettings, snapshot ->
                settings = appSettings
                snapshot
            }.collect(::handleSnapshot)
        }
    }

    fun setConnecting() {
        _uiState.value = ListenBrainzUiState(
            isConnecting = true,
            lastMessageRes = R.string.listenbrainz_connecting,
        )
    }

    fun setConnected(userName: String? = null) {
        val current = _uiState.value
        _uiState.value = current.copy(
            isConnecting = false,
            lastMessageRes = if (userName.isNullOrBlank()) {
                R.string.listenbrainz_connected
            } else {
                R.string.listenbrainz_connected_user
            },
            lastMessageArg = userName?.takeIf { it.isNotBlank() },
            lastError = null,
            userName = userName?.takeIf { it.isNotBlank() } ?: current.userName,
        )
    }

    fun setDisconnected() {
        nowPlayingJob?.cancel()
        outbox.configure(null)
        active = null
        _uiState.value = ListenBrainzUiState(
            lastMessageRes = R.string.listenbrainz_disconnected_done,
        )
    }

    fun setError(message: String) {
        _uiState.value = ListenBrainzUiState(
            lastMessageRes = R.string.listenbrainz_connect_failed,
            lastError = message,
        )
        EchoErrorLog.record(EchoErrorSource.Network, message)
    }

    private suspend fun handleSnapshot(snapshot: EchoScrobblePlayback) {
        val token = settings.listenBrainzToken?.trim().orEmpty()
        val fingerprint = if (token != accountToken) {
            token.takeIf(String::isNotBlank)?.let { scrobbleFingerprint(it) }
        } else accountFingerprint
        accountToken = token
        if (fingerprint != accountFingerprint || !settings.listenBrainzEnabled) {
            active = null
            nowPlayingJob?.cancel()
            accountFingerprint = fingerprint
        }
        outbox.configure(fingerprint?.let { ScrobbleAccount(it, token, settings.listenBrainzEnabled) })
        if (
            LastFmScrobbleRules.shouldClearActiveScrobble(
                credentialsReady = settings.listenBrainzEnabled && token.isNotBlank(),
            )
        ) {
            active = null
            return
        }

        val track = snapshot.status.track?.let {
            ListenBrainzTrack(
                id = it.id,
                title = it.title.trim(),
                artist = it.artist.trim(),
                album = it.album?.trim()?.takeIf(String::isNotBlank),
                durationMs = maxOf(it.durationMs, snapshot.position.durationMs),
            )
        }?.takeIf { it.title.isNotBlank() && it.artist.isNotBlank() }

        if (track == null) {
            val current = active
            if (current != null) {
                val nowEpochMs = System.currentTimeMillis()
                val accumulated = LastFmScrobbleRules.accumulatedPlayMs(
                    previouslyAccumulatedMs = current.accumulatedPlayMs,
                    wasPlaying = current.wasPlaying,
                    lastTickEpochMs = current.lastTickEpochMs,
                    nowEpochMs = nowEpochMs,
                )
                val updated = current.copy(
                    accumulatedPlayMs = accumulated,
                    lastTickEpochMs = 0L,
                    wasPlaying = false,
                )
                val flushed = submitListenIfDue(token, updated)
                if (LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(snapshot.status.state)) {
                    active = null
                    return
                }
                active = updated.copy(submitted = updated.submitted || flushed)
                return
            }
            if (LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(snapshot.status.state)) {
                active = null
            }
            return
        }

        val nowEpochMs = System.currentTimeMillis()
        val current = active
        val currentPositionMs = snapshot.position.positionMs.coerceAtLeast(0L)
        active = if (current?.track?.id != track.id) {
            nowPlayingJob?.cancel()
            if (current != null) {
                val accumulated = LastFmScrobbleRules.accumulatedPlayMs(
                    previouslyAccumulatedMs = current.accumulatedPlayMs,
                    wasPlaying = current.wasPlaying,
                    lastTickEpochMs = current.lastTickEpochMs,
                    nowEpochMs = nowEpochMs,
                )
                submitListenIfDue(token, current.copy(accumulatedPlayMs = accumulated))
            }
            ActiveListenBrainzListen(
                track = track,
                startedAtEpochSeconds = if (snapshot.status.isPlaying) nowEpochMs / 1000L else 0L,
                lastTickEpochMs = if (snapshot.status.isPlaying) nowEpochMs else 0L,
                lastPositionMs = currentPositionMs,
                wasPlaying = snapshot.status.isPlaying,
            )
        } else if (
            LastFmScrobbleRules.shouldStartNewListenAfterRepeat(
                alreadyScrobbled = current.submitted,
                previousPositionMs = current.lastPositionMs,
                currentPositionMs = currentPositionMs,
            )
        ) {
            ActiveListenBrainzListen(
                track = track,
                startedAtEpochSeconds = if (snapshot.status.isPlaying) nowEpochMs / 1000L else 0L,
                lastTickEpochMs = if (snapshot.status.isPlaying) nowEpochMs else 0L,
                lastPositionMs = currentPositionMs,
                wasPlaying = snapshot.status.isPlaying,
            )
        } else {
            current.copy(
                startedAtEpochSeconds = if (current.startedAtEpochSeconds <= 0L && snapshot.status.isPlaying) {
                    nowEpochMs / 1_000L
                } else current.startedAtEpochSeconds,
                track = current.track.copy(durationMs = maxOf(current.track.durationMs, track.durationMs)),
                accumulatedPlayMs = LastFmScrobbleRules.accumulatedPlayMs(
                    previouslyAccumulatedMs = current.accumulatedPlayMs,
                    wasPlaying = current.wasPlaying,
                    lastTickEpochMs = current.lastTickEpochMs,
                    nowEpochMs = nowEpochMs,
                ),
                lastTickEpochMs = if (snapshot.status.isPlaying) nowEpochMs else 0L,
                lastPositionMs = currentPositionMs,
                wasPlaying = snapshot.status.isPlaying,
            )
        }

        var activeListen = active ?: return
        if (LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = activeListen.submitted,
                lastAttemptEpochMs = activeListen.lastSubmitAttemptEpochMs, nowEpochMs = nowEpochMs,
            ) && LastFmScrobbleRules.shouldScrobble(
                activeListen.track.durationMs, activeListen.accumulatedPlayMs,
            )
        ) {
            val queued = submitListenIfDue(token, activeListen)
            activeListen = activeListen.copy(submitted = queued, lastSubmitAttemptEpochMs = nowEpochMs)
            active = activeListen
        }
        if (!snapshot.status.isPlaying) return

        if (!activeListen.nowPlayingSent &&
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = activeListen.nowPlayingSent,
                lastAttemptEpochMs = activeListen.lastNowPlayingAttemptEpochMs,
                nowEpochMs = nowEpochMs,
            )
        ) {
            val nowPlayingTrack = activeListen.track
            active = activeListen.copy(
                nowPlayingSent = true,
                lastNowPlayingAttemptEpochMs = nowEpochMs,
            )
            nowPlayingJob = scope.launch {
                client.submitPlayingNow(token, nowPlayingTrack)
                    .also { currentCoroutineContext().ensureActive() }
                    .onSuccess {
                        if (accountFingerprint != fingerprint || _uiState.value.pendingCount > 0) return@onSuccess
                        _uiState.value = ListenBrainzUiState(
                            lastMessageRes = R.string.listenbrainz_now_playing,
                            lastMessageArg = nowPlayingTrack.title,
                            userName = _uiState.value.userName,
                        )
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        if (accountFingerprint != fingerprint || _uiState.value.pendingCount > 0) return@onFailure
                        if (active?.track?.id == nowPlayingTrack.id &&
                            !LastFmScrobbleRules.keepSubmittedFlag(false)
                        ) {
                            active = active?.copy(nowPlayingSent = false)
                        }
                        val message = error.message ?: "ListenBrainz now playing failed"
                        _uiState.value = ListenBrainzUiState(
                            lastMessageRes = R.string.listenbrainz_now_playing_failed,
                            lastError = message,
                            userName = _uiState.value.userName,
                        )
                        EchoErrorLog.record(EchoErrorSource.Network, message, throwable = error)
                    }
            }
        }
    }

    private suspend fun submitListenIfDue(
        token: String,
        listen: ActiveListenBrainzListen,
    ): Boolean {
        if (!LastFmScrobbleRules.shouldFlushScrobbleBeforeReplacing(
                alreadyScrobbled = listen.submitted, durationMs = listen.track.durationMs,
                listenedMs = listen.accumulatedPlayMs,
            )) return false
        return try {
            outbox.enqueue(PendingScrobble(
                account = scrobbleFingerprint(token),
                trackId = listen.track.id, title = listen.track.title, artist = listen.track.artist,
                album = listen.track.album, durationMs = listen.track.durationMs,
                startedAtEpochSeconds = listen.startedAtEpochSeconds,
            ))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.value = _uiState.value.copy(
                lastMessageRes = R.string.listenbrainz_submit_failed,
                lastError = error.message,
            )
            false
        }
    }


}

internal class ListenBrainzClient(
    private val endpoint: String = "https://api.listenbrainz.org",
) {
    suspend fun validateToken(token: String): Result<String> = runCatching {
        val json = getJson(
            path = "/1/validate-token",
            token = token.trim(),
        )
        listenBrainzUserNameFromValidateResponse(json)
            ?: error("ListenBrainz token is not valid")
    }

    suspend fun submitPlayingNow(token: String, track: ListenBrainzTrack): Result<Unit> = runCatching {
        postJson(
            path = "/1/submit-listens",
            token = token,
            body = listenBrainzSubmitBody(
                listenType = "playing_now",
                track = track,
                listenedAtEpochSeconds = null,
            ),
        )
    }

    suspend fun submitListen(
        token: String,
        track: ListenBrainzTrack,
        listenedAtEpochSeconds: Long,
    ): Result<Unit> = runCatching {
        postJson(
            path = "/1/submit-listens",
            token = token,
            body = listenBrainzSubmitBody(
                listenType = "single",
                track = track,
                listenedAtEpochSeconds = listenedAtEpochSeconds,
            ),
        )
    }

    private suspend fun getJson(path: String, token: String): String = withContext(Dispatchers.IO) {
        val connection = (URL("$endpoint$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Token ${token.trim()}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ECHOAndroid")
        }
        readJson(connection)
    }

    private suspend fun postJson(path: String, token: String, body: String): String = withContext(Dispatchers.IO) {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val connection = (URL("$endpoint$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Authorization", "Token ${token.trim()}")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ECHOAndroid")
        }
        try {
            connection.outputStream.use { it.write(payload) }
            readJson(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun readJson(connection: HttpURLConnection): String {
        try {
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (connection.responseCode !in 200..299) {
                val message = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "HTTP ${connection.responseCode}"
                throw IOException(message)
            }
            return responseText.ifBlank { "{}" }
        } finally {
            connection.disconnect()
        }
    }
}

internal fun listenBrainzSubmitBody(
    listenType: String,
    track: ListenBrainzTrack,
    listenedAtEpochSeconds: Long?,
): String {
    val additional = JSONObject().put("media_player", "ECHOAndroid")
    if (track.durationMs > 0L) {
        additional.put("duration_ms", track.durationMs)
    }
    val metadata = JSONObject()
        .put("artist_name", track.artist)
        .put("track_name", track.title)
        .put("additional_info", additional)
    track.album?.takeIf { it.isNotBlank() }?.let { metadata.put("release_name", it) }
    val listen = JSONObject().put("track_metadata", metadata)
    if (listenType != "playing_now" && listenedAtEpochSeconds != null) {
        listen.put("listened_at", listenedAtEpochSeconds)
    }
    return JSONObject()
        .put("listen_type", listenType)
        .put("payload", JSONArray().put(listen))
        .toString()
}

internal fun listenBrainzUserNameFromValidateResponse(json: String): String? {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (!obj.optBoolean("valid", false)) return null
    return obj.optString("user_name").trim().takeIf { it.isNotEmpty() }
}

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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
import org.json.JSONObject

data class LastFmUiState(
    val pendingCount: Int = 0,
    val isConnecting: Boolean = false,
    @get:StringRes val lastMessageRes: Int = R.string.lastfm_disconnected,
    val lastMessageArg: String? = null,
    val lastError: String? = null,
    val lastSubmittedTrackId: String? = null,
    val webAuthPending: Boolean = false,
)

internal data class LastFmSession(
    val username: String,
    val sessionKey: String,
)

internal data class LastFmWebAuthStart(
    val token: String,
    val url: String,
)

internal data class LastFmCredentials(
    val apiKey: String,
    val sharedSecret: String,
    val sessionKey: String,
)

internal data class LastFmTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
)

private data class ActiveScrobble(
    val track: LastFmTrack,
    val startedAtEpochSeconds: Long,
    val accumulatedPlayMs: Long = 0L,
    val lastTickEpochMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val wasPlaying: Boolean = false,
    val nowPlayingSent: Boolean = false,
    val scrobbled: Boolean = false,
    val lastScrobbleAttemptEpochMs: Long = 0L,
    val lastNowPlayingAttemptEpochMs: Long = 0L,
)

internal class LastFmScrobbleController(
    private val scope: CoroutineScope,
    store: ScrobbleOutboxStore,
    private val client: LastFmClient = LastFmClient(),
) {
    private val _uiState = MutableStateFlow(LastFmUiState())
    val uiState: StateFlow<LastFmUiState> = _uiState.asStateFlow()

    private val outbox = EchoScrobbleOutbox<LastFmCredentials>(
        scope = scope,
        store = store,
        submit = { credentials, listen ->
            client.scrobble(credentials, LastFmTrack(
                listen.trackId, listen.title, listen.artist, listen.album, listen.durationMs,
            ), listen.startedAtEpochSeconds)
        },
        status = { listen, pending, error ->
            val current = _uiState.value
            _uiState.value = current.copy(
                pendingCount = pending.coerceAtLeast(0),
                lastMessageRes = when {
                    error != null -> R.string.lastfm_scrobble_failed
                    pending > 0 -> R.string.scrobble_pending
                    listen != null -> R.string.lastfm_scrobbled
                    else -> current.lastMessageRes
                },
                lastMessageArg = if (pending > 0) pending.toString() else listen?.title ?: current.lastMessageArg,
                lastError = error?.message,
                lastSubmittedTrackId = if (error == null && listen != null) listen.trackId else current.lastSubmittedTrackId,
            )
        },
    )
    private var accountFingerprint: String? = null
    private var accountCredentials: LastFmCredentials? = null
    private var nowPlayingJob: Job? = null

    fun retryPendingWhenConnected() = outbox.retryWhenConnected()

    private var settings: EchoAppSettings = EchoAppSettings()
    private var active: ActiveScrobble? = null
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
        _uiState.value = LastFmUiState(
            isConnecting = true,
            lastMessageRes = R.string.lastfm_connecting,
        )
    }

    fun setConnected(username: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            isConnecting = false,
            lastMessageRes = R.string.lastfm_connected,
            lastMessageArg = username,
            lastError = null,
            webAuthPending = false,
        )
    }

    fun setWebAuthPending() {
        active = null
        _uiState.value = LastFmUiState(
            lastMessageRes = R.string.lastfm_auth_opened,
            webAuthPending = true,
        )
    }

    fun setDisconnected() {
        nowPlayingJob?.cancel()
        outbox.configure(null)
        active = null
        _uiState.value = LastFmUiState(
            lastMessageRes = R.string.lastfm_disconnected_done,
        )
    }

    fun setError(message: String) {
        _uiState.value = LastFmUiState(
            lastMessageRes = R.string.lastfm_connect_failed,
            lastError = message,
        )
        EchoErrorLog.record(EchoErrorSource.Network, message)
    }

    fun setWebAuthError(message: String) {
        _uiState.value = LastFmUiState(
            lastMessageRes = R.string.lastfm_auth_incomplete,
            lastError = message,
            webAuthPending = true,
        )
        EchoErrorLog.record(EchoErrorSource.Network, message)
    }

    private suspend fun handleSnapshot(snapshot: EchoScrobblePlayback) {
        val credentials = settings.lastFmCredentialsOrNull()
        val fingerprint = if (credentials != accountCredentials) {
            credentials?.let { scrobbleFingerprint(it.apiKey, it.sessionKey) }
        } else accountFingerprint
        accountCredentials = credentials
        if (fingerprint != accountFingerprint || !settings.lastFmEnabled) {
            active = null
            nowPlayingJob?.cancel()
            accountFingerprint = fingerprint
        }
        outbox.configure(credentials?.let {
            ScrobbleAccount(checkNotNull(fingerprint), it, settings.lastFmEnabled)
        })
        if (
            LastFmScrobbleRules.shouldClearActiveScrobble(
                credentialsReady = settings.lastFmEnabled && credentials != null,
            )
        ) {
            active = null
            return
        }
        checkNotNull(credentials)

        val track = snapshot.status.track?.let {
            LastFmTrack(
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
                val flushed = submitScrobbleIfDue(credentials, updated)
                if (LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(snapshot.status.state)) {
                    active = null
                    return
                }
                active = updated.copy(scrobbled = updated.scrobbled || flushed)
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
                submitScrobbleIfDue(credentials, current.copy(accumulatedPlayMs = accumulated))
            }
            ActiveScrobble(
                track = track,
                startedAtEpochSeconds = if (snapshot.status.isPlaying) nowEpochMs / 1000L else 0L,
                lastTickEpochMs = if (snapshot.status.isPlaying) nowEpochMs else 0L,
                lastPositionMs = currentPositionMs,
                wasPlaying = snapshot.status.isPlaying,
            )
        } else if (
            LastFmScrobbleRules.shouldStartNewListenAfterRepeat(
                alreadyScrobbled = current.scrobbled,
                previousPositionMs = current.lastPositionMs,
                currentPositionMs = currentPositionMs,
            )
        ) {
            ActiveScrobble(
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

        var activeScrobble = active ?: return
        if (LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = activeScrobble.scrobbled,
                lastAttemptEpochMs = activeScrobble.lastScrobbleAttemptEpochMs, nowEpochMs = nowEpochMs,
            ) && LastFmScrobbleRules.shouldScrobble(
                activeScrobble.track.durationMs, activeScrobble.accumulatedPlayMs,
            )
        ) {
            val queued = submitScrobbleIfDue(credentials, activeScrobble)
            activeScrobble = activeScrobble.copy(scrobbled = queued, lastScrobbleAttemptEpochMs = nowEpochMs)
            active = activeScrobble
        }
        if (!snapshot.status.isPlaying) return

        if (!activeScrobble.nowPlayingSent &&
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = activeScrobble.nowPlayingSent,
                lastAttemptEpochMs = activeScrobble.lastNowPlayingAttemptEpochMs,
                nowEpochMs = nowEpochMs,
            )
        ) {
            val nowPlayingTrack = activeScrobble.track
            active = activeScrobble.copy(
                nowPlayingSent = true,
                lastNowPlayingAttemptEpochMs = nowEpochMs,
            )
            nowPlayingJob = scope.launch {
                client.updateNowPlaying(credentials, nowPlayingTrack)
                    .also { currentCoroutineContext().ensureActive() }
                    .onSuccess {
                        if (accountFingerprint != fingerprint || _uiState.value.pendingCount > 0) return@onSuccess
                        _uiState.value = LastFmUiState(
                            lastMessageRes = R.string.lastfm_now_playing,
                            lastMessageArg = nowPlayingTrack.title,
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
                        val message = error.message ?: "Now playing failed"
                        _uiState.value = LastFmUiState(
                            lastMessageRes = R.string.lastfm_now_playing_failed,
                            lastError = message,
                        )
                        EchoErrorLog.record(EchoErrorSource.Network, message, throwable = error)
                    }
            }
        }
    }

    private suspend fun submitScrobbleIfDue(
        credentials: LastFmCredentials,
        scrobble: ActiveScrobble,
    ): Boolean {
        if (!LastFmScrobbleRules.shouldFlushScrobbleBeforeReplacing(
                alreadyScrobbled = scrobble.scrobbled, durationMs = scrobble.track.durationMs,
                listenedMs = scrobble.accumulatedPlayMs,
            )) return false
        return try {
            outbox.enqueue(PendingScrobble(
                account = scrobbleFingerprint(credentials.apiKey, credentials.sessionKey),
                trackId = scrobble.track.id, title = scrobble.track.title, artist = scrobble.track.artist,
                album = scrobble.track.album, durationMs = scrobble.track.durationMs,
                startedAtEpochSeconds = scrobble.startedAtEpochSeconds,
            ))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.value = _uiState.value.copy(
                lastMessageRes = R.string.lastfm_scrobble_failed,
                lastError = error.message,
            )
            false
        }
    }

    private fun EchoAppSettings.lastFmCredentialsOrNull(): LastFmCredentials? {
        val apiKey = lastFmApiKey?.trim().orEmpty()
        val sharedSecret = lastFmSharedSecret?.trim().orEmpty()
        val sessionKey = lastFmSessionKey?.trim().orEmpty()
        return LastFmCredentials(
            apiKey = apiKey,
            sharedSecret = sharedSecret,
            sessionKey = sessionKey,
        ).takeIf {
            apiKey.isNotBlank() && sharedSecret.isNotBlank() && sessionKey.isNotBlank()
        }
    }
}

internal class LastFmClient(
    private val endpoint: String = "https://ws.audioscrobbler.com/2.0/",
) {
    suspend fun createWebAuthToken(apiKey: String, sharedSecret: String): Result<LastFmWebAuthStart> = runCatching {
        val normalizedApiKey = apiKey.trim()
        val params = linkedMapOf(
            "method" to "auth.getToken",
            "api_key" to normalizedApiKey,
        )
        val response = postSigned(params, sharedSecret)
        val token = response.getString("token").trim()
        LastFmWebAuthStart(
            token = token,
            url = authorizationUrl(normalizedApiKey, token),
        )
    }

    suspend fun completeWebAuth(
        apiKey: String,
        sharedSecret: String,
        token: String,
    ): Result<LastFmSession> = runCatching {
        val params = linkedMapOf(
            "method" to "auth.getSession",
            "api_key" to apiKey.trim(),
            "token" to token.trim(),
        )
        val response = postSigned(params, sharedSecret)
        val session = response.getJSONObject("session")
        LastFmSession(
            username = session.getString("name"),
            sessionKey = session.getString("key"),
        )
    }

    suspend fun authenticate(
        apiKey: String,
        sharedSecret: String,
        username: String,
        password: String,
    ): Result<LastFmSession> = runCatching {
        val params = linkedMapOf(
            "method" to "auth.getMobileSession",
            "username" to username.trim(),
            "password" to password,
            "api_key" to apiKey.trim(),
        )
        val response = postSigned(params, sharedSecret)
        val session = response.getJSONObject("session")
        LastFmSession(
            username = session.getString("name"),
            sessionKey = session.getString("key"),
        )
    }

    internal suspend fun updateNowPlaying(credentials: LastFmCredentials, track: LastFmTrack): Result<Unit> = runCatching {
        val params = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to track.artist,
            "track" to track.title,
            "api_key" to credentials.apiKey,
            "sk" to credentials.sessionKey,
        )
        track.album?.let { params["album"] = it }
        if (track.durationMs > 0L) params["duration"] = (track.durationMs / 1000L).toString()
        postSigned(params, credentials.sharedSecret).let { }
    }

    internal suspend fun scrobble(
        credentials: LastFmCredentials,
        track: LastFmTrack,
        startedAtEpochSeconds: Long,
    ): Result<Unit> = runCatching {
        val params = linkedMapOf(
            "method" to "track.scrobble",
            "artist" to track.artist,
            "track" to track.title,
            "timestamp" to startedAtEpochSeconds.toString(),
            "api_key" to credentials.apiKey,
            "sk" to credentials.sessionKey,
        )
        track.album?.let { params["album"] = it }
        val response = postSigned(params, credentials.sharedSecret)
        val scrobbles = response.optJSONObject("scrobbles")
        if (scrobbles?.optJSONObject("@attr")?.optInt("accepted", 0) != 1) {
            throw IOException("Last.fm did not accept the listen")
        }
    }

    private suspend fun postSigned(
        params: LinkedHashMap<String, String>,
        sharedSecret: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        val signedParams = LinkedHashMap(params)
        signedParams["api_sig"] = sign(params, sharedSecret.trim())
        signedParams["format"] = "json"
        val body = signedParams.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }.toByteArray(StandardCharsets.UTF_8)

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body) }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            val json = JSONObject(responseText.ifBlank { "{}" })
            if (connection.responseCode !in 200..299 || json.has("error") || json.optString("status") == "failed") {
                val message = json.optString("message").ifBlank { "HTTP ${connection.responseCode}" }
                throw IOException(message)
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun sign(params: Map<String, String>, sharedSecret: String): String {
        val signatureBase = buildString {
            params.toSortedMap().forEach { (key, value) ->
                if (key != "format" && key != "callback") {
                    append(key)
                    append(value)
                }
            }
            append(sharedSecret)
        }
        val bytes = MessageDigest.getInstance("MD5")
            .digest(signatureBase.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun authorizationUrl(apiKey: String, token: String): String =
        "https://www.last.fm/api/auth/?api_key=${apiKey.urlEncode()}&token=${token.urlEncode()}"
}

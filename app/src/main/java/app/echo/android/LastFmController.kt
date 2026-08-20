package app.echo.android

import app.echo.android.data.EchoAppSettings
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
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
    val isConnecting: Boolean = false,
    val lastMessage: String = "Last.fm 未连接",
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

private data class LastFmPlaybackSnapshot(
    val status: EchoPlaybackStatus,
    val position: PlaybackPositionState,
)

private data class ActiveScrobble(
    val track: LastFmTrack,
    val startedAtEpochSeconds: Long,
    val accumulatedPlayMs: Long = 0L,
    val lastTickEpochMs: Long = 0L,
    val wasPlaying: Boolean = false,
    val nowPlayingSent: Boolean = false,
    val scrobbled: Boolean = false,
)

internal class LastFmScrobbleController(
    private val scope: CoroutineScope,
    private val client: LastFmClient = LastFmClient(),
) {
    private val _uiState = MutableStateFlow(LastFmUiState())
    val uiState: StateFlow<LastFmUiState> = _uiState.asStateFlow()

    private var settings: EchoAppSettings = EchoAppSettings()
    private var active: ActiveScrobble? = null
    private var collectJob: Job? = null

    fun start(
        settingsFlow: Flow<EchoAppSettings>,
        playbackStatus: StateFlow<EchoPlaybackStatus>,
        playbackPosition: StateFlow<PlaybackPositionState>,
    ) {
        collectJob?.cancel()
        collectJob = scope.launch {
            combine(
                settingsFlow.distinctUntilChanged(),
                playbackStatus,
                playbackPosition,
            ) { appSettings, status, position ->
                settings = appSettings
                LastFmPlaybackSnapshot(status = status, position = position)
            }.distinctUntilChanged { previous, next ->
                previous.status.track?.id == next.status.track?.id &&
                    previous.status.isPlaying == next.status.isPlaying &&
                    (!next.status.isPlaying ||
                        previous.position.positionMs / 1_000L == next.position.positionMs / 1_000L)
            }.collect(::handleSnapshot)
        }
    }

    fun clear() {
        // Keep collecting after the UI ViewModel dies; start() replaces this job.
    }

    fun setConnecting() {
        _uiState.value = LastFmUiState(isConnecting = true, lastMessage = "Last.fm 正在连接")
    }

    fun setConnected(username: String) {
        _uiState.value = LastFmUiState(lastMessage = "Last.fm 已连接：$username")
    }

    fun setWebAuthPending() {
        active = null
        _uiState.value = LastFmUiState(
            lastMessage = "Last.fm 授权页已打开，允许后回到 ECHOAndroid 完成授权",
            webAuthPending = true,
        )
    }

    fun setDisconnected() {
        active = null
        _uiState.value = LastFmUiState(lastMessage = "Last.fm 已断开")
    }

    fun setError(message: String) {
        _uiState.value = LastFmUiState(lastMessage = "Last.fm 连接失败", lastError = message)
    }

    fun setWebAuthError(message: String) {
        _uiState.value = LastFmUiState(
            lastMessage = "Last.fm 授权未完成",
            lastError = message,
            webAuthPending = true,
        )
    }

    private fun handleSnapshot(snapshot: LastFmPlaybackSnapshot) {
        val credentials = settings.lastFmCredentialsOrNull()
        if (!settings.lastFmEnabled || credentials == null) {
            active = null
            return
        }

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
            active = null
            return
        }

        val nowEpochMs = System.currentTimeMillis()
        val current = active
        active = if (current?.track?.id != track.id) {
            ActiveScrobble(
                track = track,
                startedAtEpochSeconds = nowEpochMs / 1000L,
                lastTickEpochMs = if (snapshot.status.isPlaying) nowEpochMs else 0L,
                wasPlaying = snapshot.status.isPlaying,
            )
        } else {
            current.copy(
                track = current.track.copy(durationMs = maxOf(current.track.durationMs, track.durationMs)),
                accumulatedPlayMs = LastFmScrobbleRules.accumulatedPlayMs(
                    previouslyAccumulatedMs = current.accumulatedPlayMs,
                    wasPlaying = current.wasPlaying,
                    lastTickEpochMs = current.lastTickEpochMs,
                    nowEpochMs = nowEpochMs,
                ),
                lastTickEpochMs = if (snapshot.status.isPlaying) nowEpochMs else 0L,
                wasPlaying = snapshot.status.isPlaying,
            )
        }

        var activeScrobble = active ?: return
        if (!activeScrobble.scrobbled &&
            LastFmScrobbleRules.shouldScrobble(activeScrobble.track.durationMs, activeScrobble.accumulatedPlayMs)
        ) {
            val scrobbleTrack = activeScrobble.track
            val startedAt = activeScrobble.startedAtEpochSeconds
            activeScrobble = activeScrobble.copy(scrobbled = true)
            active = activeScrobble
            scope.launch(Dispatchers.IO) {
                client.scrobble(credentials, scrobbleTrack, startedAt)
                    .onSuccess {
                        _uiState.value = LastFmUiState(
                            lastMessage = "Last.fm 已记录：${scrobbleTrack.title}",
                            lastSubmittedTrackId = scrobbleTrack.id,
                        )
                    }
                    .onFailure { error ->
                        if (active?.track?.id == scrobbleTrack.id &&
                            !LastFmScrobbleRules.keepSubmittedFlag(false)
                        ) {
                            active = active?.copy(scrobbled = false)
                        }
                        _uiState.value = LastFmUiState(
                            lastMessage = "Last.fm scrobble 失败",
                            lastError = error.message ?: "Scrobble failed",
                        )
                    }
            }
        }
        if (!snapshot.status.isPlaying) return

        if (!activeScrobble.nowPlayingSent) {
            val nowPlayingTrack = activeScrobble.track
            active = activeScrobble.copy(nowPlayingSent = true)
            scope.launch(Dispatchers.IO) {
                client.updateNowPlaying(credentials, nowPlayingTrack)
                    .onSuccess {
                        _uiState.value = LastFmUiState(
                            lastMessage = "Last.fm 正在显示：${nowPlayingTrack.title}",
                        )
                    }
                    .onFailure { error ->
                        if (active?.track?.id == nowPlayingTrack.id &&
                            !LastFmScrobbleRules.keepSubmittedFlag(false)
                        ) {
                            active = active?.copy(nowPlayingSent = false)
                        }
                        _uiState.value = LastFmUiState(
                            lastMessage = "Last.fm 当前播放未提交",
                            lastError = error.message ?: "Now playing failed",
                        )
                    }
            }
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
        postSigned(params, credentials.sharedSecret).let { }
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
            if (connection.responseCode !in 200..299 || json.optString("status") == "failed") {
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

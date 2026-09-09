package app.echo.android

import app.echo.android.data.EchoAppSettings
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.i18n.echoText
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
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
    val isConnecting: Boolean = false,
    val lastMessage: String = echoText(
        en = "ListenBrainz is not connected",
        zh = "ListenBrainz 未连接",
        ja = "ListenBrainz 未接続",
    ),
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

private data class ListenBrainzPlaybackSnapshot(
    val status: EchoPlaybackStatus,
    val position: PlaybackPositionState,
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
    private val client: ListenBrainzClient = ListenBrainzClient(),
) {
    private val _uiState = MutableStateFlow(ListenBrainzUiState())
    val uiState: StateFlow<ListenBrainzUiState> = _uiState.asStateFlow()

    private var settings: EchoAppSettings = EchoAppSettings()
    private var active: ActiveListenBrainzListen? = null
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
                ListenBrainzPlaybackSnapshot(status = status, position = position)
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
        _uiState.value = ListenBrainzUiState(
            isConnecting = true,
            lastMessage = echoText(
                en = "ListenBrainz is connecting",
                zh = "ListenBrainz 正在连接",
                ja = "ListenBrainz に接続中",
            ),
        )
    }

    fun setConnected(userName: String? = null) {
        val current = _uiState.value
        _uiState.value = current.copy(
            isConnecting = false,
            lastMessage = if (userName.isNullOrBlank()) {
                echoText(
                    en = "ListenBrainz connected",
                    zh = "ListenBrainz 已连接",
                    ja = "ListenBrainz 接続済み",
                )
            } else {
                echoText(
                    en = "ListenBrainz connected: $userName",
                    zh = "ListenBrainz 已连接：$userName",
                    ja = "ListenBrainz 接続済み：$userName",
                )
            },
            lastError = null,
            userName = userName?.takeIf { it.isNotBlank() } ?: current.userName,
        )
    }

    fun setDisconnected() {
        active = null
        _uiState.value = ListenBrainzUiState(
            lastMessage = echoText(
                en = "ListenBrainz disconnected",
                zh = "ListenBrainz 已断开",
                ja = "ListenBrainz を切断しました",
            ),
        )
    }

    fun setError(message: String) {
        _uiState.value = ListenBrainzUiState(
            lastMessage = echoText(
                en = "ListenBrainz connection failed",
                zh = "ListenBrainz 连接失败",
                ja = "ListenBrainz の接続に失敗しました",
            ),
            lastError = message,
        )
        EchoErrorLog.record(EchoErrorSource.Network, message)
    }

    private fun handleSnapshot(snapshot: ListenBrainzPlaybackSnapshot) {
        val token = settings.listenBrainzToken?.trim().orEmpty()
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
                startedAtEpochSeconds = nowEpochMs / 1000L,
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
                startedAtEpochSeconds = nowEpochMs / 1000L,
                lastTickEpochMs = if (snapshot.status.isPlaying) nowEpochMs else 0L,
                lastPositionMs = currentPositionMs,
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
                lastPositionMs = currentPositionMs,
                wasPlaying = snapshot.status.isPlaying,
            )
        }

        var activeListen = active ?: return
        if (!activeListen.submitted &&
            LastFmScrobbleRules.shouldScrobble(activeListen.track.durationMs, activeListen.accumulatedPlayMs) &&
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = activeListen.submitted,
                lastAttemptEpochMs = activeListen.lastSubmitAttemptEpochMs,
                nowEpochMs = nowEpochMs,
            )
        ) {
            val listenTrack = activeListen.track
            val startedAt = activeListen.startedAtEpochSeconds
            activeListen = activeListen.copy(
                submitted = true,
                lastSubmitAttemptEpochMs = nowEpochMs,
            )
            active = activeListen
            scope.launch(Dispatchers.IO) {
                client.submitListen(token, listenTrack, startedAt)
                    .onSuccess {
                        _uiState.value = ListenBrainzUiState(
                            lastMessage = echoText(
                                en = "ListenBrainz recorded: ${listenTrack.title}",
                                zh = "ListenBrainz 已记录：${listenTrack.title}",
                                ja = "ListenBrainz に記録：${listenTrack.title}",
                            ),
                            userName = _uiState.value.userName,
                        )
                    }
                    .onFailure { error ->
                        if (active?.track?.id == listenTrack.id &&
                            !LastFmScrobbleRules.keepSubmittedFlag(false)
                        ) {
                            active = active?.copy(submitted = false)
                        }
                        val message = error.message ?: "ListenBrainz submit failed"
                        _uiState.value = ListenBrainzUiState(
                            lastMessage = echoText(
                                en = "ListenBrainz listen was not submitted",
                                zh = "ListenBrainz 听歌记录未提交",
                                ja = "ListenBrainz の listen を送信できませんでした",
                            ),
                            lastError = message,
                            userName = _uiState.value.userName,
                        )
                        EchoErrorLog.record(EchoErrorSource.Network, message, throwable = error)
                    }
            }
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
            scope.launch(Dispatchers.IO) {
                client.submitPlayingNow(token, nowPlayingTrack)
                    .onSuccess {
                        _uiState.value = ListenBrainzUiState(
                            lastMessage = echoText(
                                en = "ListenBrainz now playing: ${nowPlayingTrack.title}",
                                zh = "ListenBrainz 正在显示：${nowPlayingTrack.title}",
                                ja = "ListenBrainz で再生中：${nowPlayingTrack.title}",
                            ),
                            userName = _uiState.value.userName,
                        )
                    }
                    .onFailure { error ->
                        if (active?.track?.id == nowPlayingTrack.id &&
                            !LastFmScrobbleRules.keepSubmittedFlag(false)
                        ) {
                            active = active?.copy(nowPlayingSent = false)
                        }
                        val message = error.message ?: "ListenBrainz now playing failed"
                        _uiState.value = ListenBrainzUiState(
                            lastMessage = echoText(
                                en = "ListenBrainz now playing was not submitted",
                                zh = "ListenBrainz 当前播放未提交",
                                ja = "ListenBrainz の Now Playing を送信できませんでした",
                            ),
                            lastError = message,
                            userName = _uiState.value.userName,
                        )
                        EchoErrorLog.record(EchoErrorSource.Network, message, throwable = error)
                    }
            }
        }
    }

    private fun submitListenIfDue(
        token: String,
        listen: ActiveListenBrainzListen,
    ): Boolean {
        if (
            !LastFmScrobbleRules.shouldFlushScrobbleBeforeReplacing(
                alreadyScrobbled = listen.submitted,
                durationMs = listen.track.durationMs,
                listenedMs = listen.accumulatedPlayMs,
            )
        ) {
            return false
        }
        val listenTrack = listen.track
        val startedAt = listen.startedAtEpochSeconds
        scope.launch(Dispatchers.IO) {
            client.submitListen(token, listenTrack, startedAt)
                .onSuccess {
                    _uiState.value = ListenBrainzUiState(
                        lastMessage = echoText(
                            en = "ListenBrainz recorded: ${listenTrack.title}",
                            zh = "ListenBrainz 已记录：${listenTrack.title}",
                            ja = "ListenBrainz に記録：${listenTrack.title}",
                        ),
                        userName = _uiState.value.userName,
                    )
                }
                .onFailure { error ->
                    val message = error.message ?: "ListenBrainz submit failed"
                    _uiState.value = ListenBrainzUiState(
                        lastMessage = echoText(
                            en = "ListenBrainz listen was not submitted",
                            zh = "ListenBrainz 听歌记录未提交",
                            ja = "ListenBrainz の listen を送信できませんでした",
                        ),
                        lastError = message,
                        userName = _uiState.value.userName,
                    )
                    EchoErrorLog.record(EchoErrorSource.Network, message, throwable = error)
                }
        }
        return true
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

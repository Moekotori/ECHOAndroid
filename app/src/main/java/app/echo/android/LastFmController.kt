package app.echo.android

import androidx.annotation.StringRes
import app.echo.android.data.LastFmClient
import app.echo.android.data.LastFmCredentials
import app.echo.android.data.LastFmApiException
import app.echo.android.data.lastFmCredentialsOrNull
import app.echo.android.data.LastFmTrack
import app.echo.android.model.settings.LastFmActivity
import app.echo.android.data.EchoAppSettings
import app.echo.android.data.PendingScrobble
import app.echo.android.data.ScrobbleOutboxStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LastFmUiState(
    val pendingCount: Int = 0,
    val isConnecting: Boolean = false,
    @get:StringRes val lastMessageRes: Int = R.string.lastfm_disconnected,
    val lastMessageArg: String? = null,
    val lastError: String? = null,
    val lastSubmittedTrackId: String? = null,
    val webAuthPending: Boolean = false,
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
    private val invalidSession: suspend (LastFmCredentials) -> Unit = {},
) {
    private val _uiState = MutableStateFlow(LastFmUiState())
    val uiState: StateFlow<LastFmUiState> = _uiState.asStateFlow()

    private val _activity = MutableStateFlow(LastFmActivity())
    val activity: StateFlow<LastFmActivity> = _activity.asStateFlow()

    private val outbox = EchoScrobbleOutbox<LastFmCredentials>(
        scope = scope,
        store = store,
        submit = { credentials, listen ->
            client.scrobble(credentials, LastFmTrack(
                listen.trackId, listen.title, listen.artist, listen.album, listen.durationMs,
            ), listen.startedAtEpochSeconds).also { result ->
                if ((result.exceptionOrNull() as? LastFmApiException)?.requiresAuthorization == true) invalidSession(credentials)
            }
        },
        shouldRetry = { error -> error !is LastFmApiException || error.retryable || error.requiresAuthorization },
        status = { listen, pending, error ->
            val current = _uiState.value
            _activity.update { it.copy(pendingCount = pending.coerceAtLeast(0),
                lastScrobbleAt = if (listen != null && error == null) System.currentTimeMillis() else it.lastScrobbleAt) }
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
        _uiState.value = _uiState.value.copy(
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
        _uiState.value = _uiState.value.copy(
            isConnecting = false,
            lastError = null,
            lastMessageRes = R.string.lastfm_auth_opened,
            webAuthPending = true,
        )
    }

    fun clearAuthorizationState() {
        _uiState.update { it.copy(isConnecting = false, webAuthPending = false, lastError = null,
            lastMessageRes = if (it.isConnecting || it.webAuthPending) R.string.lastfm_disconnected else it.lastMessageRes) }
    }

    fun setDisconnected() {
        nowPlayingJob?.cancel()
        outbox.configure(null)
        active = null
        _activity.value = LastFmActivity()
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
            _activity.value = LastFmActivity()
            if (!_uiState.value.webAuthPending && !_uiState.value.isConnecting) {
                if (credentials != null) setConnected(settings.lastFmUsername.orEmpty()) else setDisconnected()
            }
        }
        if (!settings.lastFmOptions.nowPlayingEnabled) {
            nowPlayingJob?.cancel()
            if (active?.nowPlayingSent == true) active = active?.copy(nowPlayingSent = false)
        }
        outbox.configure(credentials?.let {
            ScrobbleAccount(checkNotNull(fingerprint), it, settings.lastFmEnabled && settings.lastFmOptions.scrobbleEnabled)
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
                    clearActiveTrack()
                    return
                }
                active = updated.copy(scrobbled = updated.scrobbled || flushed)
                return
            }
            if (LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(snapshot.status.state)) {
                active = null
                clearActiveTrack()
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
        _activity.update { it.copy(
            artist = activeScrobble.track.artist, title = activeScrobble.track.title,
            playedSeconds = activeScrobble.accumulatedPlayMs / 1000L,
            thresholdSeconds = minOf(activeScrobble.track.durationMs.takeIf { it > 0 }?.div(2L) ?: 240_000L, 240_000L) / 1000L,
            scrobbled = activeScrobble.scrobbled,
        ) }
        if (!snapshot.status.isPlaying || !settings.lastFmOptions.nowPlayingEnabled) return

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
                        if (accountFingerprint != fingerprint) return@onSuccess
                        _activity.update { it.copy(lastNowPlayingAt = System.currentTimeMillis()) }
                        if (_uiState.value.pendingCount > 0) return@onSuccess
                        _uiState.value = _uiState.value.copy(
                            lastError = null,
                            lastMessageRes = R.string.lastfm_now_playing,
                            lastMessageArg = nowPlayingTrack.title,
                        )
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        if ((error as? LastFmApiException)?.requiresAuthorization == true) invalidSession(credentials)
                        if (accountFingerprint != fingerprint || _uiState.value.pendingCount > 0) return@onFailure
                        val message = error.message ?: "Now playing failed"
                        _uiState.value = _uiState.value.copy(
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
        if (!settings.lastFmOptions.scrobbleEnabled) return false
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

    private fun clearActiveTrack() {
        _activity.update { it.copy(artist = "", title = "", playedSeconds = 0, thresholdSeconds = 0, scrobbled = false) }
    }

}

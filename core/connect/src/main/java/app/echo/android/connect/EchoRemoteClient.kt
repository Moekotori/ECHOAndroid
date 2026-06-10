package app.echo.android.connect

import app.echo.android.model.connect.EchoMobileDiscordPresenceSnapshot
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemoteMessage
import app.echo.android.model.connect.EchoRemoteStatus
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EchoRemoteClient private constructor(
    private val scope: CoroutineScope,
    private val transport: EchoLinkTransport = OkHttpEchoLinkTransport(),
) {
    constructor(scope: CoroutineScope) : this(scope, OkHttpEchoLinkTransport())

    private val _status = MutableStateFlow(EchoRemoteStatus())
    val status: StateFlow<EchoRemoteStatus> = _status.asStateFlow()

    private val _library = MutableStateFlow(EchoRemoteLibraryState())
    val library: StateFlow<EchoRemoteLibraryState> = _library.asStateFlow()

    private var endpoint: EchoRemoteEndpoint? = null
    private var statusPollJob: Job? = null

    fun connectManual(address: String, token: String, refreshLibraryOnConnect: Boolean = true) {
        val parsed = EchoPairingParser.parseManual(address, token)
        if (parsed == null) {
            _status.update {
                it.copy(
                    connectionState = EchoRemoteConnectionState.Error,
                    error = "PC 地址或配对 Token 无效",
                )
            }
            return
        }
        connect(parsed, refreshLibraryOnConnect)
    }

    fun pair(endpoint: EchoRemoteEndpoint, refreshLibraryOnConnect: Boolean = true) {
        connect(endpoint, refreshLibraryOnConnect)
    }

    fun connect(nextEndpoint: EchoRemoteEndpoint, refreshLibraryOnConnect: Boolean = true) {
        endpoint = nextEndpoint
        statusPollJob?.cancel()
        _status.update {
            it.copy(
                connectionState = EchoRemoteConnectionState.Connecting,
                endpoint = nextEndpoint,
                error = null,
            )
        }
        scope.launch {
            runCatching { transport.fetchStatus(nextEndpoint) }
                .onSuccess { response ->
                    applyStatus(nextEndpoint, response)
                    if (refreshLibraryOnConnect) {
                        refreshLibrary()
                    } else {
                        _library.value = EchoRemoteLibraryState()
                    }
                    startStatusPolling()
                }
                .onFailure { error -> markConnectionError(nextEndpoint, error) }
        }
    }

    fun disconnect() {
        statusPollJob?.cancel()
        statusPollJob = null
        endpoint = null
        _status.value = EchoRemoteStatus(mobileDiscordPresence = _status.value.mobileDiscordPresence)
        _library.value = EchoRemoteLibraryState()
    }

    fun ingest(message: EchoRemoteMessage) {
        when (message) {
            is EchoRemoteMessage.StatusSnapshot -> _status.update {
                it.copy(
                    connectionState = EchoRemoteConnectionState.Connected,
                    playback = message.payload,
                    error = null,
                )
            }

            is EchoRemoteMessage.MobileDiscordPresence -> publishMobileDiscordPresence(message.payload)

            is EchoRemoteMessage.Error -> _status.update {
                it.copy(connectionState = EchoRemoteConnectionState.Error, error = message.message)
            }

            is EchoRemoteMessage.Command,
            EchoRemoteMessage.Ping,
            EchoRemoteMessage.Pong,
            -> Unit
        }
    }

    fun publishMobileDiscordPresence(snapshot: EchoMobileDiscordPresenceSnapshot?) {
        _status.update { current ->
            current.copy(
                mobileDiscordPresence = snapshot,
                error = when {
                    snapshot?.enabled != true -> current.error
                    current.connectionState != EchoRemoteConnectionState.Connected -> "Discord Presence 等待 PC ECHO 配对"
                    else -> current.error
                },
            )
        }
    }

    fun send(command: EchoRemoteCommand) {
        val target = endpoint ?: run {
            _status.update {
                it.copy(
                    connectionState = EchoRemoteConnectionState.Error,
                    error = "还没有连接 PC ECHO",
                )
            }
            return
        }
        scope.launch {
            runCatching { transport.sendCommand(target, command) }
                .onSuccess { response ->
                    if (response != null) {
                        applyStatus(target, response)
                    } else {
                        refreshStatusOnce(target)
                    }
                }
                .onFailure { error -> markConnectionError(target, error) }
        }
    }

    fun refreshLibrary(query: String = _library.value.query) {
        val target = endpoint ?: run {
            _library.update { it.copy(isLoading = false, error = "还没有连接 PC ECHO") }
            return
        }
        _library.update { it.copy(isLoading = true, query = query, error = null) }
        scope.launch {
                    runCatching { transport.fetchTracks(target, query, PcLibraryPageSize) }
                .onSuccess { page ->
                    if (endpoint?.id == target.id) {
                        _library.value = EchoRemoteLibraryState(
                            isLoading = false,
                            query = query,
                            tracks = page.tracks,
                            totalCount = page.totalCount,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (endpoint?.id == target.id) {
                        _library.update {
                            it.copy(isLoading = false, query = query, error = error.userMessage())
                        }
                    }
                }
        }
    }

    fun playTrackOnPc(track: EchoRemoteTrack) {
        val trackId = track.id ?: run {
            _library.update { it.copy(error = "PC 曲目缺少 trackId，不能远程播放") }
            return
        }
        send(EchoRemoteCommand.PlayTrackOnPc(trackId))
    }

    fun playTrackOnPhone(
        track: EchoRemoteTrack,
        onTrackReady: (EchoTrack) -> Unit,
        onLyricsReady: (String, EchoRemoteLyrics) -> Unit = { _, _ -> },
    ) {
        val target = endpoint ?: run {
            _library.update { it.copy(error = "还没有连接 PC ECHO") }
            return
        }
        val trackId = track.id ?: run {
            _library.update { it.copy(error = "PC 曲目缺少 trackId，不能在手机播放") }
            return
        }
        _library.update { it.copy(error = null) }
        scope.launch {
            runCatching { transport.resolveStream(target, trackId) }
                .onSuccess { stream ->
                    val resolvedTrack = stream.track ?: track
                    val phoneTrack = resolvedTrack.toPhoneTrack(stream.streamUrl)
                    onTrackReady(phoneTrack)
                    resolveLyricsForPhoneTrack(target, resolvedTrack, phoneTrack.id, onLyricsReady)
                }
                .onFailure { error ->
                    _library.update { it.copy(error = error.userMessage()) }
                }
        }
    }

    private fun resolveLyricsForPhoneTrack(
        target: EchoRemoteEndpoint,
        track: EchoRemoteTrack,
        phoneTrackId: String,
        onLyricsReady: (String, EchoRemoteLyrics) -> Unit,
    ) {
        val trackId = track.id ?: return
        scope.launch {
            runCatching { transport.fetchLyrics(target, trackId) }
                .onSuccess { lyrics ->
                    if (lyrics != null && endpoint?.id == target.id) {
                        onLyricsReady(phoneTrackId, lyrics)
                    }
                }
        }
    }

    private fun startStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                delay(StatusPollIntervalMs)
                endpoint?.let { refreshStatusOnce(it) }
            }
        }
    }

    private fun refreshStatusOnce(target: EchoRemoteEndpoint) {
        scope.launch {
            runCatching { transport.fetchStatus(target) }
                .onSuccess { applyStatus(target, it) }
                .onFailure { error ->
                    if (endpoint?.id == target.id) {
                        _status.update { current ->
                            current.copy(
                                connectionState = EchoRemoteConnectionState.Reconnecting,
                                error = error.userMessage(),
                            )
                        }
                    }
                }
        }
    }

    private fun applyStatus(target: EchoRemoteEndpoint, response: EchoLinkStatusResponse) {
        if (endpoint?.id != target.id) return
        val namedEndpoint = response.deviceName
            ?.takeIf { it.isNotBlank() }
            ?.let { target.copy(name = it) }
            ?: target
        endpoint = namedEndpoint
        _status.update { current ->
            current.copy(
                connectionState = EchoRemoteConnectionState.Connected,
                endpoint = namedEndpoint,
                playback = response.playback,
                error = null,
            )
        }
    }

    private fun markConnectionError(target: EchoRemoteEndpoint, error: Throwable) {
        if (endpoint?.id != target.id) return
        _status.update { current ->
            current.copy(
                connectionState = EchoRemoteConnectionState.Error,
                endpoint = target,
                error = error.userMessage(),
            )
        }
    }

    private fun EchoRemoteTrack.toPhoneTrack(streamUrl: String): EchoTrack =
        EchoTrack(
            id = "echo-link:${id ?: streamUrl.hashCode()}",
            uri = streamUrl,
            title = title,
            artist = artist,
            album = album,
            artworkUri = artworkUrl,
            durationMs = durationMs,
            source = LibrarySource("echo-link"),
        )

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "PC ECHO 连接失败"

    private companion object {
        const val StatusPollIntervalMs = 5_000L
        const val PcLibraryPageSize = 500
    }
}

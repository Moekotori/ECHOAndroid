package app.echo.android.connect

import app.echo.android.model.connect.EchoMobileDiscordPresenceSnapshot
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemoteMessage
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteStatus
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EchoRemoteClient internal constructor(
    private val scope: CoroutineScope,
    private val transport: EchoLinkTransport = OkHttpEchoLinkTransport(),
    private val connectRetryDelayMs: Long = 500L,
) {
    constructor(scope: CoroutineScope) : this(scope, OkHttpEchoLinkTransport())

    private val _status = MutableStateFlow(EchoRemoteStatus())
    val status: StateFlow<EchoRemoteStatus> = _status.asStateFlow()

    private val _library = MutableStateFlow(EchoRemoteLibraryState())
    val library: StateFlow<EchoRemoteLibraryState> = _library.asStateFlow()

    private var endpoint: EchoRemoteEndpoint? = null
    private var connectJob: Job? = null
    private var statusPollJob: Job? = null
    private var libraryRefreshJob: Job? = null
    private var playOnPhoneGeneration = 0L
    private var connectGeneration = 0L
    private var libraryRefreshGeneration = 0L

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
        val generation = ++connectGeneration
        connectJob?.cancel()
        endpoint = nextEndpoint
        statusPollJob?.cancel()
        libraryRefreshGeneration += 1
        libraryRefreshJob?.cancel()
        libraryRefreshJob = null
        _status.update {
            it.copy(
                connectionState = EchoRemoteConnectionState.Connecting,
                endpoint = nextEndpoint,
                error = null,
            )
        }
        connectJob = scope.launch {
            var pairingAttempt = 0
            var target: EchoRemoteEndpoint? = null
            while (isActive && target == null) {
                if (!EchoLinkRequestPolicy.shouldApplyResolvedPlay(generation, connectGeneration)) {
                    return@launch
                }
                val paired = runSuspendCatching { transport.completePairing(nextEndpoint) }
                target = paired.getOrNull()
                if (target == null) {
                    pairingAttempt += 1
                    markReconnecting(nextEndpoint, paired.exceptionOrNull())
                    if (pairingAttempt >= 2) {
                        return@launch
                    }
                    delay(connectRetryDelayMs)
                }
            }
            val resolvedTarget = target ?: return@launch
            if (!EchoLinkRequestPolicy.shouldApplyResolvedPlay(generation, connectGeneration)) {
                return@launch
            }
            endpoint = resolvedTarget
            _status.update { current ->
                current.copy(endpoint = resolvedTarget)
            }

            var statusAttempt = 0
            while (isActive) {
                if (!EchoLinkRequestPolicy.shouldApplyResolvedPlay(generation, connectGeneration)) {
                    return@launch
                }
                val status = runSuspendCatching { transport.fetchStatus(resolvedTarget) }
                status.onSuccess { response ->
                    if (!EchoLinkRequestPolicy.shouldApplyResolvedPlay(generation, connectGeneration)) {
                        return@launch
                    }
                    applyStatus(resolvedTarget, response)
                    if (refreshLibraryOnConnect) {
                        refreshLibrary()
                    } else {
                        _library.value = EchoRemoteLibraryState()
                    }
                    startStatusPolling()
                    return@launch
                }
                statusAttempt += 1
                if (statusAttempt == 1) {
                    markReconnecting(resolvedTarget, status.exceptionOrNull())
                    delay(connectRetryDelayMs)
                    continue
                }
                markReconnecting(resolvedTarget, status.exceptionOrNull())
                startStatusPolling()
                return@launch
            }
        }
    }

    fun disconnect() {
        connectGeneration += 1
        connectJob?.cancel()
        connectJob = null
        statusPollJob?.cancel()
        statusPollJob = null
        libraryRefreshGeneration += 1
        libraryRefreshJob?.cancel()
        libraryRefreshJob = null
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
        _library.update { current ->
            val sameQuery = current.query.trim() == query.trim()
            current.copy(
                isLoading = true,
                query = query,
                tracks = if (sameQuery) current.tracks else emptyList(),
                playlists = if (sameQuery) current.playlists else emptyList(),
                playlistTracks = if (sameQuery) current.playlistTracks else emptyMap(),
                loadingPlaylistId = null,
                totalCount = if (sameQuery) current.totalCount else 0,
                error = null,
            )
        }
        val generation = ++libraryRefreshGeneration
        libraryRefreshJob?.cancel()
        libraryRefreshJob = scope.launch {
            runSuspendCatching {
                val trackPage = fetchAllTrackPages(target, query)
                val playlistPage = transport.fetchPlaylists(target, query, PcLibraryPageSize)
                trackPage to playlistPage
            }
                .onSuccess { (trackPage, playlistPage) ->
                    if (
                        endpoint?.id == target.id &&
                        generation == libraryRefreshGeneration
                    ) {
                        _library.value = EchoRemoteLibraryState(
                            isLoading = false,
                            query = query,
                            tracks = trackPage.tracks,
                            playlists = playlistPage.playlists,
                            playlistTracks = playlistPage.playlists
                                .filter { it.tracks.isNotEmpty() }
                                .associate { it.id to it.tracks },
                            totalCount = trackPage.totalCount,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (
                        endpoint?.id == target.id &&
                        generation == libraryRefreshGeneration
                    ) {
                        _library.update {
                            it.copy(isLoading = false, query = query, error = error.userMessage())
                        }
                    }
                }
        }
    }

    fun refreshPlaylistTracks(playlist: EchoRemotePlaylist) {
        val target = endpoint ?: run {
            _library.update { it.copy(error = "还没有连接 PC ECHO") }
            return
        }
        if (playlist.id.isBlank()) {
            _library.update { it.copy(error = "PC 歌单缺少 playlistId，不能打开") }
            return
        }
        if (playlist.tracks.isNotEmpty() || _library.value.playlistTracks.containsKey(playlist.id)) {
            _library.update { current ->
                current.copy(
                    playlistTracks = current.playlistTracks + (playlist.id to (current.playlistTracks[playlist.id] ?: playlist.tracks)),
                    loadingPlaylistId = null,
                    error = null,
                )
            }
            return
        }
        _library.update { it.copy(loadingPlaylistId = playlist.id, error = null) }
        scope.launch {
            runCatching { transport.fetchPlaylistTracks(target, playlist.id, PcPlaylistTrackPageSize) }
                .onSuccess { page ->
                    if (endpoint?.id == target.id) {
                        _library.update { current ->
                            current.copy(
                                playlistTracks = current.playlistTracks + (playlist.id to page.tracks),
                                loadingPlaylistId = null,
                                error = null,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (endpoint?.id == target.id) {
                        _library.update {
                            it.copy(loadingPlaylistId = null, error = error.userMessage())
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

    fun handoffToPc(track: EchoRemoteTrack, positionMs: Long) {
        val trackId = track.id ?: run {
            _library.update { it.copy(error = "PC 曲目缺少 trackId，不能交接播放") }
            return
        }
        send(EchoRemoteCommand.HandoffToPc(trackId, positionMs.coerceAtLeast(0L)))
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
        if (!track.canPlayOnPhone) {
            _library.update { it.copy(error = "这首歌暂时不能串流到手机") }
            return
        }
        _library.update { it.copy(error = null) }
        val generation = ++playOnPhoneGeneration
        scope.launch {
            runCatching { transport.resolveStream(target, trackId) }
                .onSuccess { stream ->
                    if (!EchoLinkRequestPolicy.shouldApplyResolvedPlay(generation, playOnPhoneGeneration)) {
                        return@onSuccess
                    }
                    if (!EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)) {
                        return@onSuccess
                    }
                    val resolvedTrack = stream.track ?: track
                    val phoneTrack = resolvedTrack.toPhoneTrack(stream.streamUrl)
                    onTrackReady(phoneTrack)
                    resolveLyricsForPhoneTrack(target, resolvedTrack, phoneTrack.id, onLyricsReady)
                }
                .onFailure { error ->
                    if (EchoLinkRequestPolicy.shouldApplyResolvedPlay(generation, playOnPhoneGeneration)) {
                        _library.update { it.copy(error = error.userMessage()) }
                    }
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

    private suspend fun fetchAllTrackPages(
        target: EchoRemoteEndpoint,
        query: String,
    ): EchoLinkTrackPage {
        val tracks = mutableListOf<EchoRemoteTrack>()
        var totalCount = 0
        var page = 1
        while (page <= MaxLibraryPages) {
            val pageResult = transport.fetchTracks(target, query, page, PcLibraryPageSize)
            totalCount = pageResult.totalCount
            if (pageResult.tracks.isEmpty()) {
                break
            }
            tracks += pageResult.tracks
            if (tracks.size >= totalCount) {
                break
            }
            page += 1
        }
        return EchoLinkTrackPage(tracks = tracks, totalCount = totalCount.coerceAtLeast(tracks.size))
    }

    private fun markReconnecting(target: EchoRemoteEndpoint, error: Throwable?) {
        if (endpoint?.id != null && endpoint?.id != target.id) return
        endpoint = target
        _status.update { current ->
            current.copy(
                connectionState = EchoRemoteConnectionState.Reconnecting,
                endpoint = target,
                error = error?.userMessage(),
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
        const val PcPlaylistTrackPageSize = 500
        const val MaxLibraryPages = 40
    }
}

private suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

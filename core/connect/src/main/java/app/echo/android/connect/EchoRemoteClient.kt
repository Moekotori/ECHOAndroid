package app.echo.android.connect

import app.echo.android.model.connect.EchoMobileDiscordPresenceSnapshot
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoLinkLibraryQueryPolicy
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemoteMessage
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteStatus
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.i18n.echoText
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.playback.EchoLinkPlaybackUri
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
    private val statusPollIntervalMs: Long = StatusPollIntervalMs,
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
    private var playlistRefreshJob: Job? = null
    private var phonePlaybackJob: Job? = null
    private var playOnPhoneGeneration = 0L
    private var connectGeneration = 0L
    private var statusRefreshGeneration = 0L
    private var libraryRefreshGeneration = 0L
    private var playlistRefreshGeneration = 0L

    fun connectManual(address: String, token: String, refreshLibraryOnConnect: Boolean = true) {
        val parsed = EchoPairingParser.parseManual(address, token)
        if (parsed == null) {
            _status.update {
                it.copy(
                    connectionState = EchoRemoteConnectionState.Error,
                    error = echoText(
                        en = "Invalid PC address or pairing token",
                        zh = "PC 地址或配对 Token 无效",
                        ja = "PC アドレスまたはペアリングトークンが無効です",
                    ),
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
        statusRefreshGeneration += 1
        libraryRefreshGeneration += 1
        libraryRefreshJob?.cancel()
        libraryRefreshJob = null
        playlistRefreshGeneration += 1
        playlistRefreshJob?.cancel()
        playlistRefreshJob = null
        playOnPhoneGeneration += 1
        phonePlaybackJob?.cancel()
        phonePlaybackJob = null
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
                    val pairingError = paired.exceptionOrNull()
                        ?: EchoLinkHttpException("PC ECHO pairing failed")
                    if (EchoLinkRequestPolicy.shouldFailPairingAfterAttempts(pairingAttempt)) {
                        markConnectionError(nextEndpoint, pairingError)
                        return@launch
                    }
                    markReconnecting(nextEndpoint, pairingError)
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
        statusRefreshGeneration += 1
        libraryRefreshGeneration += 1
        libraryRefreshJob?.cancel()
        libraryRefreshJob = null
        playlistRefreshGeneration += 1
        playlistRefreshJob?.cancel()
        playlistRefreshJob = null
        playOnPhoneGeneration += 1
        phonePlaybackJob?.cancel()
        phonePlaybackJob = null
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
                    current.connectionState != EchoRemoteConnectionState.Connected -> echoText(
                        en = "Discord Presence is waiting for a PC ECHO pairing",
                        zh = "Discord Presence 等待 PC ECHO 配对",
                        ja = "Discord Presence は PC ECHO のペアリング待ちです",
                    )
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
                    error = echoText(
                        en = "PC ECHO is not connected yet",
                        zh = "还没有连接 PC ECHO",
                        ja = "まだ PC ECHO に接続していません",
                    ),
                )
            }
            return
        }
        val generation = ++statusRefreshGeneration
        scope.launch {
            runSuspendCatching { transport.sendCommand(target, command) }
                .onSuccess { response ->
                    if (generation != statusRefreshGeneration) {
                        return@onSuccess
                    }
                    if (response != null) {
                        applyStatus(target, response)
                    } else {
                        refreshStatusOnce(target)
                    }
                }
                .onFailure { error ->
                    if (generation == statusRefreshGeneration) {
                        markConnectionError(target, error)
                    }
                }
        }
    }

    fun refreshLibrary(query: String = _library.value.query) {
        val target = endpoint ?: run {
            _library.update {
                it.copy(
                    isLoading = false,
                    error = echoText(
                        en = "PC ECHO is not connected yet",
                        zh = "还没有连接 PC ECHO",
                        ja = "まだ PC ECHO に接続していません",
                    ),
                )
            }
            return
        }
        playlistRefreshGeneration += 1
        playlistRefreshJob?.cancel()
        playlistRefreshJob = null
        _library.update { current ->
            val sameQuery = current.query.trim() == query.trim()
            current.copy(
                isLoading = true,
                query = query,
                tracks = if (sameQuery) current.tracks else emptyList(),
                playlists = if (sameQuery) current.playlists else emptyList(),
                playlistTracks = emptyMap(),
                loadingPlaylistId = null,
                totalCount = if (sameQuery) current.totalCount else 0,
                error = null,
            )
        }
        val generation = ++libraryRefreshGeneration
        libraryRefreshJob?.cancel()
        // 流式分页:首页 + 歌单到达即发布(不再等最多 40 页全部拉完才显示),
        // 后续页在后台续拉,每 PublishEveryPages 页合并发布一次,期间 isLoadingMore=true。
        libraryRefreshJob = scope.launch {
            fun isCurrentRefresh(): Boolean =
                endpoint?.id == target.id && generation == libraryRefreshGeneration

            val firstFetch = runSuspendCatching {
                val trackPage = transport.fetchTracks(target, query, page = 1, pageSize = PcLibraryPageSize)
                val playlistPage = fetchAllPlaylists(target, query)
                trackPage to playlistPage
            }
            val (firstPage, playlistPage) = firstFetch.getOrElse { error ->
                if (isCurrentRefresh()) {
                    _library.update {
                        it.copy(isLoading = false, query = query, error = error.userMessage())
                    }
                }
                return@launch
            }
            if (!isCurrentRefresh()) return@launch

            val loadedTracks = ArrayList(firstPage.tracks)
            var totalCount = firstPage.totalCount.coerceAtLeast(loadedTracks.size)
            val playlistTracks = playlistPage.playlists
                .filter { it.tracks.isNotEmpty() }
                .associate { it.id to it.tracks }

            fun publish(isLoadingMore: Boolean, error: String? = null) {
                // 流式拉取期间用户可能并发点开歌单:基于当前状态合并,保留
                // refreshPlaylistTracks 写入的曲目与 loadingPlaylistId,不能整体覆盖
                _library.update { current ->
                    current.copy(
                        isLoading = false,
                        isLoadingMore = isLoadingMore,
                        query = query,
                        tracks = loadedTracks.toList(),
                        playlists = playlistPage.playlists,
                        playlistTracks = playlistTracks + current.playlistTracks,
                        totalCount = totalCount,
                        error = error ?: current.error,
                    )
                }
            }

            var hasMore = firstPage.tracks.isNotEmpty() && loadedTracks.size < totalCount
            publish(isLoadingMore = hasMore)

            var page = 2
            var pagesSincePublish = 0
            while (hasMore && page <= MaxLibraryPages) {
                val pageResult = runSuspendCatching {
                    transport.fetchTracks(target, query, page, PcLibraryPageSize)
                }.getOrElse { error ->
                    if (isCurrentRefresh()) {
                        publish(isLoadingMore = false, error = error.userMessage())
                    }
                    return@launch
                }
                if (!isCurrentRefresh()) return@launch
                if (pageResult.tracks.isEmpty()) break
                loadedTracks += pageResult.tracks
                totalCount = pageResult.totalCount.coerceAtLeast(loadedTracks.size)
                hasMore = loadedTracks.size < totalCount
                pagesSincePublish += 1
                if (pagesSincePublish >= PublishEveryPages && hasMore) {
                    publish(isLoadingMore = true)
                    pagesSincePublish = 0
                }
                page += 1
            }
            if (isCurrentRefresh()) {
                publish(isLoadingMore = false)
            }
        }
    }

    fun refreshPlaylistTracks(playlist: EchoRemotePlaylist) {
        val generation = ++playlistRefreshGeneration
        playlistRefreshJob?.cancel()
        playlistRefreshJob = null
        val target = endpoint ?: run {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "PC ECHO is not connected yet",
                        zh = "还没有连接 PC ECHO",
                        ja = "まだ PC ECHO に接続していません",
                    ),
                )
            }
            return
        }
        if (playlist.id.isBlank()) {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "This PC playlist is missing a playlistId and cannot be opened",
                        zh = "PC 歌单缺少 playlistId，不能打开",
                        ja = "この PC プレイリストには playlistId がないため開けません",
                    ),
                )
            }
            return
        }
        val knownTracks = _library.value.playlistTracks[playlist.id] ?: playlist.tracks
        if (
            !EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(
                knownTrackCount = knownTracks.size,
                declaredTrackCount = playlist.trackCount,
            )
        ) {
            _library.update { current ->
                current.copy(
                    playlistTracks = current.playlistTracks + (playlist.id to knownTracks),
                    loadingPlaylistId = null,
                    error = null,
                )
            }
            return
        }
        _library.update { it.copy(loadingPlaylistId = playlist.id, error = null) }
        playlistRefreshJob = scope.launch {
            runSuspendCatching { fetchAllPlaylistTracks(target, playlist.id) }
                .onSuccess { page ->
                    if (
                        endpoint?.id == target.id &&
                        generation == playlistRefreshGeneration
                    ) {
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
                    if (
                        endpoint?.id == target.id &&
                        generation == playlistRefreshGeneration
                    ) {
                        _library.update {
                            it.copy(loadingPlaylistId = null, error = error.userMessage())
                        }
                    }
                }
        }
    }

    fun playTrackOnPc(track: EchoRemoteTrack) {
        val trackId = track.id ?: run {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "This PC track is missing a trackId and cannot be played remotely",
                        zh = "PC 曲目缺少 trackId，不能远程播放",
                        ja = "この PC トラックには trackId がないためリモート再生できません",
                    ),
                )
            }
            return
        }
        send(EchoRemoteCommand.PlayTrackOnPc(trackId))
    }

    fun handoffToPc(track: EchoRemoteTrack, positionMs: Long) {
        val trackId = track.id ?: run {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "This PC track is missing a trackId and cannot be handed off",
                        zh = "PC 曲目缺少 trackId，不能交接播放",
                        ja = "この PC トラックには trackId がないため引き継ぎできません",
                    ),
                )
            }
            return
        }
        send(EchoRemoteCommand.HandoffToPc(trackId, positionMs.coerceAtLeast(0L)))
    }

    fun playTrackOnPhone(
        track: EchoRemoteTrack,
        onTrackReady: (EchoTrack) -> Unit,
        onLyricsReady: (String, EchoRemoteLyrics) -> Unit = { _, _ -> },
    ) {
        playTracksOnPhone(
            tracks = listOf(track),
            startIndex = 0,
            onQueueReady = { queue, _ ->
                queue.firstOrNull()?.let(onTrackReady)
            },
            onLyricsReady = onLyricsReady,
        )
    }

    fun playTracksOnPhone(
        tracks: List<EchoRemoteTrack>,
        startIndex: Int,
        onQueueReady: (List<EchoTrack>, Int) -> Unit,
        onLyricsReady: (String, EchoRemoteLyrics) -> Unit = { _, _ -> },
    ) {
        val target = endpoint ?: run {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "PC ECHO is not connected yet",
                        zh = "还没有连接 PC ECHO",
                        ja = "まだ PC ECHO に接続していません",
                    ),
                )
            }
            return
        }
        val generation = ++playOnPhoneGeneration
        phonePlaybackJob?.cancel()
        val requested = tracks.getOrNull(startIndex)
        val playable = EchoLinkLibraryQueryPolicy.playableLinkedPhoneTracks(tracks)
        if (requested?.id.isNullOrBlank() || !requested.canPlayOnPhone) {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "This track cannot be streamed to the phone right now",
                        zh = "这首歌暂时不能串流到手机",
                        ja = "この曲は今スマホへストリーミングできません",
                    ),
                )
            }
            return
        }
        _library.update { it.copy(error = null) }
        val requestedId = requireNotNull(requested.id)
        phonePlaybackJob = scope.launch {
            val resolved = runSuspendCatching {
                val stream = transport.resolveStream(target, requestedId)
                playable.map { track ->
                    track.toPhonePlaybackTrack(
                        if (track.id == requestedId) stream.streamUrl
                        else EchoLinkPlaybackUri.persistUri(requireNotNull(track.id)),
                    )
                }
            }
            if (!EchoLinkRequestPolicy.shouldApplyResolvedPlay(generation, playOnPhoneGeneration)) {
                return@launch
            }
            if (!EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)) {
                return@launch
            }
            resolved.onSuccess { queue ->
                if (queue.isEmpty()) {
                    _library.update {
                        it.copy(
                            error = echoText(
                                en = "This track cannot be streamed to the phone right now",
                                zh = "这首歌暂时不能串流到手机",
                                ja = "この曲は今スマホへストリーミングできません",
                            ),
                        )
                    }
                    return@onSuccess
                }
                val start = queue.indexOfFirst { EchoLinkPlaybackUri.trackIdFromMediaId(it.id) == requestedId }
                check(start >= 0) { "Selected PC track is missing from the playback queue" }
                onQueueReady(queue, start)
                val lyrics = runSuspendCatching { transport.fetchLyrics(target, requestedId) }.getOrNull()
                if (lyrics != null && generation == playOnPhoneGeneration &&
                    EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)) {
                    onLyricsReady(queue[start].id, lyrics)
                }
            }
                .onFailure { error ->
                    if (
                        EchoLinkRequestPolicy.shouldApplyResolvedPlay(generation, playOnPhoneGeneration) &&
                        EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)
                    ) {
                        _library.update { it.copy(error = error.userMessage()) }
                    }
                }
        }
    }

    private suspend fun fetchAllPlaylists(target: EchoRemoteEndpoint, query: String): EchoLinkPlaylistPage {
        val items = mutableListOf<EchoRemotePlaylist>()
        var page = 1
        while (true) {
            val batch = transport.fetchPlaylists(target, query, page, PcLibraryPageSize)
            items += batch.playlists
            if (batch.playlists.isEmpty() || items.size >= batch.totalCount) {
                return EchoLinkPlaylistPage(items, batch.totalCount)
            }
            check(page++ < MaxLibraryPages) { "PC playlist list exceeds the supported page limit" }
        }
    }

    private suspend fun fetchAllPlaylistTracks(target: EchoRemoteEndpoint, id: String): EchoLinkTrackPage {
        val items = mutableListOf<EchoRemoteTrack>()
        var page = 1
        while (true) {
            val batch = transport.fetchPlaylistTracks(target, id, page, PcPlaylistTrackPageSize)
            items += batch.tracks
            if (batch.tracks.isEmpty() || items.size >= batch.totalCount) {
                return EchoLinkTrackPage(items, batch.totalCount)
            }
            check(page++ < MaxLibraryPages) { "PC playlist exceeds the supported page limit" }
        }
    }

    private fun startStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                delay(statusPollIntervalMs)
                endpoint?.let { refreshStatusOnce(it) }
            }
        }
    }

    private suspend fun refreshStatusOnce(target: EchoRemoteEndpoint) {
        val generation = ++statusRefreshGeneration
        runSuspendCatching { transport.fetchStatus(target) }
            .onSuccess { response ->
                if (generation == statusRefreshGeneration) {
                    applyStatus(target, response)
                }
            }
            .onFailure { error ->
                if (
                    endpoint?.id == target.id &&
                    generation == statusRefreshGeneration
                ) {
                    _status.update { current ->
                        current.copy(
                            connectionState = EchoRemoteConnectionState.Reconnecting,
                            error = error.userMessage(),
                        )
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

    suspend fun resolvePhoneStreamUrl(trackId: String): String? {
        val target = endpoint ?: return null
        return runSuspendCatching { transport.resolveStream(target, trackId) }
            .getOrNull()
            ?.streamUrl
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun fetchLyrics(trackId: String): EchoRemoteLyrics? {
        val target = endpoint ?: return null
        if (trackId.isBlank()) return null
        return runSuspendCatching { transport.fetchLyrics(target, trackId) }.getOrNull()
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: echoText(
            en = "PC ECHO connection failed",
            zh = "PC ECHO 连接失败",
            ja = "PC ECHO の接続に失敗しました",
        )

    private companion object {
        const val StatusPollIntervalMs = 5_000L
        const val PcLibraryPageSize = 500
        const val PcPlaylistTrackPageSize = 500
        const val MaxLibraryPages = 40

        // 流式拉取时每拉取多少页向 UI 合并发布一次,限制下游 catalog 重建次数
        const val PublishEveryPages = 2
    }
}

internal fun EchoRemoteTrack.toPhonePlaybackTrack(streamUrl: String): EchoTrack {
    val trackId = id?.takeIf { it.isNotBlank() } ?: streamUrl.hashCode().toString()
    return EchoTrack(
        id = EchoLinkPlaybackUri.mediaId(trackId),
        uri = streamUrl,
        title = title,
        artist = artist,
        album = album,
        artworkUri = artworkUrl,
        durationMs = durationMs,
        source = LibrarySource("echo-link"),
    )
}

private suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteAlbum
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoLinkLibraryQueryPolicy
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemoteMessage
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteStatus
import app.echo.android.model.connect.EchoRemoteStreamItem
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

    private var refreshOnForeground = false
    private var foreground = true
    private var pollFailures = 0
    private var authRejected = false

    fun setForeground(visible: Boolean) {
        foreground = visible
        if (!visible) {
            stopEventStream()
            statusPollJob?.cancel()
            if (libraryRefreshJob?.isActive == true) {
                refreshOnForeground = true
                libraryRefreshJob?.cancel()
                _library.update { it.copy(isLoading = false, isLoadingMore = false) }
            }
        } else if (endpoint != null && !authRejected) {
            if (connectJob?.isActive != true) startRealtimeStatus()
            if (refreshOnForeground && _status.value.connectionState == EchoRemoteConnectionState.Connected) {
                refreshOnForeground = false
                refreshLibrary()
            }
        }
    }

    private var endpoint: EchoRemoteEndpoint? = null
    private var connectJob: Job? = null
    private var statusPollJob: Job? = null
    private var eventsJob: Job? = null
    private var eventSubscription: EchoLinkEventSubscription? = null
    private var eventStreamActive = false
    private var libraryRefreshJob: Job? = null
    private var playlistRefreshJob: Job? = null
    private var albumRefreshJob: Job? = null
    private var folderRefreshJob: Job? = null
    private var phonePlaybackJob: Job? = null
    private var playOnPhoneGeneration = 0L
    private var connectGeneration = 0L
    private var statusRefreshGeneration = 0L
    private var libraryRefreshGeneration = 0L
    private var playlistRefreshGeneration = 0L
    private var albumRefreshGeneration = 0L
    private var folderRefreshGeneration = 0L

    fun connectManual(
        address: String,
        token: String,
        refreshLibraryOnConnect: Boolean = true,
        supportsV2Events: Boolean = false,
    ) {
        val parsed = EchoPairingParser.parseManual(address, token)
            ?.copy(supportsV2Events = supportsV2Events)
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
        authRejected = false
        refreshOnForeground = false
        pollFailures = 0
        if (!EchoLinkRequestPolicy.isSameEndpoint(endpoint, nextEndpoint)) _library.value = EchoRemoteLibraryState()
        val generation = ++connectGeneration
        connectJob?.cancel()
        endpoint = nextEndpoint
        stopEventStream()
        statusPollJob?.cancel()
        statusRefreshGeneration += 1
        libraryRefreshGeneration += 1
        libraryRefreshJob?.cancel()
        libraryRefreshJob = null
        playlistRefreshGeneration += 1
        playlistRefreshJob?.cancel()
        playlistRefreshJob = null
        albumRefreshGeneration += 1
        albumRefreshJob?.cancel()
        albumRefreshJob = null
        folderRefreshGeneration += 1
        folderRefreshJob?.cancel()
        folderRefreshJob = null
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
                    refreshOnForeground = refreshLibraryOnConnect && !foreground
                    if (refreshLibraryOnConnect && foreground) {
                        refreshLibrary()
                    } else {
                        _library.value = EchoRemoteLibraryState()
                    }
                    startRealtimeStatus()
                    return@launch
                }
                if (rejectAuthentication(resolvedTarget, status.exceptionOrNull())) return@launch
                statusAttempt += 1
                if (statusAttempt == 1) {
                    markReconnecting(resolvedTarget, status.exceptionOrNull())
                    delay(connectRetryDelayMs)
                    continue
                }
                markReconnecting(resolvedTarget, status.exceptionOrNull())
                startRealtimeStatus()
                return@launch
            }
        }
    }

    fun disconnect() {
        refreshOnForeground = false
        connectGeneration += 1
        connectJob?.cancel()
        connectJob = null
        stopEventStream()
        statusPollJob?.cancel()
        statusPollJob = null
        statusRefreshGeneration += 1
        libraryRefreshGeneration += 1
        libraryRefreshJob?.cancel()
        libraryRefreshJob = null
        playlistRefreshGeneration += 1
        playlistRefreshJob?.cancel()
        playlistRefreshJob = null
        albumRefreshGeneration += 1
        albumRefreshJob?.cancel()
        albumRefreshJob = null
        folderRefreshGeneration += 1
        folderRefreshJob?.cancel()
        folderRefreshJob = null
        playOnPhoneGeneration += 1
        phonePlaybackJob?.cancel()
        phonePlaybackJob = null
        endpoint = null
        _status.value = EchoRemoteStatus()
        _library.value = EchoRemoteLibraryState()
    }

    fun ingest(message: EchoRemoteMessage) {
        when (message) {
            is EchoRemoteMessage.StatusSnapshot -> {
                val target = endpoint ?: return
                applyStatus(
                    target,
                    EchoLinkStatusResponse(deviceName = target.name, playback = message.payload),
                )
            }

            is EchoRemoteMessage.Error -> _status.update {
                it.copy(connectionState = EchoRemoteConnectionState.Error, error = message.message)
            }

            is EchoRemoteMessage.Command,
            EchoRemoteMessage.Ping,
            EchoRemoteMessage.Pong,
            -> Unit
        }
    }

    fun send(command: EchoRemoteCommand, onSuccess: () -> Unit = {}) {
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
        scope.launch {
            dispatchCommand(target, command, onSuccess)
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
        albumRefreshGeneration += 1
        albumRefreshJob?.cancel()
        albumRefreshJob = null
        _library.update { current ->
            val sameQuery = current.query.trim() == query.trim()
            val keepTracks = sameQuery ||
                EchoLinkLibraryQueryPolicy.shouldKeepLoadedTracksForQuery(query, current.tracks.size)
            current.copy(
                isLoading = current.tracks.isEmpty(),
                isLoadingMore = keepTracks && !sameQuery,
                query = query,
                tracks = if (keepTracks) current.tracks else emptyList(),
                albums = if (sameQuery) current.albums else emptyList(),
                albumTracks = emptyMap(),
                loadingAlbumId = null,
                playlists = if (sameQuery) current.playlists else emptyList(),
                playlistTracks = emptyMap(),
                loadingPlaylistId = null,
                totalCount = if (keepTracks) current.totalCount else 0,
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

            launch {
                runSuspendCatching { fetchAllPlaylists(target, query) }
                    .onSuccess { page ->
                        if (isCurrentRefresh()) _library.update { it.copy(playlists = page.playlists) }
                    }
                    .onFailure { error ->
                        if (isCurrentRefresh()) _library.update { it.copy(error = error.userMessage()) }
                    }
            }
            if (!_library.value.albumsUnavailable) {
                launch {
                    runSuspendCatching { fetchAllAlbums(target, query) }
                        .onSuccess { page ->
                            if (isCurrentRefresh()) {
                                _library.update {
                                    it.copy(albums = page.albums, albumsUnavailable = false)
                                }
                            }
                        }
                        .onFailure { error ->
                            if (isCurrentRefresh()) {
                                _library.update { current ->
                                    current.copy(
                                        albums = emptyList(),
                                        albumsUnavailable = EchoLinkRequestPolicy.shouldMarkAlbumsUnavailable(
                                            collectionNotFound = error.isEchoLinkNotFound(),
                                        ),
                                        error = if (error.isEchoLinkNotFound()) current.error else error.userMessage(),
                                    )
                                }
                            }
                        }
                }
            }
            val firstPage = runSuspendCatching {
                transport.fetchTracks(target, query, page = 1, pageSize = PcLibraryPageSize)
            }.getOrElse { error ->
                if (isCurrentRefresh()) _library.update { it.copy(isLoading = false, isLoadingMore = false, error = error.userMessage()) }
                return@launch
            }
            if (!isCurrentRefresh()) return@launch

            val previousTracks = _library.value.tracks
            val keepPrevious = EchoLinkLibraryQueryPolicy.shouldKeepPreviousTracksOnEmptyRemotePage(
                query = query,
                remoteTrackCount = firstPage.tracks.size,
                previousTrackCount = previousTracks.size,
            )
            val loadedTracks = ArrayList(if (keepPrevious) previousTracks else firstPage.tracks)
            var totalCount = if (keepPrevious) {
                _library.value.totalCount.coerceAtLeast(loadedTracks.size)
            } else {
                firstPage.totalCount.coerceAtLeast(loadedTracks.size)
            }
            fun publish(isLoadingMore: Boolean, error: String? = null) {
                // 流式拉取期间用户可能并发点开歌单:基于当前状态合并,保留
                // refreshPlaylistTracks 写入的曲目与 loadingPlaylistId,不能整体覆盖
                _library.update { current ->
                    current.copy(
                        isLoading = false,
                        isLoadingMore = isLoadingMore,
                        query = query,
                        tracks = loadedTracks.toList(),
                        totalCount = totalCount,
                        error = error ?: current.error,
                    )
                }
            }

            var hasMore = !keepPrevious && firstPage.tracks.isNotEmpty() && loadedTracks.size < totalCount
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
                publish(isLoadingMore = false, error = if (loadedTracks.size < totalCount) echoText(
                    en = "Loaded ${loadedTracks.size} of $totalCount tracks. Search to narrow the library.",
                    zh = "已加载 ${loadedTracks.size}/$totalCount 首；请搜索以缩小曲库范围。",
                    ja = "$totalCount 曲中 ${loadedTracks.size} 曲を表示中。検索で絞り込んでください。",
                ) else null)
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

    fun refreshAlbumTracks(album: EchoRemoteAlbum) {
        val generation = ++albumRefreshGeneration
        albumRefreshJob?.cancel()
        albumRefreshJob = null
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
        if (album.id.isBlank()) {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "This PC album is missing an albumId and cannot be opened",
                        zh = "PC 专辑缺少 albumId，不能打开",
                        ja = "この PC アルバムには albumId がないため開けません",
                    ),
                )
            }
            return
        }
        val knownTracks = _library.value.albumTracks[album.id] ?: album.tracks
        if (
            !EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(
                knownTrackCount = knownTracks.size,
                declaredTrackCount = album.trackCount,
            )
        ) {
            _library.update { current ->
                current.copy(
                    albumTracks = current.albumTracks + (album.id to knownTracks),
                    loadingAlbumId = null,
                    error = null,
                )
            }
            return
        }
        _library.update { it.copy(loadingAlbumId = album.id, error = null) }
        albumRefreshJob = scope.launch {
            runSuspendCatching { fetchAllAlbumTracks(target, album.id) }
                .onSuccess { page ->
                    if (endpoint?.id == target.id && generation == albumRefreshGeneration) {
                        _library.update { current ->
                            current.copy(
                                albumTracks = current.albumTracks + (album.id to page.tracks),
                                loadingAlbumId = null,
                                error = null,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (endpoint?.id == target.id && generation == albumRefreshGeneration) {
                        _library.update {
                            it.copy(
                                loadingAlbumId = null,
                                error = error.userMessage(),
                            )
                        }
                    }
                }
        }
    }

    fun refreshFolders(path: String = _library.value.folderPath) {
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
        if (_library.value.foldersUnavailable) return
        val generation = ++folderRefreshGeneration
        folderRefreshJob?.cancel()
        val normalizedPath = path.trim().trim('/')
        _library.update {
            it.copy(
                folderPath = normalizedPath,
                loadingFolderPath = normalizedPath,
                error = null,
            )
        }
        folderRefreshJob = scope.launch {
            runSuspendCatching { transport.fetchFolders(target, normalizedPath) }
                .onSuccess { page ->
                    if (endpoint?.id == target.id && generation == folderRefreshGeneration) {
                        _library.update { current ->
                            current.copy(
                                folders = page.folders,
                                folderPath = page.path.ifBlank { normalizedPath },
                                folderTracks = page.tracks,
                                foldersUnavailable = false,
                                loadingFolderPath = null,
                                error = null,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (endpoint?.id == target.id && generation == folderRefreshGeneration) {
                        val missing = error.isEchoLinkNotFound()
                        val collectionMissing = EchoLinkRequestPolicy.shouldMarkFoldersUnavailable(
                            path = normalizedPath,
                            notFound = missing,
                        )
                        _library.update { current ->
                            current.copy(
                                folders = if (collectionMissing) emptyList() else current.folders,
                                folderTracks = if (collectionMissing) emptyList() else current.folderTracks,
                                foldersUnavailable = current.foldersUnavailable || collectionMissing,
                                loadingFolderPath = null,
                                error = if (collectionMissing) current.error else error.userMessage(),
                            )
                        }
                    }
                }
        }
    }

    fun playQueueOnPc(tracks: List<EchoRemoteTrack>, startIndex: Int = 0) {
        val ids = EchoLinkLibraryQueryPolicy.playableLinkedPcTrackIds(tracks)
        val startId = EchoLinkLibraryQueryPolicy.queueReplaceStartId(
            trackIds = ids,
            requestedId = tracks.getOrNull(startIndex)?.id,
        )
        if (ids.isEmpty() || startId == null) {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "This PC queue has no playable track IDs",
                        zh = "这个 PC 队列没有可播放的 trackId",
                        ja = "この PC キューには再生できる trackId がありません",
                    ),
                )
            }
            return
        }
        send(EchoRemoteCommand.QueueReplace(trackIds = ids, startTrackId = startId))
    }

    fun handoffPhoneQueueToPc(
        tracks: List<EchoRemoteTrack>,
        startIndex: Int,
        positionMs: Long,
        onFailure: (Throwable?) -> Unit = {},
        onSuccess: () -> Unit = {},
    ) {
        val startTrack = tracks.getOrNull(startIndex) ?: tracks.firstOrNull()
        val trackId = startTrack?.id?.takeIf { it.isNotBlank() } ?: run {
            _library.update {
                it.copy(
                    error = echoText(
                        en = "This PC track is missing a trackId and cannot be handed off",
                        zh = "PC 曲目缺少 trackId，不能交接播放",
                        ja = "この PC トラックには trackId がないため引き継ぎできません",
                    ),
                )
            }
            onFailure(null)
            return
        }
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
            onFailure(null)
            return
        }
        val ids = EchoLinkLibraryQueryPolicy.playableLinkedPcTrackIds(tracks)
        val startId = EchoLinkLibraryQueryPolicy.queueReplaceStartId(ids, trackId) ?: trackId
        val connection = connectGeneration
        scope.launch {
            if (ids.size > 1) {
                val replaced = dispatchCommand(
                    target = target,
                    command = EchoRemoteCommand.QueueReplace(trackIds = ids, startTrackId = startId),
                    onFailure = onFailure,
                )
                if (!replaced || connection != connectGeneration) return@launch
            }
            dispatchCommand(
                target = target,
                command = EchoRemoteCommand.HandoffToPc(trackId, positionMs.coerceAtLeast(0L)),
                onSuccess = onSuccess,
                onFailure = onFailure,
            )
        }
    }

    fun castRemoteQueueToPc(
        items: List<EchoRemoteStreamItem>,
        startIndex: Int,
        positionMs: Long,
        onFailure: (Throwable?) -> Unit = {},
        onSuccess: () -> Unit = {},
    ) {
        val startItem = items.getOrNull(startIndex) ?: items.firstOrNull()
        if (startItem == null || startItem.streamUrl.isBlank()) {
            val message = echoText(
                en = "There is no local file that can be sent to PC",
                zh = "没有可投送到电脑的本机文件",
                ja = "PC に送れるローカルファイルがありません",
            )
            _library.update { it.copy(error = message) }
            _status.update { it.copy(error = message) }
            onFailure(null)
            return
        }
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
            onFailure(null)
            return
        }
        val connection = connectGeneration
        val safePosition = positionMs.coerceAtLeast(0L)
        scope.launch {
            val castFailure: (Throwable) -> Unit = { error ->
                applyCastFailure(error)
                onFailure(error)
            }
            if (items.size > 1) {
                val replaced = dispatchCommand(
                    target = target,
                    command = EchoRemoteCommand.QueueReplaceRemote(
                        items = items,
                        startTrackId = startItem.id,
                    ),
                    onFailure = castFailure,
                )
                if (!replaced || connection != connectGeneration) return@launch
            }
            dispatchCommand(
                target = target,
                command = EchoRemoteCommand.PlayRemoteStream(
                    streamUrl = startItem.streamUrl,
                    positionMs = safePosition,
                    track = startItem.toRemoteTrack(),
                ),
                onSuccess = onSuccess,
                onFailure = castFailure,
            )
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

    fun handoffToPc(track: EchoRemoteTrack, positionMs: Long, onSuccess: () -> Unit = {}) {
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
        send(EchoRemoteCommand.HandoffToPc(trackId, positionMs.coerceAtLeast(0L)), onSuccess)
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

    private suspend fun fetchAllAlbums(target: EchoRemoteEndpoint, query: String): EchoLinkAlbumPage {
        val items = mutableListOf<EchoRemoteAlbum>()
        var page = 1
        while (true) {
            val batch = transport.fetchAlbums(target, query, page, PcLibraryPageSize)
            items += batch.albums
            if (batch.albums.isEmpty() || items.size >= batch.totalCount) {
                return EchoLinkAlbumPage(items, batch.totalCount.coerceAtLeast(items.size))
            }
            check(page++ < MaxLibraryPages) { "PC album list exceeds the supported page limit" }
        }
    }

    private suspend fun fetchAllAlbumTracks(target: EchoRemoteEndpoint, id: String): EchoLinkTrackPage {
        val items = mutableListOf<EchoRemoteTrack>()
        var page = 1
        while (true) {
            val batch = transport.fetchAlbumTracks(target, id, page, PcPlaylistTrackPageSize)
            items += batch.tracks
            if (batch.tracks.isEmpty() || items.size >= batch.totalCount) {
                return EchoLinkTrackPage(items, batch.totalCount.coerceAtLeast(items.size))
            }
            check(page++ < MaxLibraryPages) { "PC album exceeds the supported page limit" }
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

    private suspend fun dispatchCommand(
        target: EchoRemoteEndpoint,
        command: EchoRemoteCommand,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ): Boolean {
        val generation = ++statusRefreshGeneration
        val connection = connectGeneration
        val result = runSuspendCatching { transport.sendCommand(target, command) }
        result.onSuccess { response ->
            if (connection != connectGeneration || !EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)) {
                return@onSuccess
            }
            onSuccess()
            if (generation != statusRefreshGeneration) return@onSuccess
            if (response != null) {
                applyStatus(target, response)
            } else {
                refreshStatusOnce(target)
            }
        }.onFailure { error ->
            if (connection != connectGeneration) return@onFailure
            val statusCode = (error as? EchoLinkHttpException)?.statusCode
            if (EchoLinkRequestPolicy.shouldDisconnectOnCommandFailure(statusCode)) {
                rejectAuthentication(target, error)
                onFailure(error)
                return@onFailure
            }
            if (generation == statusRefreshGeneration) {
                _status.update { current ->
                    current.copy(error = error.userMessage())
                }
                _library.update { current ->
                    current.copy(error = error.userMessage())
                }
            }
            onFailure(error)
        }
        return result.isSuccess &&
            connection == connectGeneration &&
            EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)
    }

    private fun applyCastFailure(error: Throwable) {
        val message = if (EchoLinkCastPolicy.isUnsupportedRemoteStreamCommand(error)) {
            echoText(
                en = "This PC ECHO build cannot receive a phone stream yet. Update ECHOSteam.",
                zh = "这台电脑的 ECHOSteam 还不支持接收手机串流，请升级后再投送。",
                ja = "この PC の ECHOSteam はスマホからのキャストに未対応です。アップデートしてください。",
            )
        } else {
            error.userMessage()
        }
        _status.update { current -> current.copy(error = message) }
        _library.update { current -> current.copy(error = message) }
    }

    private fun startRealtimeStatus() {
        val target = endpoint ?: return
        if (!foreground || authRejected) return
        if (target.supportsV2Events) {
            startEventStream(target)
            startStatusPolling(SseHeartbeatPollIntervalMs)
        } else {
            stopEventStream()
            startStatusPolling(statusPollIntervalMs)
        }
    }

    private fun startEventStream(target: EchoRemoteEndpoint) {
        eventsJob?.cancel()
        eventSubscription?.cancel()
        eventSubscription = null
        eventStreamActive = false
        eventsJob = scope.launch {
            val ticket = runSuspendCatching { transport.createEventTicket(target) }
            val resolved = ticket.getOrNull()
            if (resolved == null) {
                if (rejectAuthentication(target, ticket.exceptionOrNull())) return@launch
                startStatusPolling(statusPollIntervalMs)
                return@launch
            }
            if (!isActive || !foreground || authRejected || endpoint?.id != target.id) return@launch
            eventStreamActive = true
            eventSubscription = transport.subscribeEvents(
                endpoint = target,
                ticket = resolved,
                onEvent = { message ->
                    if (endpoint?.id == target.id && !authRejected) ingest(message)
                },
                onClosed = { error ->
                    eventStreamActive = false
                    if (error != null && rejectAuthentication(target, error)) return@subscribeEvents
                    if (foreground && !authRejected && endpoint?.id == target.id) {
                        startStatusPolling(statusPollIntervalMs)
                    }
                },
            )
        }
    }

    private fun stopEventStream() {
        eventsJob?.cancel()
        eventsJob = null
        eventSubscription?.cancel()
        eventSubscription = null
        eventStreamActive = false
    }

    private fun startStatusPolling(intervalMs: Long = statusPollIntervalMs) {
        statusPollJob?.cancel()
        if (!foreground || authRejected) return
        statusPollJob = scope.launch {
            while (isActive && foreground && !authRejected) {
                delay((intervalMs * (1L shl pollFailures.coerceAtMost(4))).coerceAtMost(60_000L))
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
                    if (rejectAuthentication(target, error)) return@onFailure
                    pollFailures += 1
                    _status.update { current ->
                        current.copy(
                            connectionState = EchoRemoteConnectionState.Reconnecting,
                            error = error.userMessage(),
                        )
                    }
                }
            }
    }

    private fun rejectAuthentication(target: EchoRemoteEndpoint, error: Throwable?): Boolean {
        if ((error as? EchoLinkHttpException)?.statusCode !in listOf(401, 403)) return false
        authRejected = true
        markConnectionError(target, EchoLinkHttpException(echoText(
            en = "PC authorization expired or was revoked. Pair again.",
            zh = "PC 授权已失效或被撤销，请重新配对。",
            ja = "PC の認証が失効しました。再ペアリングしてください。",
        )))
        return true
    }

    private fun applyStatus(target: EchoRemoteEndpoint, response: EchoLinkStatusResponse) {
        if (endpoint?.id != target.id) return
        pollFailures = 0
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
        const val SseHeartbeatPollIntervalMs = 30_000L
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

private fun Throwable.isEchoLinkNotFound(): Boolean =
    (this as? EchoLinkHttpException)?.statusCode == 404

private suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

package app.echo.android.connect

import android.content.Context
import androidx.annotation.StringRes
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
    private val appContext: Context? = null,
    private val eventRetryDelayMs: Long = 1_000L,
) {
    constructor(scope: CoroutineScope) : this(scope, OkHttpEchoLinkTransport())
    constructor(scope: CoroutineScope, context: Context) : this(scope, OkHttpEchoLinkTransport(), appContext = context)

    private fun text(@StringRes id: Int, vararg args: Any): String {
        val ctx = appContext ?: return "error"
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    private val _status = MutableStateFlow(EchoRemoteStatus())
    val status: StateFlow<EchoRemoteStatus> = _status.asStateFlow()

    private val _library = MutableStateFlow(EchoRemoteLibraryState())
    val library: StateFlow<EchoRemoteLibraryState> = _library.asStateFlow()
    private val queueBrowser = EchoLinkRemoteQueue(scope, transport, { endpoint },
        { _status.value.playback.queue }, { it.userMessage() })
    val remoteQueue = queueBrowser.state
    fun isPhoneLibraryUnsupported(error: Throwable): Boolean =
        (error as? EchoLinkHttpException)?.statusCode in listOf(404, 405, 501)

    suspend fun registerPhoneLibrary(target: app.echo.android.model.connect.EchoRemoteEndpoint,
        registration: app.echo.android.model.connect.EchoPhoneLibraryRegistration) = transport.registerPhoneLibrary(target, registration)

    suspend fun unregisterPhoneLibrary(target: app.echo.android.model.connect.EchoRemoteEndpoint, sessionId: String) =
        transport.unregisterPhoneLibrary(target, sessionId)

    fun refreshQueue() { queueBrowser.startWatching(); queueBrowser.refresh() }
    fun loadMoreQueue() = queueBrowser.loadMore()
    fun loadPreviousQueue() = queueBrowser.loadPrevious()
    fun cancelQueueRequests() = queueBrowser.stopWatching()
    fun setQueueVisibleAnchor(index: Int) = queueBrowser.setVisibleAnchor(index)
    fun moveQueueItem(queueId: String, toIndex: Int) = queueBrowser.move(queueId, toIndex)
    fun confirmQueueSelection(queueId: String) = queueBrowser.confirmSelection(queueId)

    private var refreshOnForeground = false
    private var foreground = true
    private var pollFailures = 0
    private var authRejected = false

    fun setForeground(visible: Boolean) {
        if (foreground == visible) return
        foreground = visible
        if (!visible) {
            stopEventStream()
            statusPollJob?.cancel()
            foregroundRefreshJob?.cancel()
            statusRefreshGeneration += 1
            if (libraryRefreshJob?.isActive == true || collectionLoadActive()) {
                refreshOnForeground = true
                libraryRefreshJob?.cancel()
                cancelCollectionLoads()
                _library.update { it.copy(isLoading = false, isLoadingMore = false) }
            }
        } else if (endpoint != null && !authRejected) {
            if (connectJob?.isActive != true) startRealtimeStatus(refreshImmediately = true)
            if (refreshOnForeground && _status.value.connectionState == EchoRemoteConnectionState.Connected) {
                refreshOnForeground = false
                refreshLibrary()
            }
        }
    }

    private var endpoint: EchoRemoteEndpoint? = null
    suspend fun searchTracksSnapshot(query: String): List<app.echo.android.model.connect.EchoRemoteTrack> {
        val target = endpoint ?: return emptyList()
        check(status.value.connectionState == app.echo.android.model.connect.EchoRemoteConnectionState.Connected)
        val result = transport.fetchTracks(target, query.trim(), 1, 30).tracks
        check(endpoint?.id == target.id)
        return result
    }
    fun librarySyncSession(): EchoLibrarySyncSession {
        val target = endpoint ?: error("PC is not connected")
        if (target.token.isBlank()) throw app.echo.android.model.connect.EchoSyncPairingRequiredException()
        fun assertCurrent() {
            check(endpoint?.id == target.id && endpoint?.token == target.token &&
                status.value.connectionState == app.echo.android.model.connect.EchoRemoteConnectionState.Connected)
        }
        return object : EchoLibrarySyncSession {
            override suspend fun snapshot(key: String): app.echo.android.model.connect.EchoSyncState { assertCurrent(); return transport.syncSnapshot(target,key) }
            override suspend fun replace(expected: String,desired: app.echo.android.model.connect.EchoSyncState): app.echo.android.model.connect.EchoSyncApplyResult { assertCurrent(); return transport.syncReplace(target,expected,desired) }
            override suspend fun collections(): List<app.echo.android.model.connect.EchoSyncCollection> {
                assertCurrent(); return transport.syncCollections(target)
            }
            override suspend fun exportBatch(collection: app.echo.android.model.connect.EchoSyncCollection, offset: Int): app.echo.android.model.connect.EchoSyncBatch {
                assertCurrent(); return transport.syncExportBatch(target, collection, offset)
            }
            override suspend fun importBatch(batch: app.echo.android.model.connect.EchoSyncBatch, preview: Boolean): app.echo.android.model.connect.EchoSyncBatchResult {
                assertCurrent(); return transport.syncImportBatch(target, batch, preview)
            }
        }
    }
    private var connectJob: Job? = null
    private var statusPollJob: Job? = null
    private var foregroundRefreshJob: Job? = null
    private var eventSession: EchoLinkEventSession? = null
    private var pollingIntervalMs = statusPollIntervalMs
    private var libraryRefreshJob: Job? = null
    private var playlistRefreshJob: Job? = null
    private var albumRefreshJob: Job? = null
    private var trackLoadMoreJob: Job? = null
    private var albumLoadMoreJob: Job? = null
    private var playlistLoadMoreJob: Job? = null
    private var nextTrackPage = 0
    private var nextAlbumPage = 0
    private var nextPlaylistPage = 0
    private var folderRefreshJob: Job? = null
    private var phonePlaybackJob: Job? = null
    private var playOnPhoneGeneration = 0L
    private var connectGeneration = 0L
    private val streamCacheLock = Any()
    private val streamCache = object : LinkedHashMap<String, CachedEchoLinkStream>(
        EchoLinkStreamCachePolicy.MaxEntries,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedEchoLinkStream>?): Boolean =
            size > EchoLinkStreamCachePolicy.MaxEntries
    }
    private var statusRefreshGeneration = 0L
    private var commandGeneration = 0L
    private var lastCommandError: String? = null
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
                    error = text(R.string.connect_invalid_pairing),
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
        if (appContext != null && echoAddressNeedsLocalNetworkAccess("${nextEndpoint.scheme}://${if (':' in nextEndpoint.host) "[${nextEndpoint.host}]" else nextEndpoint.host}:${nextEndpoint.port}") &&
            !appContext.hasEchoLocalNetworkAccess()) {
            disconnect()
            _status.update { it.copy(connectionState = EchoRemoteConnectionState.Error,
                error = text(R.string.connect_local_network_permission)) }
            return
        }
        authRejected = false
        refreshOnForeground = false
        pollFailures = 0
        if (!EchoLinkRequestPolicy.isSameEndpoint(endpoint, nextEndpoint)) {
            _library.value = EchoRemoteLibraryState()
            clearStreamCache()
        }
        val generation = ++connectGeneration
        queueBrowser.cancel(clear = true)
        lastCommandError = null
        connectJob?.cancel()
        endpoint = nextEndpoint
        stopEventStream()
        statusPollJob?.cancel()
        statusRefreshGeneration += 1
        libraryRefreshGeneration += 1
        foregroundRefreshJob?.cancel()
        libraryRefreshJob?.cancel()
        libraryRefreshJob = null
        cancelCollectionLoads()
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
        queueBrowser.cancel(clear = true)
        lastCommandError = null
        refreshOnForeground = false
        connectGeneration += 1
        connectJob?.cancel()
        connectJob = null
        stopEventStream()
        statusPollJob?.cancel()
        statusPollJob = null
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = null
        statusRefreshGeneration += 1
        libraryRefreshGeneration += 1
        libraryRefreshJob?.cancel()
        libraryRefreshJob = null
        cancelCollectionLoads()
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
        clearStreamCache()
        _status.value = EchoRemoteStatus()
        _library.value = EchoRemoteLibraryState()
    }

    fun ingest(message: EchoRemoteMessage) {
        when (message) {
            is EchoRemoteMessage.StatusSnapshot -> {
                val target = endpoint ?: return
                val playback = mergeEchoLinkSnapshot(_status.value.playback, message)
                statusRefreshGeneration += 1
                applyStatus(
                    target,
                    EchoLinkStatusResponse(deviceName = target.name, playback = playback),
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
        if (command is EchoRemoteCommand.SetVolume && !_status.value.playback.volumeControlEnabled) {
            _status.update { it.copy(error = text(R.string.connect_volume_locked)) }
            return
        }
        val target = endpoint ?: run {
            _status.update {
                it.copy(
                    connectionState = EchoRemoteConnectionState.Error,
                    error = text(R.string.connect_not_connected),
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
                    error = text(R.string.connect_not_connected),
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
        val generation = ++libraryRefreshGeneration
        cancelCollectionLoads()
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
                albumTotalCount = if (sameQuery) current.albumTotalCount else 0,
                albumTracks = emptyMap(),
                loadingAlbumId = null,
                playlists = if (sameQuery) current.playlists else emptyList(),
                playlistTotalCount = if (sameQuery) current.playlistTotalCount else 0,
                playlistTracks = emptyMap(),
                loadingPlaylistId = null,
                totalCount = if (keepTracks) current.totalCount else 0,
                error = null,
            )
        }
        libraryRefreshJob?.cancel()
        // 只发布第一页。后面的页等列表滚到末尾再取，避免整库进内存。
        libraryRefreshJob = scope.launch {
            fun isCurrentRefresh(): Boolean =
                endpoint?.id == target.id && generation == libraryRefreshGeneration

            launch {
                runSuspendCatching {
                    transport.fetchPlaylists(target, query, page = 1, pageSize = PcLibraryPageSize)
                }.onSuccess { page ->
                    if (!isCurrentRefresh()) return@onSuccess
                    nextPlaylistPage = if (page.playlists.size < page.totalCount) 2 else 0
                    _library.update {
                        it.copy(
                            playlists = page.playlists,
                            playlistTotalCount = page.totalCount.coerceAtLeast(page.playlists.size),
                        )
                    }
                }.onFailure { error ->
                    if (isCurrentRefresh()) _library.update { it.copy(error = error.userMessage()) }
                }
            }
            if (!_library.value.albumsUnavailable) {
                launch {
                    runSuspendCatching {
                        transport.fetchAlbums(target, query, page = 1, pageSize = PcLibraryPageSize)
                    }.onSuccess { page ->
                        if (!isCurrentRefresh()) return@onSuccess
                        nextAlbumPage = if (page.albums.size < page.totalCount) 2 else 0
                        _library.update {
                            it.copy(
                                albums = page.albums,
                                albumTotalCount = page.totalCount.coerceAtLeast(page.albums.size),
                                albumsUnavailable = false,
                            )
                        }
                    }.onFailure { error ->
                        if (isCurrentRefresh()) {
                            _library.update { current ->
                                current.copy(
                                    albums = emptyList(),
                                    albumTotalCount = 0,
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
            val loadedTracks = if (keepPrevious) previousTracks else firstPage.tracks
            val totalCount = if (keepPrevious) {
                _library.value.totalCount.coerceAtLeast(loadedTracks.size)
            } else {
                firstPage.totalCount.coerceAtLeast(loadedTracks.size)
            }
            nextTrackPage = if (!keepPrevious && loadedTracks.size < totalCount) 2 else 0
            // 保留并发打开的歌单曲目，不能把整个曲库状态覆盖掉。
            _library.update { current ->
                current.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    query = query,
                    tracks = loadedTracks,
                    totalCount = totalCount,
                )
            }
        }
    }

    fun loadMoreTracks() {
        loadMoreCollection(
            nextPage = nextTrackPage,
            loaded = _library.value.tracks.size,
            total = _library.value.totalCount,
            active = trackLoadMoreJob,
            onJob = { trackLoadMoreJob = it },
            onAdvance = { nextTrackPage = it },
            fetch = { target, query, page ->
                transport.fetchTracks(target, query, page, PcLibraryPageSize)
            },
            append = { current, page ->
                val tracks = current.tracks + page.tracks
                current.copy(
                    tracks = tracks,
                    totalCount = page.totalCount.coerceAtLeast(tracks.size),
                )
            },
            itemCount = { page -> page.tracks.size },
            isComplete = { state -> state.tracks.size >= state.totalCount },
        )
    }

    fun loadMoreAlbums() {
        if (_library.value.albumsUnavailable) return
        loadMoreCollection(
            nextPage = nextAlbumPage,
            loaded = _library.value.albums.size,
            total = _library.value.albumTotalCount,
            active = albumLoadMoreJob,
            onJob = { albumLoadMoreJob = it },
            onAdvance = { nextAlbumPage = it },
            fetch = { target, query, page ->
                transport.fetchAlbums(target, query, page, PcLibraryPageSize)
            },
            append = { current, page ->
                val albums = current.albums + page.albums
                current.copy(
                    albums = albums,
                    albumTotalCount = page.totalCount.coerceAtLeast(albums.size),
                )
            },
            itemCount = { page -> page.albums.size },
            isComplete = { state -> state.albums.size >= state.albumTotalCount },
        )
    }

    fun loadMorePlaylists() {
        loadMoreCollection(
            nextPage = nextPlaylistPage,
            loaded = _library.value.playlists.size,
            total = _library.value.playlistTotalCount,
            active = playlistLoadMoreJob,
            onJob = { playlistLoadMoreJob = it },
            onAdvance = { nextPlaylistPage = it },
            fetch = { target, query, page ->
                transport.fetchPlaylists(target, query, page, PcLibraryPageSize)
            },
            append = { current, page ->
                val playlists = current.playlists + page.playlists
                current.copy(
                    playlists = playlists,
                    playlistTotalCount = page.totalCount.coerceAtLeast(playlists.size),
                )
            },
            itemCount = { page -> page.playlists.size },
            isComplete = { state -> state.playlists.size >= state.playlistTotalCount },
        )
    }

    private fun <T> loadMoreCollection(
        nextPage: Int,
        loaded: Int,
        total: Int,
        active: Job?,
        onJob: (Job?) -> Unit,
        onAdvance: (Int) -> Unit,
        fetch: suspend (EchoRemoteEndpoint, String, Int) -> T,
        append: (EchoRemoteLibraryState, T) -> EchoRemoteLibraryState,
        itemCount: (T) -> Int,
        isComplete: (EchoRemoteLibraryState) -> Boolean,
    ) {
        if (nextPage < 2 || active?.isActive == true) return
        if (total in 1..loaded) {
            onAdvance(0)
            return
        }
        val target = endpoint ?: return
        if (nextPage > MaxLibraryPages) {
            onAdvance(0)
            _library.update {
                it.copy(
                    isLoadingMore = false,
                    error = text(R.string.connect_library_partial, loaded, total.coerceAtLeast(loaded)),
                )
            }
            return
        }
        val generation = libraryRefreshGeneration
        val query = _library.value.query
        onJob(scope.launch {
            _library.update { it.copy(isLoadingMore = true) }
            val result = runSuspendCatching { fetch(target, query, nextPage) }
            if (endpoint?.id != target.id || generation != libraryRefreshGeneration) return@launch
            result.onSuccess { page ->
                if (itemCount(page) <= 0) {
                    onAdvance(0)
                    _library.update { it.copy(isLoadingMore = false) }
                    return@onSuccess
                }
                if (endpoint?.id != target.id || generation != libraryRefreshGeneration) return@onSuccess
                _library.update { current -> append(current, page).copy(isLoadingMore = false) }
                if (generation != libraryRefreshGeneration) return@onSuccess
                onAdvance(if (isComplete(_library.value)) 0 else nextPage + 1)
            }.onFailure { error ->
                onAdvance(0)
                _library.update { it.copy(isLoadingMore = false, error = error.userMessage()) }
            }
        })
    }

    private fun collectionLoadActive(): Boolean =
        trackLoadMoreJob?.isActive == true ||
            albumLoadMoreJob?.isActive == true ||
            playlistLoadMoreJob?.isActive == true

    private fun cancelCollectionLoads() {
        trackLoadMoreJob?.cancel()
        trackLoadMoreJob = null
        albumLoadMoreJob?.cancel()
        albumLoadMoreJob = null
        playlistLoadMoreJob?.cancel()
        playlistLoadMoreJob = null
        nextTrackPage = 0
        nextAlbumPage = 0
        nextPlaylistPage = 0
    }

    fun refreshPlaylistTracks(playlist: EchoRemotePlaylist) {
        val generation = ++playlistRefreshGeneration
        playlistRefreshJob?.cancel()
        playlistRefreshJob = null
        val target = endpoint ?: run {
            _library.update {
                it.copy(
                    error = text(R.string.connect_not_connected),
                )
            }
            return
        }
        if (playlist.id.isBlank()) {
            _library.update {
                it.copy(
                    error = text(R.string.connect_playlist_missing_id),
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
                    error = text(R.string.connect_not_connected),
                )
            }
            return
        }
        if (album.id.isBlank()) {
            _library.update {
                it.copy(
                    error = text(R.string.connect_album_missing_id),
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
                    error = text(R.string.connect_not_connected),
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
                    error = text(R.string.connect_queue_no_ids),
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
                    error = text(R.string.connect_track_missing_id_handoff),
                )
            }
            onFailure(null)
            return
        }
        val target = endpoint ?: run {
            _status.update {
                it.copy(
                    connectionState = EchoRemoteConnectionState.Error,
                    error = text(R.string.connect_not_connected),
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
            val message = text(R.string.connect_no_local_cast_file)
            _library.update { it.copy(error = message) }
            _status.update { it.copy(error = message) }
            onFailure(null)
            return
        }
        val target = endpoint ?: run {
            _status.update {
                it.copy(
                    connectionState = EchoRemoteConnectionState.Error,
                    error = text(R.string.connect_not_connected),
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
                val atomic = _status.value.playback.supportsAtomicPhoneQueue
                val replaced = dispatchCommand(
                    target = target,
                    command = EchoRemoteCommand.QueueReplaceRemote(
                        items = items,
                        startTrackId = startItem.id,
                        positionMs = if (atomic) safePosition else 0L,
                        startIndex = if (atomic) (if (startIndex in items.indices) startIndex else 0) else null,
                    ),
                    onSuccess = { if (atomic) onSuccess() },
                    onFailure = castFailure,
                )
                if (!replaced || connection != connectGeneration) return@launch
                if (atomic) return@launch
            }
            dispatchCommand(
                target = target,
                command = EchoRemoteCommand.PlayRemoteStream(
                    streamUrl = startItem.streamUrl,
                    positionMs = safePosition,
                    track = startItem.toRemoteTrack(),
                    audio = startItem.audio,
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
                    error = text(R.string.connect_track_missing_id_remote),
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
                    error = text(R.string.connect_track_missing_id_handoff),
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

    /** Invalidate an in-flight stream resolve without disconnecting or clearing the PC library. */
    fun cancelPhonePlaybackRequest() {
        playOnPhoneGeneration += 1
        phonePlaybackJob?.cancel()
        phonePlaybackJob = null
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
                    error = text(R.string.connect_not_connected),
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
                    error = text(R.string.connect_stream_unavailable),
                )
            }
            return
        }
        _library.update { it.copy(error = null) }
        val requestedId = requireNotNull(requested.id)
        phonePlaybackJob = scope.launch {
            val resolved = runSuspendCatching {
                val stream = resolveCachedStream(target, requestedId)
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
                            error = text(R.string.connect_stream_unavailable),
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
        val commandRequest = ++commandGeneration
        lastCommandError = null
        _status.update { it.copy(error = null) }
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
            if (commandRequest == commandGeneration) {
                lastCommandError = error.userMessage()
                _status.update { current ->
                    current.copy(error = lastCommandError)
                }
                _library.update { current ->
                    current.copy(error = lastCommandError)
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
            text(R.string.connect_cast_unsupported)
        } else {
            error.userMessage()
        }
        _status.update { current -> current.copy(error = message) }
        _library.update { current -> current.copy(error = message) }
    }

    private fun startRealtimeStatus(refreshImmediately: Boolean = false) {
        val target = endpoint ?: return
        if (!foreground || authRejected) return
        if (refreshImmediately) {
            foregroundRefreshJob?.cancel()
            foregroundRefreshJob = scope.launch { refreshStatusOnce(target) }
        }
        if (target.supportsV2Events) {
            startStatusPolling(statusPollIntervalMs)
            startEventStream(target)
        } else {
            stopEventStream()
            startStatusPolling(statusPollIntervalMs)
        }
    }

    private fun startEventStream(target: EchoRemoteEndpoint) {
        stopEventStream()
        val connection = connectGeneration
        fun isCurrent(): Boolean = foreground && !authRejected &&
            connection == connectGeneration && EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)
        eventSession = EchoLinkEventSession(
            scope, transport, eventRetryDelayMs,
            onConnected = { if (isCurrent()) startStatusPolling(SseHeartbeatPollIntervalMs) },
            onEvent = { if (isCurrent()) ingest(it) },
            onFailure = { error ->
                if (!isCurrent() || rejectAuthentication(target, error)) {
                    false
                } else {
                    startStatusPolling(statusPollIntervalMs)
                    true
                }
            },
        ).also { it.start(target) }
    }

    private fun stopEventStream() {
        eventSession?.stop()
        eventSession = null
    }

    private fun startStatusPolling(intervalMs: Long = statusPollIntervalMs) {
        if (statusPollJob?.isActive == true && pollingIntervalMs == intervalMs) return
        statusPollJob?.cancel()
        if (!foreground || authRejected) return
        pollingIntervalMs = intervalMs
        statusPollJob = scope.launch {
            while (isActive && foreground && !authRejected) {
                delay((intervalMs * (1L shl pollFailures.coerceAtMost(4))).coerceAtMost(60_000L))
                if (foregroundRefreshJob?.isActive != true) endpoint?.let { refreshStatusOnce(it) }
            }
        }
    }

    private suspend fun refreshStatusOnce(target: EchoRemoteEndpoint) {
        val connection = connectGeneration
        val generation = ++statusRefreshGeneration
        runSuspendCatching { transport.fetchStatus(target) }
            .onSuccess { response ->
                if (connection == connectGeneration && generation == statusRefreshGeneration &&
                    EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)) {
                    applyStatus(target, response)
                }
            }
            .onFailure { error ->
                if (
                    connection == connectGeneration && EchoLinkRequestPolicy.isSameEndpoint(endpoint, target) &&
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
        statusPollJob?.cancel()
        foregroundRefreshJob?.cancel()
        stopEventStream()
        val message = if (target.token.isBlank()) R.string.connect_direct_unavailable else R.string.connect_auth_expired
        markConnectionError(target, EchoLinkHttpException(text(message)))
        clearStreamCache()
        return true
    }

    private fun applyStatus(target: EchoRemoteEndpoint, response: EchoLinkStatusResponse) {
        if (authRejected || !EchoLinkRequestPolicy.isSameEndpoint(endpoint, target)) return
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
                error = lastCommandError,
            )
        }
        if (response.playback.queueIdentityAvailable) {
            queueBrowser.observe(response.playback.queue.revision, response.playback.queue.currentQueueId)
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
        if (trackId.isBlank()) return null
        return runSuspendCatching { resolveCachedStream(target, trackId) }
            .getOrNull()
            ?.streamUrl
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveCachedStream(
        target: EchoRemoteEndpoint,
        trackId: String,
    ): EchoLinkStreamResponse {
        val key = EchoLinkStreamCachePolicy.cacheKey(
            EchoLinkRequestPolicy.endpointIdentity(target),
            trackId,
        )
        val now = System.currentTimeMillis()
        val cached = synchronized(streamCacheLock) { streamCache[key] }
        if (cached != null && EchoLinkStreamCachePolicy.isFresh(cached.expiresAtEpochMs, now)) {
            return EchoLinkStreamResponse(
                streamUrl = cached.streamUrl,
                track = null,
                expiresAtEpochMs = cached.expiresAtEpochMs,
            )
        }
        val stream = transport.resolveStream(target, trackId)
        rememberStream(key, stream)
        return stream
    }

    private fun rememberStream(key: String, stream: EchoLinkStreamResponse) {
        val url = stream.streamUrl.takeIf { it.isNotBlank() } ?: return
        val expiresAt = stream.expiresAtEpochMs
        if (!EchoLinkStreamCachePolicy.shouldCache(expiresAt)) return
        synchronized(streamCacheLock) {
            streamCache[key] = CachedEchoLinkStream(
                streamUrl = url,
                expiresAtEpochMs = requireNotNull(expiresAt),
            )
        }
    }

    private fun clearStreamCache() {
        synchronized(streamCacheLock) { streamCache.clear() }
    }

    suspend fun fetchLyrics(trackId: String): EchoRemoteLyrics? {
        val target = endpoint ?: return null
        if (trackId.isBlank()) return null
        return runSuspendCatching { transport.fetchLyrics(target, trackId) }.getOrNull()
    }

    private fun Throwable.userMessage(): String = when {
        this !is EchoLinkHttpException -> message?.takeIf { it.isNotBlank() } ?: text(R.string.connect_failed)
        message == "fixed_volume" -> text(R.string.connect_volume_locked)
        message == "playback_action_unavailable" -> text(R.string.connect_playback_unavailable)
        message == "main_window_unavailable" || message == "main_window_playback_controller_unavailable" -> text(R.string.connect_pc_player_unavailable)
        message == "main_window_playback_command_timeout" -> text(R.string.connect_command_timeout)
        message == "unknown_command" || message == "unsupported_playback_action" -> text(R.string.connect_action_update_pc)
        message == "playback_queue_session_conflict" -> text(R.string.connect_queue_changed)
        else -> message?.takeIf { it.isNotBlank() } ?: text(R.string.connect_failed)
    }

    private companion object {
        const val StatusPollIntervalMs = 5_000L
        const val SseHeartbeatPollIntervalMs = 30_000L
        const val PcLibraryPageSize = 500
        const val PcPlaylistTrackPageSize = 500
        const val MaxLibraryPages = 40
    }
}

private data class CachedEchoLinkStream(
    val streamUrl: String,
    val expiresAtEpochMs: Long,
)

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
        source = LibrarySource.EchoLink,
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

package app.echo.android

import app.echo.android.connect.hasEchoLocalNetworkAccess
import android.app.Application
import android.os.SystemClock
import app.echo.android.connect.EchoCastMediaStatus
import app.echo.android.connect.EchoChromecastCastPolicy
import app.echo.android.connect.EchoChromecastClient
import app.echo.android.connect.EchoDlnaCastPolicy
import app.echo.android.connect.EchoDlnaClient
import app.echo.android.connect.EchoDlnaException
import app.echo.android.connect.EchoLinkCastFormat
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.connect.EchoLinkCastPublication
import app.echo.android.connect.EchoLinkCastServer
import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.connect.EchoLanCastFollowPolicy
import app.echo.android.connect.EchoLinkLanAddresses
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLanRendererKind
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteStreamItem
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.playback.PlaybackPositionState
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Process-owned connection: activity recreation must not replace playback credentials. */
class EchoLinkSession(private val application: Application) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
            EchoErrorLog.recordUncaught(error)
        },
    )
    val client = EchoRemoteClient(scope, application).apply { setForeground(false) }
    val phoneLibrary = EchoPhoneLibrarySession(application, client, scope)
    val playbackRouter = EchoLinkPlaybackRouter(client)
    private val castPeerHost = AtomicReference<String?>(null)
    val castServer = EchoLinkCastServer(
        openBody = EchoLinkCastMediaOpener(application.contentResolver),
        allowedPeerHost = { castPeerHost.get() ?: client.status.value.endpoint?.host },
    )
    private val dlnaClient = EchoDlnaClient()
    private val chromecastClient = EchoChromecastClient()
    private val settings = EchoSettingsStore(application)
    private var persistedKey: Pair<String?, String>? = null
    private var attemptedKey: Pair<String?, String>? = null
    private val localNetworkAccess = MutableStateFlow(application.hasEchoLocalNetworkAccess())

    fun refreshLocalNetworkAccess() {
        val granted = application.hasEchoLocalNetworkAccess()
        if (localNetworkAccess.value == granted) return
        if (!granted) {
            val endpoint = client.status.value.endpoint
            if (endpoint != null && app.echo.android.connect.echoAddressNeedsLocalNetworkAccess(
                    EchoLinkCastPolicy.advertisedBaseUrl(endpoint.host, endpoint.port))) {
                attemptedKey = null
                client.disconnect()
            }
            stopLocalCast()
        }
        localNetworkAccess.value = granted
    }
    private val _castActive = MutableStateFlow(false)
    val castActive: StateFlow<Boolean> = _castActive.asStateFlow()
    private val _castTargetName = MutableStateFlow<String?>(null)
    val castTargetName: StateFlow<String?> = _castTargetName.asStateFlow()
    private val _dlnaRenderer = MutableStateFlow<EchoLanRenderer?>(null)
    val dlnaRenderer: StateFlow<EchoLanRenderer?> = _dlnaRenderer.asStateFlow()
    private var dlnaItems: List<EchoRemoteStreamItem> = emptyList()
    private var dlnaSources: List<EchoLinkCastSourceTrack> = emptyList()
    private var dlnaIndex: Int = 0
    private var dlnaPaused: Boolean = false
    private val _castPlayback = MutableStateFlow<EchoLanCastPlayback?>(null)
    /** 投送到 DLNA / Chromecast 时远端的播放状态；未投送时为 null。 */
    val castPlayback: StateFlow<EchoLanCastPlayback?> = _castPlayback.asStateFlow()
    private val _castPosition = MutableStateFlow(PlaybackPositionState())
    val castPosition: StateFlow<PlaybackPositionState> = _castPosition.asStateFlow()
    private var followJob: Job? = null
    private var lastAdvanceElapsedMs: Long = 0L
    private var castSampledPositionMs: Long = 0L
    private var castSampledAtElapsedMs: Long = 0L

    init {
        chromecastClient.onMediaStatus = { status -> scope.launch { onChromecastStatus(status) } }
        chromecastClient.onVolume = { level -> scope.launch { _castPlayback.value = _castPlayback.value?.copy(volume = level) } }
    }

    init {
        scope.launch {
            kotlinx.coroutines.flow.combine(settings.appSettings, localNetworkAccess) { saved, allowed -> saved to allowed }.collect { (saved, allowed) ->
                val key = saved.echoLinkPcAddress to saved.echoLinkPcToken.orEmpty()
                if (!saved.echoLinkAutoReconnectEnabled) {
                    attemptedKey = null
                } else if ((allowed || !app.echo.android.connect.echoAddressNeedsLocalNetworkAccess(key.first.orEmpty())) &&
                    key != attemptedKey && !key.first.isNullOrBlank()) {
                    attemptedKey = key
                    client.connectManual(
                        address = key.first!!,
                        token = key.second,
                        refreshLibraryOnConnect = saved.echoLinkPreferLinkedLibrary,
                        supportsV2Events = saved.echoLinkV2Events,
                    )
                }
            }
        }
        scope.launch {
            var lastConnectionError: String? = null
            client.status.collect { status ->
                phoneLibrary.connectionChanged(status.endpoint, status.connectionState == EchoRemoteConnectionState.Connected)
                val endpoint = status.endpoint
                if (status.connectionState == EchoRemoteConnectionState.Connected && endpoint != null && !endpoint.needsV2PairExchange) {
                    val address = "${endpoint.scheme}://${if (':' in endpoint.host) "[${endpoint.host}]" else endpoint.host}:${endpoint.port}"
                    attemptedKey = address to endpoint.token
                    if (persistedKey != attemptedKey) {
                        settings.setEchoLinkPcEndpoint(
                            address,
                            endpoint.token,
                            supportsV2Events = endpoint.supportsV2Events,
                            name = endpoint.name,
                        )
                        persistedKey = attemptedKey
                    }
                }
                val error = status.error?.trim()?.takeIf { it.isNotEmpty() }
                if (status.connectionState == EchoRemoteConnectionState.Error && error != null) {
                    if (error != lastConnectionError) {
                        lastConnectionError = error
                        EchoErrorLog.record(EchoErrorSource.Connect, error)
                    }
                } else if (status.connectionState == EchoRemoteConnectionState.Connected) {
                    lastConnectionError = null
                }
                if (status.connectionState == EchoRemoteConnectionState.Disconnected &&
                    _dlnaRenderer.value == null
                ) {
                    stopLocalCast()
                }
            }
        }
        scope.launch {
            var lastLibraryError: String? = null
            client.library.collect { library ->
                val error = library.error?.trim()?.takeIf { it.isNotEmpty() }
                if (error != null && error != lastLibraryError) {
                    lastLibraryError = error
                    EchoErrorLog.record(EchoErrorSource.Connect, error)
                } else if (error == null) {
                    lastLibraryError = null
                }
            }
        }
        scope.launch {
            while (true) {
                delay(60_000)
                if (_castActive.value && castServer.isIdle(System.currentTimeMillis())) {
                    stopCastPlayback()
                }
            }
        }
    }

    fun publishLocalCast(
        tracks: List<EchoLinkCastSourceTrack>,
        peerHost: String? = null,
    ): List<EchoRemoteStreamItem>? {
        castPeerHost.set(peerHost ?: client.status.value.endpoint?.host)
        val host = EchoLinkLanAddresses.ipv4(application) ?: return null
        val port = runCatching { castServer.start() }.getOrNull() ?: return null
        val publications = tracks.map { track ->
            val artworkToken = track.artworkUri
                ?.takeIf { EchoLinkCastPolicy.isLocalFileUri(it) }
                ?.let { EchoLinkCastPolicy.newToken() }
            EchoLinkCastPublication(
                token = EchoLinkCastPolicy.newToken(),
                trackId = track.id,
                uri = track.uri,
                mimeType = track.mimeType,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUri,
                artworkToken = artworkToken,
                durationMs = track.durationMs,
                format = EchoLinkCastFormat.fromTrack(
                    uri = track.uri,
                    mimeType = track.mimeType,
                    sampleRateHz = track.sampleRateHz,
                    bitDepth = track.bitDepth,
                    channelCount = track.channelCount,
                    codec = track.codec,
                ),
            )
        }
        val items = castServer.publish(publications, host, port)
        if (items.isEmpty()) return null
        if (runCatching { EchoLinkCastService.start(application) }.isFailure) {
            castServer.stop()
            return null
        }
        return items
    }

    fun markCastStarted(targetName: String?) {
        _castActive.value = true
        _castTargetName.value = targetName?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun startDlnaCast(
        renderer: EchoLanRenderer,
        tracks: List<EchoLinkCastSourceTrack>,
        startIndex: Int,
        positionMs: Long,
        onFailure: (Throwable) -> Unit,
        onSuccess: () -> Unit,
    ) {
        val previous = _dlnaRenderer.value
        if (client.status.value.connectionState == EchoRemoteConnectionState.Connected) {
            client.send(EchoRemoteCommand.Stop)
        }
        scope.launch {
            if (previous != null) {
                withContext(Dispatchers.IO) { stopRenderer(previous) }
            }
            val items = publishLocalCast(tracks, peerHost = renderer.host)
            if (items == null) {
                onFailure(EchoDlnaException(500, "no_lan"))
                return@launch
            }
            val index = startIndex.coerceIn(0, items.lastIndex)
            val current = items[index]
            val rejected = EchoDlnaCastPolicy.rejectReason(renderer, current)
            if (rejected != null) {
                stopLocalCast()
                onFailure(EchoDlnaException(415, rejected.code))
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    dlnaClient.play(
                        renderer = renderer,
                        item = current,
                        positionMs = positionMs,
                        next = items.getOrNull(index + 1),
                    )
                }
            }
            result.onSuccess {
                dlnaItems = items
                dlnaSources = tracks
                dlnaIndex = index
                dlnaPaused = false
                _dlnaRenderer.value = renderer
                markCastStarted(renderer.name)
                beginFollowing(renderer, positionMs)
                onSuccess()
            }.onFailure { error ->
                EchoErrorLog.record(
                    EchoErrorSource.Connect,
                    error.message ?: error.javaClass.simpleName,
                )
                stopLocalCast()
                onFailure(error)
            }
        }
    }

    fun startChromecastCast(
        renderer: EchoLanRenderer,
        tracks: List<EchoLinkCastSourceTrack>,
        startIndex: Int,
        positionMs: Long,
        onFailure: (Throwable) -> Unit,
        onSuccess: () -> Unit,
    ) {
        if (client.status.value.connectionState == EchoRemoteConnectionState.Connected) {
            client.send(EchoRemoteCommand.Stop)
        }
        val previous = _dlnaRenderer.value
        scope.launch {
            if (previous != null && previous.kind != EchoLanRendererKind.Chromecast) {
                withContext(Dispatchers.IO) { stopRenderer(previous) }
            }
            val items = publishLocalCast(tracks, peerHost = renderer.host)
            if (items == null) {
                onFailure(EchoDlnaException(500, "no_lan"))
                return@launch
            }
            val index = startIndex.coerceIn(0, items.lastIndex)
            val current = items[index]
            val rejected = EchoChromecastCastPolicy.rejectReason(renderer, current)
            if (rejected != null) {
                stopLocalCast()
                onFailure(EchoDlnaException(415, rejected.code))
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { chromecastClient.play(renderer, current, positionMs) }
            }
            result.onSuccess {
                dlnaItems = items
                dlnaSources = tracks
                dlnaIndex = index
                dlnaPaused = false
                // Chromecast 也登记为当前渲染器，播放页的播放 / 暂停 / 切歌 / 拖动才会发往设备。
                _dlnaRenderer.value = renderer
                markCastStarted(renderer.name)
                beginFollowing(renderer, positionMs)
                onSuccess()
            }.onFailure { error ->
                EchoErrorLog.record(
                    EchoErrorSource.Connect,
                    error.message ?: error.javaClass.simpleName,
                )
                stopLocalCast()
                onFailure(error)
            }
        }
    }

    fun dlnaPlayPause() {
        if (chromecastClient.isActive()) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { chromecastClient.togglePause() } }
                if (result.isSuccess) {
                    dlnaPaused = !dlnaPaused
                    markCastPlaying(!dlnaPaused)
                }
            }
            return
        }
        val renderer = _dlnaRenderer.value ?: return
        val pause = !dlnaPaused
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (pause) dlnaClient.pause(renderer) else dlnaClient.resume(renderer)
                }
            }
            if (result.isSuccess) {
                dlnaPaused = pause
                markCastPlaying(!pause)
            }
        }
    }

    fun dlnaSkip(delta: Int) {
        val nextIndex = dlnaIndex + delta
        val items = dlnaItems
        val item = items.getOrNull(nextIndex) ?: return
        lastAdvanceElapsedMs = SystemClock.elapsedRealtime()
        if (chromecastClient.isActive()) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { chromecastClient.playNext(item) } }
                if (result.isSuccess) {
                    dlnaIndex = nextIndex
                    dlnaPaused = false
                    publishCastTrack(positionMs = 0L, playing = true)
                }
            }
            return
        }
        val renderer = _dlnaRenderer.value ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    dlnaClient.play(renderer, item, positionMs = 0L, next = items.getOrNull(nextIndex + 1))
                }
            }
            if (result.isSuccess) {
                dlnaIndex = nextIndex
                dlnaPaused = false
                publishCastTrack(positionMs = 0L, playing = true)
            }
        }
    }

    fun dlnaSeek(positionMs: Long) {
        val renderer = _dlnaRenderer.value ?: return
        val target = positionMs.coerceAtLeast(0L)
        sampleCastPosition(target)
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (renderer.kind == EchoLanRendererKind.Chromecast) {
                    chromecastClient.seek(target)
                } else {
                    dlnaClient.seek(renderer, target)
                }
            }
        }
    }

    /**
     * 音量键调远端音量。设备还没上报过音量时返回 false，让系统照常调手机音量，
     * 避免从一个猜测值起步把远端音量突然调大。
     */
    fun adjustCastVolume(delta: Float): Boolean {
        val renderer = _dlnaRenderer.value ?: return false
        val current = _castPlayback.value ?: return false
        val volume = current.volume ?: return false
        val next = (volume + delta).coerceIn(0f, 1f)
        _castPlayback.value = current.copy(volume = next)
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (renderer.kind == EchoLanRendererKind.Chromecast) {
                    chromecastClient.setVolume(next)
                } else {
                    dlnaClient.setVolume(renderer, (next * 100f).roundToInt())
                }
            }
        }
        return true
    }

    fun stopLocalCast() {
        runCatching { chromecastClient.close() }
        followJob?.cancel()
        followJob = null
        _castPlayback.value = null
        _castPosition.value = PlaybackPositionState()
        _dlnaRenderer.value = null
        dlnaItems = emptyList()
        dlnaSources = emptyList()
        dlnaIndex = 0
        dlnaPaused = false
        castPeerHost.set(null)
        castServer.stop()
        EchoLinkCastService.stop(application)
        _castActive.value = false
        _castTargetName.value = null
    }

    fun stopCastPlayback() {
        if (chromecastClient.isActive()) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { chromecastClient.stop() } }
                stopLocalCast()
            }
            return
        }
        val renderer = _dlnaRenderer.value
        if (renderer != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { dlnaClient.stop(renderer) }
                }
                stopLocalCast()
            }
            return
        }
        if (client.status.value.connectionState == EchoRemoteConnectionState.Connected) {
            client.send(EchoRemoteCommand.Stop)
        }
        stopLocalCast()
    }

    private fun stopRenderer(renderer: EchoLanRenderer) {
        runCatching {
            if (renderer.kind == EchoLanRendererKind.Chromecast) chromecastClient.stop() else dlnaClient.stop(renderer)
        }
    }

    private fun beginFollowing(renderer: EchoLanRenderer, positionMs: Long) {
        lastAdvanceElapsedMs = SystemClock.elapsedRealtime()
        publishCastTrack(positionMs = positionMs, playing = true)
        followJob?.cancel()
        followJob = scope.launch {
            if (renderer.kind == EchoLanRendererKind.Chromecast) {
                followChromecast(renderer)
            } else {
                followDlna(renderer)
            }
        }
    }

    private fun publishCastTrack(positionMs: Long, playing: Boolean, durationMs: Long? = null) {
        val renderer = _dlnaRenderer.value ?: return
        val source = dlnaSources.getOrNull(dlnaIndex) ?: return
        val previous = _castPlayback.value
        val sameTrack = previous?.index == dlnaIndex
        _castPlayback.value = EchoLanCastPlayback(
            rendererName = renderer.name,
            index = dlnaIndex,
            track = source,
            isPlaying = playing,
            durationMs = durationMs ?: if (sameTrack) previous!!.durationMs else source.durationMs,
            volume = previous?.volume,
        )
        sampleCastPosition(positionMs)
    }

    private fun markCastPlaying(playing: Boolean) {
        val current = _castPlayback.value ?: return
        sampleCastPosition(_castPosition.value.positionMs)
        _castPlayback.value = current.copy(isPlaying = playing)
    }

    private fun sampleCastPosition(positionMs: Long) {
        castSampledPositionMs = positionMs.coerceAtLeast(0L)
        castSampledAtElapsedMs = SystemClock.elapsedRealtime()
        _castPosition.value = PlaybackPositionState(
            positionMs = castSampledPositionMs,
            durationMs = _castPlayback.value?.durationMs ?: 0L,
        )
    }

    /** DLNA 没有推送，每秒拉一次进度和传输状态；音量每 10 秒拉一次。 */
    private suspend fun followDlna(renderer: EchoLanRenderer) {
        var wasPlaying = true
        var lastPositionMs = 0L
        var polls = 0
        while (_dlnaRenderer.value === renderer) {
            val info = withContext(Dispatchers.IO) { runCatching { dlnaClient.positionInfo(renderer) }.getOrNull() }
            val state = withContext(Dispatchers.IO) { runCatching { dlnaClient.transportState(renderer) }.getOrNull() }
            if (_dlnaRenderer.value !== renderer) return
            if (polls % DLNA_VOLUME_EVERY_POLLS == 0 && renderer.renderingControl != null) {
                withContext(Dispatchers.IO) { runCatching { dlnaClient.volume(renderer) }.getOrNull() }?.let { volume ->
                    _castPlayback.value = _castPlayback.value?.copy(volume = volume / 100f)
                }
            }
            polls += 1

            val advancedTo = EchoLanCastFollowPolicy.indexForTrackUri(dlnaItems, dlnaIndex, info?.trackUri)
            if (advancedTo != null) {
                // 渲染器已按 SetNextAVTransportURI 自行切歌：同步索引，并补上再下一首。
                dlnaIndex = advancedTo
                lastAdvanceElapsedMs = SystemClock.elapsedRealtime()
                publishCastTrack(positionMs = info?.positionMs ?: 0L, playing = true, durationMs = info?.durationMs)
                dlnaItems.getOrNull(advancedTo + 1)?.let { next ->
                    withContext(Dispatchers.IO) { runCatching { dlnaClient.setNext(renderer, next) } }
                }
                wasPlaying = true
                lastPositionMs = info?.positionMs ?: 0L
                delay(DLNA_POLL_MS)
                continue
            }

            val durationMs = info?.durationMs?.takeIf { it > 0L } ?: _castPlayback.value?.durationMs ?: 0L
            val inGrace = SystemClock.elapsedRealtime() - lastAdvanceElapsedMs < EchoLanCastFollowPolicy.ADVANCE_GRACE_MS
            if (!inGrace && EchoLanCastFollowPolicy.dlnaTrackEnded(state, wasPlaying, dlnaPaused, lastPositionMs, durationMs)) {
                if (dlnaItems.getOrNull(dlnaIndex + 1) != null) {
                    dlnaSkip(1)
                } else {
                    markCastPlaying(false)
                }
                wasPlaying = false
                delay(DLNA_POLL_MS)
                continue
            }

            if (state != null) {
                val playing = EchoLanCastFollowPolicy.isDlnaPlaying(state)
                val current = _castPlayback.value
                if (current != null && (current.isPlaying != playing || (durationMs > 0L && current.durationMs != durationMs))) {
                    _castPlayback.value = current.copy(isPlaying = playing, durationMs = durationMs)
                }
                wasPlaying = playing
            }
            info?.positionMs?.let { position ->
                lastPositionMs = position
                sampleCastPosition(position)
            }
            delay(DLNA_POLL_MS)
        }
    }

    /** Chromecast 状态由读线程推来；这里只在两次上报之间按时间外推进度。 */
    private suspend fun followChromecast(renderer: EchoLanRenderer) {
        while (_dlnaRenderer.value === renderer) {
            val cast = _castPlayback.value
            if (cast != null && cast.isPlaying) {
                _castPosition.value = PlaybackPositionState(
                    positionMs = EchoLanCastFollowPolicy.extrapolatedPositionMs(
                        sampledPositionMs = castSampledPositionMs,
                        sampledAtElapsedMs = castSampledAtElapsedMs,
                        nowElapsedMs = SystemClock.elapsedRealtime(),
                        playing = true,
                        durationMs = cast.durationMs,
                    ),
                    durationMs = cast.durationMs,
                )
            }
            delay(CHROMECAST_TICK_MS)
        }
    }

    private fun onChromecastStatus(status: EchoCastMediaStatus) {
        val renderer = _dlnaRenderer.value ?: return
        if (renderer.kind != EchoLanRendererKind.Chromecast) return
        if (status.playerState == "CLOSED") {
            stopLocalCast()
            return
        }
        val inGrace = SystemClock.elapsedRealtime() - lastAdvanceElapsedMs < EchoLanCastFollowPolicy.ADVANCE_GRACE_MS
        if (status.finished && !inGrace) {
            if (dlnaItems.getOrNull(dlnaIndex + 1) != null) dlnaSkip(1) else markCastPlaying(false)
            return
        }
        val current = _castPlayback.value ?: return
        val durationMs = status.durationMs?.takeIf { it > 0L } ?: current.durationMs
        val playing = when (status.playerState) {
            null -> current.isPlaying
            else -> status.isPlaying
        }
        dlnaPaused = !playing
        if (current.isPlaying != playing || current.durationMs != durationMs) {
            _castPlayback.value = current.copy(isPlaying = playing, durationMs = durationMs)
        }
        status.positionMs?.let(::sampleCastPosition)
    }

    private companion object {
        const val DLNA_POLL_MS = 1_000L
        const val DLNA_VOLUME_EVERY_POLLS = 10
        const val CHROMECAST_TICK_MS = 500L
    }
}

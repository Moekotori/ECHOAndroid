package app.echo.android

import android.app.Application
import app.echo.android.connect.EchoDlnaCastPolicy
import app.echo.android.connect.EchoDlnaClient
import app.echo.android.connect.EchoDlnaException
import app.echo.android.connect.EchoLinkCastFormat
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.connect.EchoLinkCastPublication
import app.echo.android.connect.EchoLinkCastServer
import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.connect.EchoLinkLanAddresses
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteStreamItem
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val client = EchoRemoteClient(scope).apply { setForeground(false) }
    private val castPeerHost = AtomicReference<String?>(null)
    val castServer = EchoLinkCastServer(
        openBody = EchoLinkCastMediaOpener(application.contentResolver),
        allowedPeerHost = { castPeerHost.get() ?: client.status.value.endpoint?.host },
    )
    private val dlnaClient = EchoDlnaClient()
    private val settings = EchoSettingsStore(application)
    private var persistedKey: Pair<String?, String?>? = null
    private var attemptedKey: Pair<String?, String?>? = null
    private val _castActive = MutableStateFlow(false)
    val castActive: StateFlow<Boolean> = _castActive.asStateFlow()
    private val _castTargetName = MutableStateFlow<String?>(null)
    val castTargetName: StateFlow<String?> = _castTargetName.asStateFlow()
    private val _dlnaRenderer = MutableStateFlow<EchoLanRenderer?>(null)
    val dlnaRenderer: StateFlow<EchoLanRenderer?> = _dlnaRenderer.asStateFlow()
    private var dlnaItems: List<EchoRemoteStreamItem> = emptyList()
    private var dlnaIndex: Int = 0
    private var dlnaPaused: Boolean = false

    init {
        scope.launch {
            settings.appSettings.collect { saved ->
                val key = saved.echoLinkPcAddress to saved.echoLinkPcToken
                if (!saved.echoLinkAutoReconnectEnabled) {
                    attemptedKey = null
                } else if (key != attemptedKey && !key.first.isNullOrBlank() && !key.second.isNullOrBlank()) {
                    attemptedKey = key
                    client.connectManual(
                        address = key.first!!,
                        token = key.second!!,
                        refreshLibraryOnConnect = saved.echoLinkPreferLinkedLibrary,
                        supportsV2Events = saved.echoLinkV2Events,
                    )
                }
            }
        }
        scope.launch {
            var lastConnectionError: String? = null
            client.status.collect { status ->
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
        runCatching { EchoLinkCastService.start(application) }
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
                withContext(Dispatchers.IO) {
                    runCatching { dlnaClient.stop(previous) }
                }
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
                dlnaIndex = index
                dlnaPaused = false
                _dlnaRenderer.value = renderer
                markCastStarted(renderer.name)
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
        val renderer = _dlnaRenderer.value ?: return
        val pause = !dlnaPaused
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (pause) dlnaClient.pause(renderer) else dlnaClient.resume(renderer)
                }
            }
            if (result.isSuccess) dlnaPaused = pause
        }
    }

    fun dlnaSkip(delta: Int) {
        val renderer = _dlnaRenderer.value ?: return
        val nextIndex = dlnaIndex + delta
        val items = dlnaItems
        val item = items.getOrNull(nextIndex) ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    dlnaClient.play(renderer, item, positionMs = 0L, next = items.getOrNull(nextIndex + 1))
                }
            }
            if (result.isSuccess) {
                dlnaIndex = nextIndex
                dlnaPaused = false
            }
        }
    }

    fun dlnaSeek(positionMs: Long) {
        val renderer = _dlnaRenderer.value ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { dlnaClient.seek(renderer, positionMs) }
        }
    }

    fun stopLocalCast() {
        _dlnaRenderer.value = null
        dlnaItems = emptyList()
        dlnaIndex = 0
        dlnaPaused = false
        castPeerHost.set(null)
        castServer.stop()
        EchoLinkCastService.stop(application)
        _castActive.value = false
        _castTargetName.value = null
    }

    fun stopCastPlayback() {
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
}

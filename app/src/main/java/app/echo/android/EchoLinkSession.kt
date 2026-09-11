package app.echo.android

import android.app.Application
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.connect.EchoLinkCastPublication
import app.echo.android.connect.EchoLinkCastServer
import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.connect.EchoLinkLanAddresses
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteStreamItem
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Process-owned connection: activity recreation must not replace playback credentials. */
class EchoLinkSession(private val application: Application) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
            EchoErrorLog.recordUncaught(error)
        },
    )
    val client = EchoRemoteClient(scope).apply { setForeground(false) }
    val castServer = EchoLinkCastServer(
        openBody = EchoLinkCastMediaOpener(application.contentResolver),
        allowedPeerHost = { client.status.value.endpoint?.host },
    )
    private val settings = EchoSettingsStore(application)
    private var persistedKey: Pair<String?, String?>? = null
    private var attemptedKey: Pair<String?, String?>? = null

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
                if (status.connectionState == EchoRemoteConnectionState.Disconnected) {
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
                if (castServer.isIdle(System.currentTimeMillis())) {
                    stopLocalCast()
                }
            }
        }
    }

    fun publishLocalCast(tracks: List<EchoLinkCastSourceTrack>): List<EchoRemoteStreamItem>? {
        val host = EchoLinkLanAddresses.ipv4(application) ?: return null
        val port = runCatching { castServer.start() }.getOrNull() ?: return null
        val publications = tracks.map { track ->
            EchoLinkCastPublication(
                token = EchoLinkCastPolicy.newToken(),
                trackId = track.id,
                uri = track.uri,
                mimeType = track.mimeType,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUri,
                durationMs = track.durationMs,
            )
        }
        val items = castServer.publish(publications, host, port)
        if (items.isEmpty()) return null
        runCatching { EchoLinkCastService.start(application) }
        return items
    }

    fun stopLocalCast() {
        castServer.stop()
        EchoLinkCastService.stop(application)
    }

    fun stopCastPlayback() {
        if (client.status.value.connectionState == EchoRemoteConnectionState.Connected) {
            client.send(EchoRemoteCommand.Stop)
        }
        stopLocalCast()
    }
}

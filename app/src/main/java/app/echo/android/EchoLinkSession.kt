package app.echo.android

import android.app.Application
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.connect.EchoRemoteConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Process-owned connection: activity recreation must not replace playback credentials. */
class EchoLinkSession(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val client = EchoRemoteClient(scope).apply { setForeground(false) }
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
                    client.connectManual(key.first!!, key.second!!, saved.echoLinkPreferLinkedLibrary)
                }
            }
        }
        scope.launch {
            client.status.collect { status ->
                val endpoint = status.endpoint
                if (status.connectionState == EchoRemoteConnectionState.Connected && endpoint != null && !endpoint.needsV2PairExchange) {
                    val address = "${endpoint.scheme}://${if (':' in endpoint.host) "[${endpoint.host}]" else endpoint.host}:${endpoint.port}"
                    attemptedKey = address to endpoint.token
                    if (persistedKey != attemptedKey) {
                        settings.setEchoLinkPcEndpoint(address, endpoint.token)
                        persistedKey = attemptedKey
                    }
                }
            }
        }
    }
}

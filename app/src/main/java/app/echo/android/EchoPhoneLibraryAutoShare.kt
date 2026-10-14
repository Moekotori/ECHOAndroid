package app.echo.android

import app.echo.android.connect.EchoLinkRequestPolicy
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Start only from a visible app; an already-running share keeps its own background owner. */
internal suspend fun observeEchoPhoneLibraryAutoShare(
    enabled: Flow<Boolean>,
    connection: Flow<EchoRemoteStatus>,
    visible: Flow<Boolean>,
    start: () -> Unit,
    stop: () -> Unit,
) {
    combine(enabled, connection, visible) { allowed, status, foreground ->
        val endpoint = status.endpoint
        val key = if (allowed && foreground && status.connectionState == EchoRemoteConnectionState.Connected &&
            endpoint != null && !endpoint.needsV2PairExchange) EchoLinkRequestPolicy.endpointIdentity(endpoint) else null
        allowed to key
    }.distinctUntilChanged().collect { (allowed, key) ->
        if (!allowed) stop()
        else if (key != null) start()
    }
}

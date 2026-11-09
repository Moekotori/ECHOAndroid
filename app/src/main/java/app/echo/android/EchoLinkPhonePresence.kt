package app.echo.android

import app.echo.android.connect.EchoLinkRequestPolicy
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.model.connect.EchoLinkPhonePresenceTrack
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import java.io.IOException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun observeEchoLinkPhonePresence(
    enabled: Flow<Boolean>,
    surface: StateFlow<EchoPlaybackSurfaceSnapshot>,
    client: EchoRemoteClient,
) = coroutineScope {
    val desired = MutableStateFlow<PhonePresencePublication?>(null)
    val targets = client.status.map { status ->
        status.endpoint?.takeIf { status.supportsPhonePresence }?.let { endpoint ->
            PhonePresenceTarget(endpoint, status.connectionId, status.connectionState)
        }
    }.distinctUntilChanged()
    val tracks = surface.distinctUntilChanged { previous, next ->
        previous.isPlaying == next.isPlaying && previous.hasTrack == next.hasTrack &&
            previous.title == next.title && previous.artist == next.artist
    }.map { snapshot ->
        if (snapshot.isPlaying && snapshot.hasTrack)
            EchoLinkPhonePresenceTrack.fromMetadata(snapshot.title, snapshot.artist)
        else null
    }.distinctUntilChanged()

    launch {
        enabled.distinctUntilChanged().collectLatest { sharing ->
            if (!sharing) {
                desired.value = null
                client.setPhonePresenceBackgroundConnectionRequired(false)
            } else {
                combine(targets, tracks) { target, track -> target to track }.collect { (target, track) ->
                    val connected = target?.state == EchoRemoteConnectionState.Connected
                    client.setPhonePresenceBackgroundConnectionRequired(
                        track != null && (connected || target?.state == EchoRemoteConnectionState.Reconnecting),
                    )
                    desired.value = if (connected && track != null)
                        PhonePresencePublication(target.endpoint, target.connectionId, track)
                    else null
                }
            }
        }
    }
    publishEchoLinkPhonePresence(desired, client::updatePhonePresence)
}

private data class PhonePresenceTarget(
    val endpoint: EchoRemoteEndpoint,
    val connectionId: Long,
    val state: EchoRemoteConnectionState,
)

internal data class PhonePresencePublication(
    val endpoint: EchoRemoteEndpoint,
    val connectionId: Long,
    val track: EchoLinkPhonePresenceTrack,
)

internal suspend fun publishEchoLinkPhonePresence(
    desired: StateFlow<PhonePresencePublication?>,
    update: suspend (EchoRemoteEndpoint, EchoLinkPhonePresenceTrack?) -> Unit,
) {
    suspend fun send(endpoint: EchoRemoteEndpoint, track: EchoLinkPhonePresenceTrack?) {
        try {
            update(endpoint, track)
        } catch (_: IOException) {
            // Next heartbeat retries a live track. The PC expires an unreachable clear.
        }
    }

    var published: PhonePresencePublication? = null
    while (true) {
        var next = desired.value
        if (published != null && (next == null ||
                !EchoLinkRequestPolicy.isSameEndpoint(published.endpoint, next.endpoint))
        ) {
            send(published.endpoint, null)
            next = desired.value
        }
        val publication = next
        if (publication != null) send(publication.endpoint, publication.track)
        published = publication
        if (publication == null) {
            desired.first { it != null }
        } else {
            withTimeoutOrNull(PhonePresenceRefreshMs) { desired.first { it != publication } }
        }
    }
}

private const val PhonePresenceRefreshMs = 30_000L

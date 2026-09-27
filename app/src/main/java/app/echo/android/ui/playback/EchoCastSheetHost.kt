package app.echo.android.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.connect.EchoLinkCastFormat
import app.echo.android.connect.EchoLinkCastPlan
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.data.EchoAppSettings
import app.echo.android.feature.connect.CastDevicesSheet
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteStatus
import app.echo.android.model.playback.EchoPlaybackStatus

/** Collect discovery results only while the player drawer is composed. */
@Composable
internal fun EchoCastSheetHost(
    viewModel: EchoAndroidViewModel,
    appSettings: EchoAppSettings,
    playbackStatus: EchoPlaybackStatus,
    remoteStatus: EchoRemoteStatus,
    castPlan: EchoLinkCastPlan,
    trackFormat: String?,
    trackLossless: Boolean,
    casting: Boolean,
    castSessionActive: Boolean,
    castSessionName: String?,
    sendingAddress: String?,
    castSetupError: String?,
    activeRendererId: String?,
    onCastToAddress: (String, String) -> Unit,
    onCastToConnected: () -> Unit,
    onCastToRenderer: (EchoLanRenderer) -> Unit,
    onStopCast: () -> Unit,
    onDismiss: () -> Unit,
) {
    val discoveryState by viewModel.echoLinkDiscoveryState.collectAsStateWithLifecycle()
    val devices by viewModel.echoLinkLanDevices.collectAsStateWithLifecycle()
    val renderers by viewModel.lanRenderers.collectAsStateWithLifecycle()
    val rendererState by viewModel.lanRendererDiscoveryState.collectAsStateWithLifecycle()
    val connected = remoteStatus.connectionState == EchoRemoteConnectionState.Connected
    CastDevicesSheet(
        phoneTrackTitle = playbackStatus.track?.title,
        phoneTrackArtist = playbackStatus.track?.artist,
        phoneTrackArtworkUrl = playbackStatus.track?.artworkUri,
        phoneTrackFormat = trackFormat,
        phoneTrackLossless = trackLossless,
        blockedReason = (castPlan as? EchoLinkCastPlan.Blocked)?.reason,
        casting = casting,
        castSessionActive = castSessionActive,
        castSessionName = castSessionName,
        sendingAddress = sendingAddress,
        remoteError = castSetupError ?: remoteStatus.error,
        discoveryState = discoveryState,
        discoveredLanDevices = devices,
        savedPcs = appSettings.echoLinkSavedPcs,
        savedPcAddress = appSettings.echoLinkPcAddress,
        savedPcToken = appSettings.echoLinkPcToken,
        connectedAddress = remoteStatus.endpoint?.takeIf { connected }?.let { endpoint ->
            EchoLinkCastPolicy.advertisedBaseUrl(endpoint.host, endpoint.port)
                .removePrefix("http://").removePrefix("https://")
        },
        connectedPcName = remoteStatus.endpoint?.name,
        castQueueCount = EchoLinkCastPolicy.trackCount(castPlan),
        showDsdWarning = EchoLinkCastPolicy.isDsd(
            playbackStatus.diagnostics.codec
                ?: EchoLinkCastFormat.fromTrack(playbackStatus.track?.uri.orEmpty()).codec,
        ),
        lanRenderers = renderers,
        lanRendererState = rendererState,
        activeRendererId = activeRendererId,
        onCastToAddress = onCastToAddress,
        onCastToConnected = onCastToConnected.takeIf { connected && castPlan !is EchoLinkCastPlan.Blocked },
        onCastToRenderer = onCastToRenderer,
        onStopCast = onStopCast,
        onRefreshLanDevices = viewModel::refreshEchoLinkDiscovery,
        onDismiss = onDismiss,
    )
}

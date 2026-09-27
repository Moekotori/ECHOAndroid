package app.echo.android.feature.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkCastBlockReason
import app.echo.android.connect.EchoLinkDiscoveryState
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoSavedPcEndpoint

/** The same device actions as Connect, presented without leaving the player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicesSheet(
    phoneTrackTitle: String?,
    phoneTrackArtist: String?,
    phoneTrackArtworkUrl: String?,
    phoneTrackFormat: String?,
    phoneTrackLossless: Boolean,
    blockedReason: EchoLinkCastBlockReason?,
    casting: Boolean,
    castSessionActive: Boolean,
    castSessionName: String?,
    sendingAddress: String?,
    remoteError: String?,
    discoveryState: EchoLinkDiscoveryState,
    discoveredLanDevices: List<EchoLinkLanDevice>,
    savedPcs: List<EchoSavedPcEndpoint>,
    savedPcAddress: String?,
    savedPcToken: String?,
    connectedAddress: String?,
    connectedPcName: String?,
    castQueueCount: Int,
    showDsdWarning: Boolean,
    lanRenderers: List<EchoLanRenderer>,
    lanRendererState: EchoLinkDiscoveryState,
    activeRendererId: String?,
    onCastToAddress: (String, String) -> Unit,
    onCastToConnected: (() -> Unit)?,
    onCastToRenderer: (EchoLanRenderer) -> Unit,
    onStopCast: () -> Unit,
    onRefreshLanDevices: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            CastDevicesPanel(
                phoneTrackTitle = phoneTrackTitle,
                phoneTrackArtist = phoneTrackArtist,
                phoneTrackArtworkUrl = phoneTrackArtworkUrl,
                phoneTrackFormat = phoneTrackFormat,
                phoneTrackLossless = phoneTrackLossless,
                blockedReason = blockedReason,
                casting = casting,
                castSessionActive = castSessionActive,
                castSessionName = castSessionName,
                sendingAddress = sendingAddress,
                remoteError = remoteError,
                discoveryState = discoveryState,
                discoveredLanDevices = discoveredLanDevices,
                savedPcs = savedPcs,
                savedPcAddress = savedPcAddress,
                savedPcToken = savedPcToken,
                connectedAddress = connectedAddress,
                connectedPcName = connectedPcName,
                castQueueCount = castQueueCount,
                showDsdWarning = showDsdWarning,
                lanRenderers = lanRenderers,
                lanRendererState = lanRendererState,
                activeRendererId = activeRendererId,
                onCastToAddress = onCastToAddress,
                onCastToConnected = onCastToConnected,
                onCastToRenderer = onCastToRenderer,
                onStopCast = onStopCast,
                onRefreshLanDevices = onRefreshLanDevices,
                onRequestPairing = {},
            )
        }
    }
}

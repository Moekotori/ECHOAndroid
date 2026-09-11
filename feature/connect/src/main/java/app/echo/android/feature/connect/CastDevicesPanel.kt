package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkCastBlockReason
import app.echo.android.connect.EchoLinkDiscoveryPolicy
import app.echo.android.connect.EchoLinkDiscoveryState
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLanRendererKind
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoSavedPcEndpoint

@Composable
internal fun CastDevicesPanel(
    phoneTrackTitle: String?,
    phoneTrackArtist: String?,
    phoneTrackArtworkUrl: String?,
    phoneTrackFormat: String? = null,
    phoneTrackLossless: Boolean = false,
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
    onCastToAddress: (String, String) -> Unit,
    onCastToConnected: (() -> Unit)? = null,
    onStopCast: () -> Unit,
    onRequestPairing: () -> Unit,
    onSelectLanDevice: (EchoLinkLanDevice) -> Unit = {},
    onRefreshLanDevices: () -> Unit,
    connectedPcName: String? = null,
    castQueueCount: Int = 0,
    showDsdWarning: Boolean = false,
    lanRenderers: List<EchoLanRenderer> = emptyList(),
    lanRendererState: EchoLinkDiscoveryState = EchoLinkDiscoveryState.Idle,
    activeRendererId: String? = null,
    onCastToRenderer: (EchoLanRenderer) -> Unit = {},
) {
    val connected = connectedAddress?.trim()?.takeIf { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(L10nR.string.echo_link_cast_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            ConnectNote(stringResource(L10nR.string.echo_link_cast_subtitle))
        }
        remoteError?.takeIf { it.isNotBlank() }?.let { ConnectNote(it, error = true) }
        ConnectSection(stringResource(L10nR.string.echo_link_cast_now_playing)) {
            val title = phoneTrackTitle?.trim().orEmpty()
            if (title.isEmpty() || blockedReason == EchoLinkCastBlockReason.EmptyQueue) {
                ConnectNote(stringResource(L10nR.string.echo_link_cast_play_first))
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EchoArtworkImage(
                        artworkUri = phoneTrackArtworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        shape = ConnectControlShape,
                        sizeClass = EchoArtworkSize.Thumbnail,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        phoneTrackArtist?.trim()?.takeIf { it.isNotEmpty() }?.let { ConnectNote(it) }
                        phoneTrackFormat?.takeIf { it.isNotBlank() }?.let { ConnectNote(it) }
                        if (phoneTrackLossless) {
                            ConnectNote(stringResource(L10nR.string.echo_link_cast_original))
                        }
                    }
                }
                if (blockedReason == EchoLinkCastBlockReason.UnsupportedSource) {
                    ConnectNote(stringResource(L10nR.string.echo_link_cast_unsupported_source))
                }
                if (showDsdWarning) {
                    ConnectNote(stringResource(L10nR.string.echo_link_cast_dsd_warning))
                }
                if (castQueueCount > 1) {
                    ConnectNote(stringResource(L10nR.string.echo_link_cast_queue_count, castQueueCount))
                }
            }
            val connectedCast = onCastToConnected
            if (connectedCast != null && blockedReason == null && !phoneTrackTitle.isNullOrBlank()) {
                Button(
                    onClick = connectedCast,
                    enabled = !casting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ConnectControlShape,
                ) {
                    Text(
                        stringResource(
                            L10nR.string.echo_link_cast_send_to,
                            connectedPcName?.takeIf { it.isNotBlank() }
                                ?: stringResource(L10nR.string.feature_connect_pc_link_4ca6bd),
                        ),
                    )
                }
            }
            if (casting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                ConnectNote(stringResource(L10nR.string.echo_link_cast_sending))
            } else if (castSessionActive) {
                ConnectNote(
                    stringResource(
                        L10nR.string.echo_link_cast_active,
                        castSessionName?.takeIf { it.isNotBlank() }
                            ?: stringResource(L10nR.string.feature_connect_pc_link_4ca6bd),
                    ),
                )
                OutlinedButton(
                    onClick = onStopCast,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ConnectControlShape,
                ) {
                    Text(stringResource(L10nR.string.echo_link_cast_stop))
                }
            }
        }
        ConnectSection(
            stringResource(L10nR.string.echo_link_cast_devices),
            action = {
                TextButton(onClick = onRefreshLanDevices, enabled = !casting) {
                    Text(stringResource(L10nR.string.feature_connect_refresh_828c69))
                }
            },
        ) {
            if (discoveredLanDevices.isEmpty()) {
                if (discoveryState == EchoLinkDiscoveryState.Searching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                ConnectNote(
                    stringResource(
                        when (discoveryState) {
                            EchoLinkDiscoveryState.Searching -> L10nR.string.echo_link_lan_searching
                            EchoLinkDiscoveryState.Failed -> L10nR.string.echo_link_lan_failed
                            else -> L10nR.string.feature_connect_no_pc_found_use_the_same_wi_fi_ce621e
                        },
                    ),
                    error = discoveryState == EchoLinkDiscoveryState.Failed,
                )
            }
            discoveredLanDevices.forEach { device ->
                val address = EchoLinkDiscoveryPolicy.addressLabel(device)
                val token = tokenForDevice(device, savedPcs, savedPcAddress, savedPcToken)
                CastDeviceRow(
                    name = device.name,
                    address = address,
                    sessionHere = castSessionActive &&
                        connected != null &&
                        EchoLinkDiscoveryPolicy.addressMatchesDevice(connected, device),
                    sending = casting && EchoLinkDiscoveryPolicy.sameLanEndpoint(sendingAddress, address),
                    token = token,
                    canCast = blockedReason == null && !phoneTrackTitle.isNullOrBlank(),
                    casting = casting,
                    onCast = { onCastToAddress(address, token) },
                    onPair = {
                        onSelectLanDevice(device)
                        onRequestPairing()
                    },
                )
            }
        }
        val nearbyKeys = discoveredLanDevices.map { device ->
            EchoLinkDiscoveryPolicy.lanEndpointKey(device.host, device.port)
        }.toSet()
        val extraSaved = savedPcs.filter { pc ->
            val key = EchoLinkDiscoveryPolicy.lanEndpointKey(pc.address)
            key != null && key !in nearbyKeys
        }
        ConnectSection(stringResource(L10nR.string.echo_link_cast_tvs)) {
            ConnectNote(stringResource(L10nR.string.echo_link_cast_tvs_note))
            if (lanRenderers.isEmpty()) {
                if (lanRendererState == EchoLinkDiscoveryState.Searching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                ConnectNote(
                    stringResource(
                        when (lanRendererState) {
                            EchoLinkDiscoveryState.Searching -> L10nR.string.echo_link_cast_tvs_searching
                            EchoLinkDiscoveryState.Failed -> L10nR.string.echo_link_lan_failed
                            else -> L10nR.string.echo_link_cast_tvs_empty
                        },
                    ),
                    error = lanRendererState == EchoLinkDiscoveryState.Failed,
                )
            }
            lanRenderers.forEach { renderer ->
                val kindLabel = stringResource(
                    if (renderer.kind == EchoLanRendererKind.Chromecast) {
                        L10nR.string.echo_link_cast_tv_kind_cast
                    } else {
                        L10nR.string.echo_link_cast_tv_kind_dlna
                    },
                )
                val dlnaReady = renderer.kind == EchoLanRendererKind.Dlna && renderer.avTransport != null
                CastDeviceRow(
                    name = renderer.name,
                    address = "$kindLabel · ${renderer.host}",
                    sessionHere = activeRendererId == renderer.id,
                    sending = casting && EchoLinkDiscoveryPolicy.sameLanEndpoint(sendingAddress, renderer.host),
                    token = if (dlnaReady) "dlna" else "",
                    canCast = dlnaReady && blockedReason == null && !phoneTrackTitle.isNullOrBlank(),
                    casting = casting,
                    onCast = { onCastToRenderer(renderer) },
                    unavailableLabel = if (dlnaReady) {
                        null
                    } else {
                        stringResource(L10nR.string.echo_link_cast_tv_unavailable)
                    },
                )
            }
        }
        if (extraSaved.isNotEmpty()) {
            ConnectSection(stringResource(L10nR.string.feature_connect_saved_pcs_4d2e91)) {
                extraSaved.asReversed().forEach { pc ->
                    CastDeviceRow(
                        name = pc.name,
                        address = pc.address,
                        sessionHere = castSessionActive && EchoLinkDiscoveryPolicy.sameLanEndpoint(connected, pc.address),
                        sending = casting && EchoLinkDiscoveryPolicy.sameLanEndpoint(sendingAddress, pc.address),
                        token = pc.token.orEmpty(),
                        canCast = blockedReason == null && !phoneTrackTitle.isNullOrBlank(),
                        casting = casting,
                        onCast = { onCastToAddress(pc.address, pc.token.orEmpty()) },
                        onPair = onRequestPairing,
                    )
                }
            }
        }
    }
}

@Composable
private fun CastDeviceRow(
    name: String,
    address: String,
    sessionHere: Boolean,
    sending: Boolean,
    token: String,
    canCast: Boolean,
    casting: Boolean,
    onCast: () -> Unit = {},
    onPair: () -> Unit = {},
    unavailableLabel: String? = null,
) {
    val paired = token.isNotBlank()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            ConnectNote(
                when {
                    sending -> stringResource(L10nR.string.echo_link_cast_sending)
                    sessionHere -> stringResource(L10nR.string.echo_link_cast_playing_here, address)
                    else -> address
                },
            )
        }
        when {
            unavailableLabel != null -> {
                OutlinedButton(onClick = {}, enabled = false, shape = ConnectControlShape) {
                    Text(unavailableLabel)
                }
            }
            paired -> {
                Button(
                    onClick = onCast,
                    enabled = canCast && !casting,
                    shape = ConnectControlShape,
                ) {
                    Text(stringResource(L10nR.string.echo_link_cast_send))
                }
            }
            else -> {
                OutlinedButton(onClick = onPair, enabled = !casting, shape = ConnectControlShape) {
                    Text(stringResource(L10nR.string.echo_link_cast_pair_first))
                }
            }
        }
    }
}

private fun tokenForDevice(
    device: EchoLinkLanDevice,
    savedPcs: List<EchoSavedPcEndpoint>,
    savedPcAddress: String?,
    savedPcToken: String?,
): String {
    val saved = savedPcs.firstOrNull { EchoLinkDiscoveryPolicy.addressMatchesDevice(it.address, device) }
    return saved?.token?.takeIf { it.isNotBlank() }
        ?: EchoLinkDiscoveryPolicy.tokenAfterSelecting(device, savedPcAddress, savedPcToken)
}

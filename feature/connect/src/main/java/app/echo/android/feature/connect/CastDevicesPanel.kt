package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val canCast = blockedReason == null && !phoneTrackTitle.isNullOrBlank()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(horizontal = 4.dp)) { ConnectNote(stringResource(L10nR.string.echo_link_cast_subtitle)) }
        remoteError?.takeIf { it.isNotBlank() }?.let { ConnectNote(it, error = true) }
        ConnectSection(stringResource(L10nR.string.echo_link_cast_now_playing)) {
            val title = phoneTrackTitle?.trim().orEmpty()
            if (title.isEmpty() || blockedReason == EchoLinkCastBlockReason.EmptyQueue) {
                ConnectNote(stringResource(L10nR.string.echo_link_cast_play_first))
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EchoArtworkImage(
                        artworkUri = phoneTrackArtworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        shape = ConnectControlShape,
                        sizeClass = EchoArtworkSize.Thumbnail,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        phoneTrackArtist?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                val format = phoneTrackFormat?.takeIf { it.isNotBlank() }
                if (format != null || phoneTrackLossless || castQueueCount > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        format?.let { ConnectStatusChip(it, ConnectTone.Neutral) }
                        if (phoneTrackLossless) ConnectStatusChip(stringResource(L10nR.string.echo_link_cast_original), ConnectTone.Positive)
                        if (castQueueCount > 1) ConnectStatusChip(stringResource(L10nR.string.echo_link_cast_queue_count, castQueueCount), ConnectTone.Neutral)
                    }
                }
                if (blockedReason == EchoLinkCastBlockReason.UnsupportedSource) {
                    ConnectNote(stringResource(L10nR.string.echo_link_cast_unsupported_source))
                }
                if (blockedReason == EchoLinkCastBlockReason.ClippedTrack) {
                    ConnectNote(stringResource(L10nR.string.echo_link_cast_clipped_track))
                }
                if (showDsdWarning) {
                    ConnectNote(stringResource(L10nR.string.echo_link_cast_dsd_warning))
                }
            }
            val connectedCast = onCastToConnected
            if (connectedCast != null && canCast) {
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
            list = true,
            action = {
                IconButton(onClick = onRefreshLanDevices, enabled = !casting) {
                    EchoIcon(Icons.Rounded.Refresh, stringResource(L10nR.string.feature_connect_refresh_828c69))
                }
            },
        ) {
            if (discoveredLanDevices.isEmpty()) PcDiscoveryEmptyState(discoveryState)
            discoveredLanDevices.forEachIndexed { index, device ->
                if (index > 0) ConnectRowDivider()
                val address = EchoLinkDiscoveryPolicy.addressLabel(device)
                val token = tokenForDevice(device, savedPcs, savedPcAddress, savedPcToken)
                CastDeviceRow(
                    name = device.name,
                    address = address,
                    sessionHere = castSessionActive &&
                        connected != null &&
                        EchoLinkDiscoveryPolicy.addressMatchesDevice(connected, device),
                    sending = casting && EchoLinkDiscoveryPolicy.sameLanEndpoint(sendingAddress, address),
                    canCast = canCast,
                    casting = casting,
                    onCast = { onCastToAddress(address, token) },
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
        ConnectSection(
            stringResource(L10nR.string.echo_link_cast_tvs),
            subtitle = stringResource(L10nR.string.echo_link_cast_tvs_note),
            list = true,
        ) {
            if (lanRenderers.isEmpty()) {
                PcDiscoveryEmptyState(
                    lanRendererState,
                    searching = L10nR.string.echo_link_cast_tvs_searching,
                    empty = L10nR.string.echo_link_cast_tvs_empty,
                )
            }
            lanRenderers.forEachIndexed { index, renderer ->
                if (index > 0) ConnectRowDivider()
                val kindLabel = stringResource(
                    if (renderer.kind == EchoLanRendererKind.Chromecast) {
                        L10nR.string.echo_link_cast_tv_kind_cast
                    } else {
                        L10nR.string.echo_link_cast_tv_kind_dlna
                    },
                )
                val dlnaReady = renderer.kind == EchoLanRendererKind.Dlna && renderer.avTransport != null
                val castReady = renderer.kind == EchoLanRendererKind.Chromecast
                val rendererReady = dlnaReady || castReady
                CastDeviceRow(
                    name = renderer.name,
                    address = "$kindLabel · ${renderer.host}",
                    sessionHere = activeRendererId == renderer.id,
                    sending = casting && EchoLinkDiscoveryPolicy.sameLanEndpoint(sendingAddress, renderer.host),
                    canCast = rendererReady && canCast,
                    casting = casting,
                    onCast = { onCastToRenderer(renderer) },
                    unavailableLabel = if (rendererReady) {
                        null
                    } else {
                        stringResource(L10nR.string.echo_link_cast_tv_unavailable)
                    },
                )
            }
        }
        if (extraSaved.isNotEmpty()) {
            ConnectSection(stringResource(L10nR.string.feature_connect_saved_pcs_4d2e91), list = true) {
                extraSaved.asReversed().forEachIndexed { index, pc ->
                    if (index > 0) ConnectRowDivider()
                    CastDeviceRow(
                        name = pc.name,
                        address = pc.address,
                        sessionHere = castSessionActive && EchoLinkDiscoveryPolicy.sameLanEndpoint(connected, pc.address),
                        sending = casting && EchoLinkDiscoveryPolicy.sameLanEndpoint(sendingAddress, pc.address),
                        canCast = canCast,
                        casting = casting,
                        onCast = { onCastToAddress(pc.address, pc.token.orEmpty()) },
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) { ConnectNote(stringResource(L10nR.string.echo_link_battery_hint)) }
    }
}

@Composable
private fun CastDeviceRow(
    name: String,
    address: String,
    sessionHere: Boolean,
    sending: Boolean,
    canCast: Boolean,
    casting: Boolean,
    onCast: () -> Unit = {},
    unavailableLabel: String? = null,
) {
    PcDeviceRow(
        name = name,
        address = when {
            sending -> stringResource(L10nR.string.echo_link_cast_sending)
            sessionHere -> stringResource(L10nR.string.echo_link_cast_playing_here, address)
            else -> address
        },
        actionLabel = unavailableLabel ?: stringResource(L10nR.string.echo_link_cast_send),
        onConnect = onCast,
        enabled = unavailableLabel == null && canCast && !casting,
        active = sessionHere,
    )
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

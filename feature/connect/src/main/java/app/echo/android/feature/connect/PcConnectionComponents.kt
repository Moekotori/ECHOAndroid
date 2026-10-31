package app.echo.android.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkDiscoveryState
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.model.connect.EchoRemoteConnectionState

internal fun connectionTone(state: EchoRemoteConnectionState): ConnectTone = when (state) {
    EchoRemoteConnectionState.Connected -> ConnectTone.Positive
    EchoRemoteConnectionState.Pairing,
    EchoRemoteConnectionState.Connecting,
    EchoRemoteConnectionState.Reconnecting -> ConnectTone.Warning
    EchoRemoteConnectionState.Error -> ConnectTone.Error
    EchoRemoteConnectionState.Disconnected -> ConnectTone.Neutral
}

/** Connection summary card: who we are linked to, in what state, and the one action that matters now. */
@Composable
internal fun PcLinkHero(
    title: String,
    state: EchoRemoteConnectionState,
    showRequirement: Boolean,
    error: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val busy = state == EchoRemoteConnectionState.Pairing ||
        state == EchoRemoteConnectionState.Connecting || state == EchoRemoteConnectionState.Reconnecting
    ConnectCard(highlighted = state == EchoRemoteConnectionState.Connected) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ConnectIconTile(Icons.Rounded.Computer, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                ConnectStatusChip(remoteConnectionLabel(state), connectionTone(state), progress = busy)
            }
        }
        if (showRequirement) EchoSteamNotice()
        error?.takeIf { it.isNotBlank() }?.let { ConnectNote(it, error = true) }
        content()
    }
}

/** Device text yields space to actions even on narrow screens. */
@Composable
internal fun PcDeviceRow(
    name: String,
    address: String,
    actionLabel: String,
    onConnect: () -> Unit,
    onForget: (() -> Unit)? = null,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            ConnectIconTile(Icons.Rounded.Computer, size = 40.dp)
            if (active) Box(Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(12.dp)
                .background(scheme.surfaceContainerLow, CircleShape).padding(2.dp)
                .background(scheme.primary, CircleShape))
        }
        Column(Modifier.weight(1f).echoClickable(enabled = enabled, role = Role.Button, onClick = onConnect)
            .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(address, style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(onClick = onConnect, enabled = enabled, shape = ConnectControlShape,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            Text(actionLabel, maxLines = 1)
        }
        if (onForget != null) {
            IconButton(onClick = onForget) {
                EchoIcon(Icons.Rounded.DeleteOutline,
                    stringResource(R.string.feature_connect_forget_30df50))
            }
        }
    }
}

@Composable
internal fun PcDiscoveryEmptyState(
    state: EchoLinkDiscoveryState,
    searching: Int = R.string.echo_link_lan_searching,
    empty: Int = R.string.feature_connect_no_pc_found_use_the_same_wi_fi_ce621e,
) {
    val failed = state == EchoLinkDiscoveryState.Failed
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state == EchoLinkDiscoveryState.Searching ->
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                failed -> EchoIcon(Icons.Rounded.ErrorOutline, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                else -> EchoIcon(Icons.Rounded.Wifi, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ConnectNote(stringResource(when (state) {
                EchoLinkDiscoveryState.Searching -> searching
                EchoLinkDiscoveryState.Failed -> R.string.echo_link_lan_failed
                else -> empty
            }), error = failed)
        }
    }
}

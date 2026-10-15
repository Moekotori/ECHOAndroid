package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkDiscoveryState
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import androidx.compose.ui.semantics.Role
import app.echo.android.model.connect.EchoRemoteConnectionState

@Composable
internal fun PcLinkHeader(title: String, state: EchoRemoteConnectionState, showRequirement: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                EchoIcon(Icons.Rounded.Computer, null, Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(remoteConnectionLabel(state), style = MaterialTheme.typography.labelMedium,
                color = if (state == EchoRemoteConnectionState.Connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            if (showRequirement) EchoSteamNotice()
        }
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
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoIcon(Icons.Rounded.Computer, null, Modifier.size(24.dp))
        Column(Modifier.weight(1f).echoClickable(role = Role.Button, onClick = onConnect)
            .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(address, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(onClick = onConnect, shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Text(actionLabel)
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
internal fun PcDiscoveryEmptyState(state: EchoLinkDiscoveryState) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state == EchoLinkDiscoveryState.Searching) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            ConnectNote(stringResource(when (state) {
                EchoLinkDiscoveryState.Searching -> R.string.echo_link_lan_searching
                EchoLinkDiscoveryState.Failed -> R.string.echo_link_lan_failed
                else -> R.string.feature_connect_no_pc_found_use_the_same_wi_fi_ce621e
            }), error = state == EchoLinkDiscoveryState.Failed)
        }
    }
}

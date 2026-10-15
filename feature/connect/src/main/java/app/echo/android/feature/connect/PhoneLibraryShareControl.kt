package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSwitch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.echo.android.model.connect.EchoRemoteConnectionState

@Composable
internal fun PhoneLibraryShareControl(
    status: String,
    connectionState: EchoRemoteConnectionState,
    active: Boolean,
    onConnectRequested: () -> Unit,
    onChange: (Boolean) -> Unit,
) {
    val connected = connectionState == EchoRemoteConnectionState.Connected
    // This intent belongs to the visible page, never to a restored/background session.
    var awaitingConnection by remember { mutableStateOf(false) }
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(connectionState, active) {
        if (!active || connectionState == EchoRemoteConnectionState.Error ||
            connectionState == EchoRemoteConnectionState.Disconnected) {
            awaitingConnection = false
        }
    }
    LaunchedEffect(connected, awaitingConnection, active) {
        if (active && connected && awaitingConnection) {
            awaitingConnection = false
            change(true)
        }
    }
    val title = stringResource(R.string.phone_library_share_title)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.phone_library_share_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EchoSwitch(
                modifier = Modifier.semantics { contentDescription = title },
                checked = awaitingConnection || status == "starting" || status == "sharing",
                onCheckedChange = { enabled ->
                    if (enabled && !connected) {
                        awaitingConnection = true
                        onConnectRequested()
                    } else {
                        awaitingConnection = false
                        onChange(enabled)
                    }
                },
            )
        }
        Text(stringResource(when {
            awaitingConnection -> R.string.phone_library_share_waiting
            status == "unsupported" -> R.string.phone_library_share_unsupported
            status == "auth_error" -> R.string.phone_library_share_auth_error
            status == "network_error" -> R.string.phone_library_share_network_error
            status == "no_lan" -> R.string.phone_library_share_no_lan
            status == "service_error" -> R.string.phone_library_share_service_error
            status == "connection_required" -> R.string.phone_library_share_connection_required
            status == "connection_lost" -> R.string.phone_library_share_connection_lost
            status == "error" -> R.string.phone_library_share_error
            status == "starting" -> R.string.phone_library_share_starting
            status == "sharing" -> R.string.phone_library_share_active
            !connected -> R.string.phone_library_share_connect_first
            else -> R.string.phone_library_share_off
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status == "sharing") {
            Text(
                stringResource(R.string.phone_library_share_pc_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.design.echoExpandIndicator

/** Collapsed "enter address or pairing link" card; the form only exists while expanded. */
@Composable
internal fun PcManualEntry(
    expanded: Boolean,
    onToggle: () -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    validAddress: Boolean,
    canConnect: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectCard(modifier, contentPadding = ListCardPadding, spacing = 0.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).echoClickable(role = Role.Button, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoIcon(Icons.Rounded.Link, null, Modifier.size(22.dp))
            Text(stringResource(L10nR.string.echo_link_pairing_entry), Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall)
            EchoIcon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, Modifier.echoExpandIndicator(expanded))
        }
        EchoExpand(expanded) {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectNote(stringResource(L10nR.string.echo_link_direct_address_hint))
                ConnectInput(stringResource(L10nR.string.feature_connect_pc_address_or_pairing_link_a3e680), address, onAddressChange,
                    "192.168.1.20:26789", url = true, onDone = onConnect,
                    error = if (address.isNotBlank() && !validAddress) stringResource(L10nR.string.feature_connect_check_the_pc_address_or_paste_a_complete_0f216a) else null)
                Button(onClick = onConnect, enabled = canConnect, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(L10nR.string.feature_connect_connect_pc_724ba0))
                }
            }
        }
    }
}

@Composable
internal fun PcLinkPreferences(
    hasSaved: Boolean,
    busy: Boolean,
    autoReconnectEnabled: Boolean,
    linkedLibraryDefault: Boolean,
    onAutoReconnectChange: (Boolean) -> Unit,
    onLinkedLibraryDefaultChange: (Boolean) -> Unit,
    onForgetRequest: () -> Unit,
) {
    ConnectSection(stringResource(L10nR.string.feature_connect_link_preferences_f81ffb), list = true) {
        ConnectPreference(stringResource(L10nR.string.feature_connect_auto_reconnect_5259cc),
            if (hasSaved) stringResource(L10nR.string.feature_connect_try_the_saved_pc_when_opening_connect_38be4d)
            else stringResource(L10nR.string.feature_connect_available_after_your_first_saved_connection_7136ab),
            autoReconnectEnabled, enabled = hasSaved && !busy, onChange = onAutoReconnectChange)
        ConnectRowDivider()
        ConnectPreference(stringResource(L10nR.string.feature_connect_use_linked_library_by_default_a9eb0f),
            stringResource(L10nR.string.feature_connect_read_the_pc_library_after_connecting_local_music_d1da04),
            linkedLibraryDefault, onChange = onLinkedLibraryDefaultChange)
        if (hasSaved && !busy) {
            ConnectRowDivider()
            TextButton(onClick = onForgetRequest) {
                Text(stringResource(L10nR.string.feature_connect_forget_saved_pc_8860a3), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

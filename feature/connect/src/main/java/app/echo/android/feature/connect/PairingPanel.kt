package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkDiscoveryPolicy
import app.echo.android.connect.EchoPairingParser
import app.echo.android.design.EchoExpand
import app.echo.android.design.echoClickable
import app.echo.android.design.echoExpandIndicator
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoSavedPcEndpoint

@Composable
internal fun PcLinkPanel(
    remoteState: EchoRemoteConnectionState,
    pcTitle: String,
    trackTitle: String,
    trackArtist: String,
    trackArtworkUrl: String?,
    isPlaying: Boolean,
    remoteError: String?,
    scanMessage: String?,
    scanMessageIsError: Boolean,
    savedPcAddress: String?,
    savedPcToken: String?,
    savedPcs: List<EchoSavedPcEndpoint> = emptyList(),
    autoReconnectEnabled: Boolean,
    linkedLibraryDefault: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    queueTitles: List<String> = emptyList(),
    onConnectPc: (String, String) -> Unit,
    onScanPairingCode: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onDisconnect: () -> Unit,
    onForgetPc: () -> Unit,
    onForgetSavedPc: (EchoSavedPcEndpoint) -> Unit = {},
    onAutoReconnectChange: (Boolean) -> Unit,
    onLinkedLibraryDefaultChange: (Boolean) -> Unit,
    discoveredLanDevices: List<EchoLinkLanDevice>,
    onSelectLanDevice: (EchoLinkLanDevice) -> Unit,
    onRefreshLanDevices: () -> Unit,
    onHandoffPhoneToPc: (() -> Unit)? = null,
) {
    val connected = remoteState == EchoRemoteConnectionState.Connected
    val busy = remoteState in listOf(EchoRemoteConnectionState.Pairing, EchoRemoteConnectionState.Connecting, EchoRemoteConnectionState.Reconnecting)
    var address by rememberSaveable(savedPcAddress) { mutableStateOf(savedPcAddress.orEmpty()) }
    var token by rememberSaveable(savedPcToken) { mutableStateOf(savedPcToken.orEmpty()) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    val hasSaved = !savedPcAddress.isNullOrBlank() && !savedPcToken.isNullOrBlank()
    val endpoint = remember(address, token) { EchoPairingParser.parseManual(address, token) }
    val embeddedPairing = remember(address) { EchoPairingParser.parse(address) != null }
    val validAddress = remember(address) { EchoPairingParser.parseManual(address, "validation-token") != null }
    val keyboard = LocalSoftwareKeyboardController.current
    val connect = { if (endpoint != null && !busy) { keyboard?.hide(); onConnectPc(address.trim(), token.trim()) } }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (connected || hasSaved) pcTitle else stringResource(L10nR.string.feature_connect_listen_together_with_pc_41d068),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Text(remoteConnectionLabel(remoteState), color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
            ConnectNote(stringResource(L10nR.string.feature_connect_control_pc_playback_from_your_phone_or_browse_20f8b5))
        }
        remoteError?.takeIf { it.isNotBlank() }?.let { ConnectNote(it, error = true) }
        scanMessage?.takeIf { it.isNotBlank() }?.let { ConnectNote(it, error = scanMessageIsError) }
        when {
            busy -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                ConnectNote(stringResource(L10nR.string.feature_connect_keep_pc_echo_running_and_both_devices_on_7986ec))
                TextButton(onClick = onDisconnect) { Text(stringResource(L10nR.string.feature_connect_cancel_connection_fd085f)) }
            }
            connected -> {
                RemoteNowPlaying(
                    title = trackTitle,
                    artist = trackArtist,
                    artworkUrl = trackArtworkUrl,
                    isPlaying = isPlaying,
                    controlsEnabled = true,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    volume = volume,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    onVolume = onVolume,
                    queueTitles = queueTitles,
                )
                if (onHandoffPhoneToPc != null) {
                    OutlinedButton(onClick = onHandoffPhoneToPc, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(L10nR.string.echo_link_handoff_phone))
                    }
                }
                TextButton(onClick = onDisconnect) { Text(stringResource(L10nR.string.feature_connect_disconnect_pc_fd446c)) }
            }
            else -> {
                if (savedPcs.isNotEmpty() && !manual) {
                    ConnectNote(stringResource(L10nR.string.feature_connect_a_saved_pc_is_available_reconnect_or_pair_69fbf8))
                    ConnectSection(stringResource(L10nR.string.feature_connect_saved_pcs_4d2e91)) {
                        savedPcs.asReversed().forEach { pc ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(pc.name, style = MaterialTheme.typography.bodyMedium)
                                    ConnectNote(pc.address)
                                }
                                TextButton(
                                    onClick = { onConnectPc(pc.address, pc.token.orEmpty()) },
                                    enabled = !pc.token.isNullOrBlank() && !busy,
                                ) {
                                    Text(stringResource(L10nR.string.feature_connect_connect_c7c091))
                                }
                                TextButton(
                                    onClick = { onForgetSavedPc(pc) },
                                    enabled = !busy,
                                ) {
                                    Text(
                                        stringResource(L10nR.string.feature_connect_forget_30df50),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                } else if (hasSaved && !manual) {
                    ConnectNote(stringResource(L10nR.string.feature_connect_a_saved_pc_is_available_reconnect_or_pair_69fbf8))
                    Button(onClick = { onConnectPc(savedPcAddress.orEmpty(), savedPcToken.orEmpty()) }, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(L10nR.string.feature_connect_reconnect_pc_ea8c5f))
                    }
                }
                if (!manual) {
                    if (!hasSaved) ConnectNote(stringResource(L10nR.string.feature_connect_open_the_echo_link_page_on_your_pc_d07a0b))
                    if (hasSaved) {
                        OutlinedButton(onClick = onScanPairingCode, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(L10nR.string.feature_connect_pair_another_pc_23df07))
                        }
                    } else {
                        Button(onClick = onScanPairingCode, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(L10nR.string.feature_connect_scan_to_pair_597ba6))
                        }
                    }
                }
                ConnectSection(stringResource(L10nR.string.feature_connect_nearby_pcs_b9b12a),
                    action = { TextButton(onClick = onRefreshLanDevices) { Text(stringResource(L10nR.string.feature_connect_refresh_828c69)) } }) {
                    if (discoveredLanDevices.isEmpty()) ConnectNote(stringResource(L10nR.string.feature_connect_no_pc_found_use_the_same_wi_fi_ce621e))
                    discoveredLanDevices.forEach { device ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).echoClickable(role = Role.Button) {
                            address = EchoLinkDiscoveryPolicy.addressLabel(device)
                            token = EchoLinkDiscoveryPolicy.tokenAfterSelecting(device, savedPcAddress, savedPcToken)
                            manual = true
                            onSelectLanDevice(device)
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.bodyMedium)
                                ConnectNote(EchoLinkDiscoveryPolicy.addressLabel(device))
                            }
                            Text(stringResource(L10nR.string.feature_connect_select_21bce2), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).echoClickable(role = Role.Button) { keyboard?.hide(); manual = !manual }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(L10nR.string.feature_connect_enter_address_manually_ec2890), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, Modifier.echoExpandIndicator(manual))
                    }
                    EchoExpand(manual) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ConnectInput(stringResource(L10nR.string.feature_connect_pc_address_or_pairing_link_a3e680), address, { address = it }, "192.168.1.12:26789", url = true,
                                error = if (address.isNotBlank() && !validAddress) stringResource(L10nR.string.feature_connect_check_the_pc_address_or_paste_a_complete_0f216a) else null)
                            if (!embeddedPairing) ConnectInput(stringResource(L10nR.string.feature_connect_pairing_token_2e95dd), token, { token = it },
                                secret = true, onDone = connect,
                                error = if (token.isNotBlank() && token.trim().length < 8) stringResource(L10nR.string.feature_connect_copy_the_complete_token_from_pc_echo_7d737c) else null)
                            ConnectNote(stringResource(L10nR.string.feature_connect_copy_the_address_and_token_from_pc_echo_5d25bc))
                            Button(onClick = connect, enabled = endpoint != null, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(L10nR.string.feature_connect_connect_pc_724ba0))
                            }
                        }
                    }
                }
            }
        }
        ConnectSection(stringResource(L10nR.string.feature_connect_link_preferences_f81ffb)) {
            ConnectPreference(stringResource(L10nR.string.feature_connect_auto_reconnect_5259cc),
                if (hasSaved) stringResource(L10nR.string.feature_connect_try_the_saved_pc_when_opening_connect_38be4d)
                else stringResource(L10nR.string.feature_connect_available_after_your_first_saved_connection_7136ab),
                autoReconnectEnabled, enabled = hasSaved && !busy, onChange = onAutoReconnectChange)
            ConnectPreference(stringResource(L10nR.string.feature_connect_use_linked_library_by_default_a9eb0f),
                stringResource(L10nR.string.feature_connect_read_the_pc_library_after_connecting_local_music_d1da04),
                linkedLibraryDefault, onChange = onLinkedLibraryDefaultChange)
            if (hasSaved && !busy) TextButton(onClick = { confirmForget = true }) {
                Text(stringResource(L10nR.string.feature_connect_forget_saved_pc_8860a3), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmForget) ForgetConnectionDialog(
        stringResource(L10nR.string.feature_connect_forget_saved_pc_80b440),
        onDismiss = { confirmForget = false },
        onConfirm = { confirmForget = false; address = ""; token = ""; onForgetPc() },
    )
}

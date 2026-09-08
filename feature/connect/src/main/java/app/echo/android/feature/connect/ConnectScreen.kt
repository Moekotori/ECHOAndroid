package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkDiscoveryPolicy
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.echoExpandIndicator
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassCyan
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoGlassViolet
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoOnAccentColor
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.EchoTextButton
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.PageChrome
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoGlassRowBrush
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress

@Composable
fun ConnectScreen(
    remoteState: EchoRemoteConnectionState,
    pcTitle: String,
    trackTitle: String,
    trackArtist: String,
    trackArtworkUrl: String?,
    isPlaying: Boolean,
    remoteError: String?,
    scanMessage: String?,
    scanMessageIsError: Boolean = false,
    savedPcAddress: String?,
    savedPcToken: String?,
    autoReconnectEnabled: Boolean,
    linkedLibraryDefault: Boolean,
    discordPresenceEnabled: Boolean,
    discordPresenceReady: Boolean,
    discordPresenceTrackTitle: String?,
    subsonicServerUrl: String?,
    subsonicUsername: String?,
    subsonicPassword: String?,
    webDavServerUrl: String?,
    webDavUsername: String?,
    webDavPassword: String?,
    remoteScanState: LibraryScanProgress,
    onConnectPc: (String, String) -> Unit,
    onScanPairingCode: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
    onForgetPc: () -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onLinkedLibraryDefaultChange: (Boolean) -> Unit,
    onSyncSubsonicLibrary: (String, String, String) -> Unit,
    onSaveSubsonicCredentials: (String, String, String) -> Unit,
    onClearSubsonicCredentials: () -> Unit,
    onSyncWebDavLibrary: (String, String, String) -> Unit,
    onSaveWebDavCredentials: (String, String, String) -> Unit,
    onClearWebDavCredentials: () -> Unit,
    onCancelRemoteSync: () -> Unit,
    discoveredLanDevices: List<EchoLinkLanDevice> = emptyList(),
    onSelectLanDevice: (EchoLinkLanDevice) -> Unit = {},
    onRefreshLanDevices: () -> Unit = {},
) {
    val connected = remoteState == EchoRemoteConnectionState.Connected
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    var subsonicServerInput by rememberSaveable(subsonicServerUrl) { mutableStateOf(subsonicServerUrl.orEmpty()) }
    var subsonicUserInput by rememberSaveable(subsonicUsername) { mutableStateOf(subsonicUsername.orEmpty()) }
    var subsonicPasswordInput by rememberSaveable(subsonicPassword) { mutableStateOf(subsonicPassword.orEmpty()) }
    var webDavServerInput by rememberSaveable(webDavServerUrl) { mutableStateOf(webDavServerUrl.orEmpty()) }
    var webDavUserInput by rememberSaveable(webDavUsername) { mutableStateOf(webDavUsername.orEmpty()) }
    var webDavPasswordInput by rememberSaveable(webDavPassword) { mutableStateOf(webDavPassword.orEmpty()) }
    var webDavExpanded by rememberSaveable(webDavServerUrl, webDavUsername, webDavPassword) {
        mutableStateOf(!webDavServerUrl.isNullOrBlank() || !webDavUsername.isNullOrBlank() || !webDavPassword.isNullOrBlank())
    }
    var subsonicExpanded by rememberSaveable(subsonicServerUrl, subsonicUsername, subsonicPassword) {
        mutableStateOf(!subsonicServerUrl.isNullOrBlank() || !subsonicUsername.isNullOrBlank() || !subsonicPassword.isNullOrBlank())
    }
    var remoteSourcesExpanded by rememberSaveable(
        subsonicServerUrl,
        subsonicUsername,
        subsonicPassword,
        webDavServerUrl,
        webDavUsername,
        webDavPassword,
    ) {
        mutableStateOf(
            !subsonicServerUrl.isNullOrBlank() ||
                !subsonicUsername.isNullOrBlank() ||
                !subsonicPassword.isNullOrBlank() ||
                !webDavServerUrl.isNullOrBlank() ||
                !webDavUsername.isNullOrBlank() ||
                !webDavPassword.isNullOrBlank(),
        )
    }
    var pcAddressInput by rememberSaveable(savedPcAddress) { mutableStateOf(savedPcAddress.orEmpty()) }
    var pcTokenInput by rememberSaveable(savedPcToken) { mutableStateOf(savedPcToken.orEmpty()) }
    val hasSavedPc = !savedPcAddress.isNullOrBlank() && !savedPcToken.isNullOrBlank()
    val canConnectPc = pcAddressInput.isNotBlank() &&
        (pcTokenInput.isNotBlank() || pcAddressInput.trim().lowercase().startsWith("echo://pair"))
    PageChrome(
        title = stringResource(L10nR.string.feature_connect_connect_c7c091),
        subtitle = stringResource(L10nR.string.feature_connect_library_sources_pc_link_b81eae),
        badge = stringResource(L10nR.string.feature_connect_link_f7ada1),
        scrollable = true,
        scrollBottomPadding = 188.dp,
    ) {
        EchoSectionTitle(
            stringResource(L10nR.string.feature_connect_music_services_c8d7d1),
            stringResource(L10nR.string.feature_connect_connect_your_library_sources_2ae5a5),
        )
        Spacer(Modifier.height(12.dp))
        RemoteSourcesPanel(
            subsonicServerUrl = subsonicServerInput,
            subsonicUsername = subsonicUserInput,
            subsonicPassword = subsonicPasswordInput,
            webDavServerUrl = webDavServerInput,
            webDavUsername = webDavUserInput,
            webDavPassword = webDavPasswordInput,
            expanded = remoteSourcesExpanded || remoteScanState.isScanning,
            subsonicExpanded = subsonicExpanded,
            webDavExpanded = webDavExpanded,
            scanState = remoteScanState,
            onExpandedChange = { remoteSourcesExpanded = it },
            onSubsonicExpandedChange = { subsonicExpanded = it },
            onWebDavExpandedChange = { webDavExpanded = it },
            onSubsonicServerUrlChange = { subsonicServerInput = it },
            onSubsonicUsernameChange = { subsonicUserInput = it },
            onSubsonicPasswordChange = { subsonicPasswordInput = it },
            onSaveSubsonic = {
                onSaveSubsonicCredentials(subsonicServerInput, subsonicUserInput, subsonicPasswordInput)
            },
            onSyncSubsonic = {
                remoteSourcesExpanded = true
                subsonicExpanded = true
                onSyncSubsonicLibrary(subsonicServerInput, subsonicUserInput, subsonicPasswordInput)
            },
            onClearSubsonic = {
                subsonicServerInput = ""
                subsonicUserInput = ""
                subsonicPasswordInput = ""
                onClearSubsonicCredentials()
            },
            onWebDavServerUrlChange = { webDavServerInput = it },
            onWebDavUsernameChange = { webDavUserInput = it },
            onWebDavPasswordChange = { webDavPasswordInput = it },
            onSaveWebDav = {
                onSaveWebDavCredentials(webDavServerInput, webDavUserInput, webDavPasswordInput)
            },
            onSyncWebDav = {
                remoteSourcesExpanded = true
                webDavExpanded = true
                onSyncWebDavLibrary(webDavServerInput, webDavUserInput, webDavPasswordInput)
            },
            onCancel = onCancelRemoteSync,
            onClearWebDav = {
                webDavServerInput = ""
                webDavUserInput = ""
                webDavPasswordInput = ""
                onClearWebDavCredentials()
            },
        )
        Spacer(Modifier.height(10.dp))
        ServiceCard(
            name = stringResource(L10nR.string.feature_connect_local_library_579638),
            subtitle = stringResource(L10nR.string.feature_connect_local_audio_files_already_scanned_619627),
            icon = Icons.Rounded.LibraryMusic,
            brandColor = Color(0xFF35C28E),
            statusLabel = stringResource(L10nR.string.feature_connect_connected_6b85ee),
            active = true,
            locked = false,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        ServiceCard(
            name = "Discord Rich Presence",
            subtitle = discordPresenceTrackTitle?.let {
                stringResource(L10nR.string.feature_connect_playing_on_phone_it_a1815e, (it).toString())
            } ?: stringResource(L10nR.string.feature_connect_forward_phone_playback_through_pc_echo_ca17b2),
            icon = Icons.Rounded.GraphicEq,
            brandColor = Color(0xFF5865F2),
            statusLabel = when {
                !discordPresenceEnabled -> stringResource(L10nR.string.feature_connect_off_d48859)
                discordPresenceReady -> stringResource(L10nR.string.feature_connect_ready_to_send_832298)
                else -> stringResource(L10nR.string.feature_connect_waiting_for_pc_66972e)
            },
            active = discordPresenceEnabled && discordPresenceReady,
            locked = !discordPresenceEnabled,
            onClick = {},
        )
        Spacer(Modifier.height(20.dp))
        EchoSectionTitle(
            stringResource(L10nR.string.feature_connect_device_link_e4f412),
            if (connected) {
                stringResource(L10nR.string.feature_connect_control_on_phone_output_on_pc_fe2fbb)
            } else {
                stringResource(L10nR.string.feature_connect_take_over_pc_echo_playback_after_pairing_b11731)
            },
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (dark) Color.White.copy(alpha = 0.08f) else scheme.surface.copy(alpha = 0.70f),
                            if (dark) EchoGlassPanel.copy(alpha = 0.34f) else EchoHomeMist.copy(alpha = 0.62f),
                            if (dark) EchoGlassCyan.copy(alpha = 0.20f) else scheme.primary.copy(alpha = 0.08f),
                            if (dark) EchoGlassViolet.copy(alpha = 0.14f) else scheme.primary.copy(alpha = 0.08f),
                        ),
                    ),
                )
                .border(
                    if (dark) echoDarkGlassBorder(connected) else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)),
                    RoundedCornerShape(24.dp),
                ),
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(echoAccentColor()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = echoOnAccentColor(),
                            modifier = Modifier.size(25.dp),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            pcTitle,
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            remoteConnectionLabel(remoteState),
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    ServiceStatusPill(
                        label = if (connected) {
                            stringResource(L10nR.string.feature_connect_paired_8c3083)
                        } else {
                            stringResource(L10nR.string.feature_connect_unpaired_89e494)
                        },
                        active = connected,
                        locked = false,
                    )
                }
                if (connected) {
                    RemoteNowPlaying(
                        title = trackTitle,
                        artist = trackArtist,
                        artworkUrl = trackArtworkUrl,
                        isPlaying = isPlaying,
                        controlsEnabled = true,
                        onPlayPause = onPlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                    )
                } else {
                    PcPairingInputs(
                        address = pcAddressInput,
                        token = pcTokenInput,
                        hasSavedPc = hasSavedPc,
                        autoReconnectEnabled = autoReconnectEnabled,
                        scanMessage = scanMessage,
                        scanMessageIsError = scanMessageIsError,
                        discoveredLanDevices = discoveredLanDevices,
                        onSelectLanDevice = { device ->
                            pcAddressInput = EchoLinkDiscoveryPolicy.addressLabel(device)
                            pcTokenInput = EchoLinkDiscoveryPolicy.tokenAfterSelecting(
                                device = device,
                                savedAddress = savedPcAddress,
                                savedToken = savedPcToken,
                            )
                            onSelectLanDevice(device)
                        },
                        onRefreshLanDevices = onRefreshLanDevices,
                        onAddressChange = { pcAddressInput = it },
                        onTokenChange = { pcTokenInput = it },
                        onAutoReconnectChange = onAutoReconnectChange,
                        onScanPairingCode = onScanPairingCode,
                    )
                }
                LinkedLibraryDefaultRow(
                    checked = linkedLibraryDefault,
                    connected = connected,
                    onCheckedChange = onLinkedLibraryDefaultChange,
                )
                remoteError?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        error,
                        color = scheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EchoTextButton(
                        text = if (connected) {
                            stringResource(L10nR.string.feature_connect_connected_6b85ee)
                        } else {
                            stringResource(L10nR.string.feature_connect_connect_pc_724ba0)
                        },
                        onClick = { onConnectPc(pcAddressInput, pcTokenInput) },
                        enabled = !connected && canConnectPc,
                    )
                    if (connected) {
                        TextButton(onClick = onDisconnect) {
                            Text(stringResource(L10nR.string.feature_connect_disconnect_b0144f), color = echoAccentColor())
                        }
                    } else if (hasSavedPc) {
                        TextButton(
                            onClick = {
                                pcAddressInput = ""
                                pcTokenInput = ""
                                onForgetPc()
                            },
                        ) {
                            Text(stringResource(L10nR.string.feature_connect_forget_30df50), color = echoAccentColor())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PcPairingInputs(
    address: String,
    token: String,
    hasSavedPc: Boolean,
    autoReconnectEnabled: Boolean,
    scanMessage: String?,
    scanMessageIsError: Boolean,
    discoveredLanDevices: List<EchoLinkLanDevice>,
    onSelectLanDevice: (EchoLinkLanDevice) -> Unit,
    onRefreshLanDevices: () -> Unit,
    onAddressChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onScanPairingCode: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(echoGlassRowBrush(accent = EchoGlassCyan))
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.70f)),
                RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(L10nR.string.feature_connect_nearby_pcs_then_address_and_pairing_token_d5b4e2),
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RemoteCompactAction(
                text = stringResource(L10nR.string.feature_connect_refresh_828c69),
                enabled = true,
                modifier = Modifier.width(72.dp),
                onClick = onRefreshLanDevices,
            )
        }
        if (discoveredLanDevices.isEmpty()) {
            Text(
                stringResource(L10nR.string.feature_connect_no_lan_pcs_yet_scan_a_qr_code_0b3f56),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                discoveredLanDevices.forEach { device ->
                    val selected = EchoLinkDiscoveryPolicy.addressMatchesDevice(address, device)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) echoAccentColor().copy(alpha = 0.18f)
                                else scheme.surface.copy(alpha = 0.35f),
                            )
                            .echoClickable { onSelectLanDevice(device) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = echoAccentColor(),
                            modifier = Modifier.size(18.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.name,
                                color = scheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                EchoLinkDiscoveryPolicy.addressLabel(device),
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Text(
            stringResource(L10nR.string.feature_connect_supports_192_168_1_12_26789_or_a_7d7e27),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(L10nR.string.feature_connect_scan_the_qr_code_shown_on_pc_to_e7ce7b),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RemoteCompactAction(
                text = stringResource(L10nR.string.feature_connect_scan_qr_b42f5b),
                enabled = true,
                modifier = Modifier.width(92.dp),
                onClick = onScanPairingCode,
            )
        }
        scanMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                message,
                color = if (scanMessageIsError) scheme.error else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RemoteTextInput(
            label = stringResource(L10nR.string.feature_connect_pc_address_4be94c),
            value = address,
            placeholder = "192.168.1.12:26789",
            onValueChange = onAddressChange,
        )
        RemoteTextInput(
            label = stringResource(L10nR.string.feature_connect_pairing_token_2e95dd),
            value = token,
            placeholder = stringResource(L10nR.string.feature_connect_copy_from_the_pc_echo_link_page_ce2ca6),
            secret = token.isNotBlank(),
            onValueChange = onTokenChange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(L10nR.string.feature_connect_auto_reconnect_5259cc),
                    color = if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (hasSavedPc) {
                        stringResource(L10nR.string.feature_connect_reconnect_to_the_last_pc_when_connect_opens_e13dd4)
                    } else {
                        stringResource(L10nR.string.feature_connect_available_after_a_successful_saved_connection_f08f11)
                    },
                    color = if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.70f) else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EchoConnectSwitch(
                checked = autoReconnectEnabled,
                onCheckedChange = onAutoReconnectChange,
                enabled = hasSavedPc,
            )
        }
    }
}

@Composable
private fun LinkedLibraryDefaultRow(
    checked: Boolean,
    connected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (checked) echoAccentColor().copy(alpha = if (dark) 0.18f else 0.10f)
                else if (dark) EchoGlassPanel.copy(alpha = 0.44f) else scheme.surfaceVariant.copy(alpha = 0.24f),
            )
            .border(
                if (dark) echoDarkGlassBorder(checked) else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.64f)),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(L10nR.string.feature_connect_use_linked_library_by_default_a9eb0f),
                color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    checked && connected -> stringResource(L10nR.string.feature_connect_the_linked_echo_library_is_shown_separately_with_89ff2b)
                    checked -> stringResource(L10nR.string.feature_connect_after_connecting_read_pc_echo_automatically_without_merging_d0f676)
                    else -> stringResource(L10nR.string.feature_connect_the_local_library_stays_default_refresh_the_linked_6c5cd0)
                },
                color = if (dark) Color.White.copy(alpha = 0.70f) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EchoConnectSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun EchoConnectSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = scheme.primary.copy(alpha = if (dark) 0.86f else 0.76f),
            checkedBorderColor = Color.White.copy(alpha = if (dark) 0.28f else 0.52f),
            uncheckedThumbColor = if (dark) Color.White.copy(alpha = 0.58f) else scheme.onSurfaceVariant.copy(alpha = 0.72f),
            uncheckedTrackColor = if (dark) Color.White.copy(alpha = 0.14f) else scheme.outlineVariant.copy(alpha = 0.55f),
            uncheckedBorderColor = if (dark) Color.White.copy(alpha = 0.22f) else scheme.outlineVariant.copy(alpha = 0.76f),
            disabledUncheckedThumbColor = if (dark) Color.White.copy(alpha = 0.30f) else scheme.onSurfaceVariant.copy(alpha = 0.36f),
            disabledUncheckedTrackColor = if (dark) Color.White.copy(alpha = 0.08f) else scheme.outlineVariant.copy(alpha = 0.30f),
            disabledUncheckedBorderColor = if (dark) Color.White.copy(alpha = 0.12f) else scheme.outlineVariant.copy(alpha = 0.34f),
        ),
    )
}

@Composable
private fun RemoteSourcesPanel(
    subsonicServerUrl: String,
    subsonicUsername: String,
    subsonicPassword: String,
    webDavServerUrl: String,
    webDavUsername: String,
    webDavPassword: String,
    expanded: Boolean,
    subsonicExpanded: Boolean,
    webDavExpanded: Boolean,
    scanState: LibraryScanProgress,
    onExpandedChange: (Boolean) -> Unit,
    onSubsonicExpandedChange: (Boolean) -> Unit,
    onWebDavExpandedChange: (Boolean) -> Unit,
    onSubsonicServerUrlChange: (String) -> Unit,
    onSubsonicUsernameChange: (String) -> Unit,
    onSubsonicPasswordChange: (String) -> Unit,
    onSaveSubsonic: () -> Unit,
    onSyncSubsonic: () -> Unit,
    onClearSubsonic: () -> Unit,
    onWebDavServerUrlChange: (String) -> Unit,
    onWebDavUsernameChange: (String) -> Unit,
    onWebDavPasswordChange: (String) -> Unit,
    onSaveWebDav: () -> Unit,
    onSyncWebDav: () -> Unit,
    onCancel: () -> Unit,
    onClearWebDav: () -> Unit,
) {
    val subsonicReady = subsonicServerUrl.isNotBlank() && subsonicUsername.isNotBlank() && subsonicPassword.isNotBlank()
    val webDavReady = webDavServerUrl.isNotBlank() && webDavUsername.isNotBlank() && webDavPassword.isNotBlank()
    val readyCount = (if (subsonicReady) 1 else 0) + (if (webDavReady) 1 else 0)
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (dark) Color.White.copy(alpha = 0.08f) else scheme.surface.copy(alpha = 0.70f),
                        if (dark) EchoGlassPanel.copy(alpha = 0.58f) else scheme.surface.copy(alpha = 0.70f),
                        echoAccentColor().copy(alpha = if (dark) 0.20f else 0.12f),
                        if (dark) EchoGlassViolet.copy(alpha = 0.13f) else EchoHomeMist.copy(alpha = 0.28f),
                    ),
                ),
            )
            .border(
                if (dark) echoDarkGlassBorder(readyCount > 0) else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)),
                RoundedCornerShape(20.dp),
            )
            .padding(15.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .echoClickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(echoAccentColor()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = echoOnAccentColor(), modifier = Modifier.size(25.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(L10nR.string.feature_connect_remote_libraries_520815),
                        color = scheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        remoteSourcesSummary(scanState, readyCount),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ServiceStatusPill(
                    label = if (scanState.isScanning) {
                        stringResource(L10nR.string.feature_connect_syncing_ac4e35)
                    } else if (readyCount > 0) {
                        stringResource(L10nR.string.feature_connect_readycount_ready_ad163c, (readyCount).toString())
                    } else {
                        stringResource(L10nR.string.feature_connect_not_set_up_287510)
                    },
                    active = readyCount > 0 && scanState.phase != LibraryScanPhase.Error,
                    locked = readyCount == 0 && !scanState.isScanning,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        stringResource(L10nR.string.feature_connect_collapse_remote_libraries_61a9c7)
                    } else {
                        stringResource(L10nR.string.feature_connect_expand_remote_libraries_a8df3f)
                    },
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).echoExpandIndicator(expanded),
                )
            }
            EchoExpand(expanded = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteSourceProviderSection(
                    title = "Subsonic / Navidrome",
                    subtitle = stringResource(L10nR.string.feature_connect_sync_the_server_library_artwork_and_playback_urls_cad58c),
                    serverLabel = stringResource(L10nR.string.feature_connect_server_address_ec245e),
                    serverPlaceholder = "https://music.example.com",
                    usernameLabel = stringResource(L10nR.string.feature_connect_username_dfb030),
                    passwordLabel = stringResource(L10nR.string.feature_connect_password_2eaf7d),
                    serverUrl = subsonicServerUrl,
                    username = subsonicUsername,
                    password = subsonicPassword,
                    expanded = subsonicExpanded,
                    expandable = true,
                    scanState = scanState,
                    onExpandedChange = onSubsonicExpandedChange,
                    onServerUrlChange = onSubsonicServerUrlChange,
                    onUsernameChange = onSubsonicUsernameChange,
                    onPasswordChange = onSubsonicPasswordChange,
                    onSave = onSaveSubsonic,
                    onSync = onSyncSubsonic,
                    onCancel = onCancel,
                    onClear = onClearSubsonic,
                )
                RemoteSourceProviderSection(
                    title = stringResource(L10nR.string.feature_connect_webdav_cloud_drive_3aa8bf),
                    subtitle = stringResource(L10nR.string.feature_connect_sync_a_nas_or_cloud_music_folder_9fa330),
                    serverLabel = stringResource(L10nR.string.feature_connect_webdav_address_a59b28),
                    serverPlaceholder = "https://dav.example.com/music",
                    usernameLabel = stringResource(L10nR.string.feature_connect_webdav_username_946873),
                    passwordLabel = stringResource(L10nR.string.feature_connect_webdav_password_4443d4),
                    serverUrl = webDavServerUrl,
                    username = webDavUsername,
                    password = webDavPassword,
                    expanded = webDavExpanded,
                    expandable = true,
                    scanState = scanState,
                    onExpandedChange = onWebDavExpandedChange,
                    onServerUrlChange = onWebDavServerUrlChange,
                    onUsernameChange = onWebDavUsernameChange,
                    onPasswordChange = onWebDavPasswordChange,
                    onSave = onSaveWebDav,
                    onSync = onSyncWebDav,
                    onCancel = onCancel,
                    onClear = onClearWebDav,
                )
                }
            }
        }
    }
}

@Composable
private fun RemoteSourceProviderSection(
    title: String,
    subtitle: String,
    serverLabel: String,
    serverPlaceholder: String,
    usernameLabel: String,
    passwordLabel: String,
    serverUrl: String,
    username: String,
    password: String,
    expanded: Boolean,
    expandable: Boolean,
    scanState: LibraryScanProgress,
    onExpandedChange: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onSync: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    val ready = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    val hasInput = serverUrl.isNotBlank() || username.isNotBlank() || password.isNotBlank()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val statusLabel = when {
        scanState.isScanning && expanded -> stringResource(L10nR.string.feature_connect_syncing_ac4e35)
        ready -> stringResource(L10nR.string.feature_connect_ready_to_sync_47b40c)
        hasInput -> stringResource(L10nR.string.feature_connect_incomplete_4522f2)
        else -> stringResource(L10nR.string.feature_connect_not_set_up_287510)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (dark) {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.06f),
                            EchoGlassPanel.copy(alpha = 0.24f),
                            EchoGlassInk.copy(alpha = 0.14f),
                            if (ready) EchoGlassCyan.copy(alpha = 0.14f) else EchoGlassViolet.copy(alpha = 0.10f),
                        ),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            scheme.surface.copy(alpha = 0.30f),
                            EchoHomeMist.copy(alpha = 0.26f),
                        ),
                    )
                },
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (ready) scheme.primary.copy(alpha = if (dark) 0.36f else 0.24f)
                    else if (dark) EchoDarkGlassBorder
                    else EchoGlassBorder.copy(alpha = 0.58f),
                ),
                RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expandable) Modifier.echoClickable { onExpandedChange(!expanded) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (expanded) 38.dp else 30.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (ready) echoAccentColor() else if (dark) Color.White.copy(alpha = 0.20f) else scheme.outlineVariant.copy(alpha = 0.82f)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    remoteLibraryDetail(scanState, ready).takeIf { expanded && scanState.phase != LibraryScanPhase.Idle } ?: subtitle,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ServiceStatusPill(
                label = statusLabel,
                active = ready && scanState.phase != LibraryScanPhase.Error,
                locked = !ready && !scanState.isScanning,
            )
            if (expandable) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        stringResource(L10nR.string.feature_connect_collapse_970e1a)
                    } else {
                        stringResource(L10nR.string.feature_connect_expand_6a79dd)
                    },
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).echoExpandIndicator(expanded),
                )
            }
        }
        EchoExpand(expanded = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteTextInput(
                label = serverLabel,
                value = serverUrl,
                placeholder = serverPlaceholder,
                onValueChange = onServerUrlChange,
            )
            RemoteTextInput(
                label = usernameLabel,
                value = username,
                placeholder = stringResource(L10nR.string.feature_connect_username_dfb030),
                onValueChange = onUsernameChange,
            )
            RemoteTextInput(
                label = passwordLabel,
                value = password,
                placeholder = stringResource(L10nR.string.feature_connect_password_or_app_password_cf402d),
                secret = true,
                onValueChange = onPasswordChange,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RemoteCompactAction(
                    text = stringResource(L10nR.string.feature_connect_save_68ae20),
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                )
                RemoteCompactAction(
                    text = if (scanState.isScanning) {
                        stringResource(L10nR.string.feature_connect_cancel_4c5fa5)
                    } else {
                        stringResource(L10nR.string.feature_connect_sync_b57352)
                    },
                    enabled = ready || scanState.isScanning,
                    modifier = Modifier.weight(1f),
                    onClick = { if (scanState.isScanning) onCancel() else onSync() },
                )
                RemoteCompactAction(
                    text = stringResource(L10nR.string.feature_connect_clear_ec6a55),
                    enabled = hasInput,
                    modifier = Modifier.weight(1f),
                    onClick = onClear,
                )
            }
            }
        }
    }
}

@Composable
private fun RemoteTextInput(
    label: String,
    value: String,
    placeholder: String,
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(15.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            focusedContainerColor = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.48f),
            unfocusedContainerColor = if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.34f),
            focusedBorderColor = scheme.primary.copy(alpha = if (dark) 0.62f else 0.42f),
            unfocusedBorderColor = if (dark) EchoDarkGlassBorder else EchoGlassBorder.copy(alpha = 0.74f),
            cursorColor = scheme.primary,
            focusedLabelColor = scheme.primary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            focusedPlaceholderColor = scheme.onSurfaceVariant.copy(alpha = 0.72f),
            unfocusedPlaceholderColor = scheme.onSurfaceVariant.copy(alpha = 0.58f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RemoteCompactAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (enabled) scheme.primary.copy(alpha = if (dark) 0.20f else 0.13f)
                else if (dark) Color.White.copy(alpha = 0.08f) else scheme.surfaceVariant.copy(alpha = 0.28f),
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (dark) EchoDarkGlassBorder else EchoGlassBorder.copy(alpha = 0.62f),
                ),
                RoundedCornerShape(13.dp),
            )
            .then(if (enabled) Modifier.echoClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun remoteLibraryDetail(scanState: LibraryScanProgress, ready: Boolean): String =
    when {
        scanState.isScanning -> {
            val phaseLabel = remoteScanPhaseLabel(scanState.phase)
            buildString {
                append(phaseLabel)
                append(" · ")
                append(scanState.totalCount?.let { "${scanState.scannedCount}/$it" } ?: scanState.scannedCount.toString())
                scanState.currentTitle?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
        }
        scanState.phase == LibraryScanPhase.Completed -> stringResource(L10nR.string.feature_connect_sync_complete_scanstate_scannedcount_tracks_scanstate_insertedco_41ae3e, (scanState.scannedCount).toString(), (scanState.insertedCount).toString(), (scanState.updatedCount).toString(), (scanState.deletedCount).toString())
        scanState.phase == LibraryScanPhase.Error -> scanState.error ?: stringResource(L10nR.string.feature_connect_remote_library_sync_failed_e55eb1)
        ready -> stringResource(L10nR.string.feature_connect_ready_to_sync_to_the_cloud_album_wall_20a1e5)
        else -> stringResource(L10nR.string.feature_connect_enter_the_server_username_and_password_b90327)
    }

@Composable
private fun remoteSourcesSummary(scanState: LibraryScanProgress, readyCount: Int): String =
    when {
        scanState.isScanning -> remoteLibraryDetail(scanState, ready = true)
        scanState.phase == LibraryScanPhase.Completed -> remoteLibraryDetail(scanState, ready = true)
        scanState.phase == LibraryScanPhase.Error -> remoteLibraryDetail(scanState, ready = false)
        readyCount > 0 -> stringResource(L10nR.string.feature_connect_tap_to_expand_subsonic_webdav_fold_separately_a293a2)
        else -> stringResource(L10nR.string.feature_connect_tap_to_set_up_subsonic_webdav_cloud_drive_6f2e97)
    }

@Composable
private fun remoteScanPhaseLabel(phase: LibraryScanPhase): String =
    when (phase) {
        LibraryScanPhase.Preparing -> stringResource(L10nR.string.feature_connect_preparing_sync_f64e14)
        LibraryScanPhase.QueryingMediaStore -> stringResource(L10nR.string.feature_connect_reading_remote_library_06fcd1)
        LibraryScanPhase.Diffing -> stringResource(L10nR.string.feature_connect_comparing_index_7c03bf)
        LibraryScanPhase.WritingDatabase -> stringResource(L10nR.string.feature_connect_writing_library_a8f4bd)
        LibraryScanPhase.CleaningRemoved -> stringResource(L10nR.string.feature_connect_cleaning_old_index_2c0185)
        LibraryScanPhase.Completed -> stringResource(L10nR.string.feature_connect_sync_complete_9ac5cf)
        LibraryScanPhase.Cancelled -> stringResource(L10nR.string.feature_connect_cancelled_4ab41f)
        LibraryScanPhase.Error -> stringResource(L10nR.string.feature_connect_sync_failed_8cc151)
        LibraryScanPhase.Idle -> stringResource(L10nR.string.feature_connect_waiting_to_sync_21ad43)
    }


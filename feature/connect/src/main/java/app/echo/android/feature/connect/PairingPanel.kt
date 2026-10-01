package app.echo.android.feature.connect

import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoLinkModeSwitch

import app.echo.android.feature.connect.R as L10nR

import app.echo.android.connect.EchoLinkDiscoveryState
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkDiscoveryPolicy
import app.echo.android.connect.EchoPairingParser
import app.echo.android.design.EchoExpand
import app.echo.android.design.echoClickable
import app.echo.android.design.echoExpandIndicator
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.connect.EchoSavedPcEndpoint

@Composable
internal fun PcLinkPanel(
    librarySyncActions: LibrarySyncActions? = null,
    remoteState: EchoRemoteConnectionState,
    pcTitle: String,
    trackTitle: String,
    trackArtist: String,
    trackArtworkUrl: String?,
    isPlaying: Boolean,
    remoteError: String?,
    savedPcAddress: String?,
    savedPcToken: String?,
    savedPcs: List<EchoSavedPcEndpoint> = emptyList(),
    autoReconnectEnabled: Boolean,
    linkedLibraryDefault: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    volumeControlEnabled: Boolean = true,
    volumeLockedReason: String? = null,
    outputMode: String = "",
    currentTrackId: String? = null,
    queueItems: List<EchoRemoteTrack> = emptyList(),
    onConnectPc: (String, String) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit = {},
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onPlayQueueItem: (String) -> Unit = {},
    onDisconnect: () -> Unit,
    onForgetPc: () -> Unit,
    onForgetSavedPc: (EchoSavedPcEndpoint) -> Unit = {},
    onAutoReconnectChange: (Boolean) -> Unit,
    onLinkedLibraryDefaultChange: (Boolean) -> Unit,
    discoveryState: EchoLinkDiscoveryState = EchoLinkDiscoveryState.Idle,
    discoveredLanDevices: List<EchoLinkLanDevice>,
    onSelectLanDevice: (EchoLinkLanDevice) -> Unit,
    onRefreshLanDevices: () -> Unit,
    onHandoffPhoneToPc: (() -> Unit)? = null,
    remoteLibrary: EchoRemoteLibraryState = EchoRemoteLibraryState(),
    onSearchPcLibrary: (String) -> Unit = {},
    onLoadMorePcLibrary: () -> Unit = {},
    active: Boolean = true,
    onOpenListening: (() -> Unit)? = null,
    remoteMode: Boolean = true,
    onRemoteModeChange: (Boolean) -> Unit = {},
    onOpenPcLibrary: () -> Unit = {},
    openQueueNonce: Int = 0,
    onQueueRequestHandled: () -> Unit = {},
) {
    var showLibrarySync by remember { mutableStateOf(false) }
    var legacySync by remember { mutableStateOf(false) }
    LaunchedEffect(remoteState, active) {
        if (remoteState != EchoRemoteConnectionState.Connected || !active) showLibrarySync = false
    }
    if (showLibrarySync && librarySyncActions != null) {
        val advanced = librarySyncActions.advanced
        if (!legacySync && advanced != null) LibraryReconcileSheet(advanced,{ legacySync = true },{ showLibrarySync = false })
        else LibrarySyncSheet(pcTitle, librarySyncActions) { showLibrarySync = false }
    }
    val connected = remoteState == EchoRemoteConnectionState.Connected
    val busy = remoteState in listOf(EchoRemoteConnectionState.Pairing, EchoRemoteConnectionState.Connecting, EchoRemoteConnectionState.Reconnecting)
    var address by rememberSaveable(savedPcAddress) { mutableStateOf(savedPcAddress.orEmpty()) }
    var token by rememberSaveable(savedPcToken) { mutableStateOf(savedPcToken.orEmpty()) }
    var manual by rememberSaveable { mutableStateOf(savedPcAddress.isNullOrBlank()) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    var musicPicker by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openQueueNonce) {
        if (openQueueNonce > 0 && connected && remoteMode) {
            musicPicker = "queue"
            onQueueRequestHandled()
        }
    }
    LaunchedEffect(remoteMode) { if (!remoteMode) musicPicker = null }
    val hasSaved = !savedPcAddress.isNullOrBlank()
    val endpoint = remember(address, token) { EchoPairingParser.parseManual(address, token) }
    val validAddress = remember(address) { EchoPairingParser.parseManual(address, "validation-token") != null }
    val keyboard = LocalSoftwareKeyboardController.current
    val connect = { if (endpoint != null && !busy) { keyboard?.hide(); onConnectPc(address.trim(), token.trim()) } }
    val selectPc: (String, String, Boolean) -> Unit = { selectedAddress, selectedToken, requiresPairing ->
        address = selectedAddress
        token = selectedToken
        manual = requiresPairing && selectedToken.isBlank()
        if (!manual && !busy) onConnectPc(selectedAddress, selectedToken)
    }
    LaunchedEffect(remoteState) {
        if (remoteState == EchoRemoteConnectionState.Error) manual = true
        if (!connected) musicPicker = null
    }
    if (connected && active && musicPicker != null) {
        RemoteMusicPicker(
            initialQueue = musicPicker == "queue",
            library = remoteLibrary,
            queueItems = queueItems,
            currentTrackId = currentTrackId,
            pcTitle = pcTitle,
            remoteError = remoteError,
            onSearchLibrary = onSearchPcLibrary,
            onLoadMoreLibrary = onLoadMorePcLibrary,
            onPlayTrack = onPlayQueueItem,
            onDismiss = { musicPicker = null },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (connected || hasSaved) pcTitle else stringResource(L10nR.string.remote_connect_title),
                style = if (connected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold, maxLines = if (connected) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(remoteConnectionLabel(remoteState), color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
            if (!connected) {
                ConnectNote(stringResource(L10nR.string.feature_connect_control_pc_playback_from_your_phone_or_browse_20f8b5))
            }
        }
        if (onOpenListening != null && !connected) {
            ListenTogetherEntry(onOpen = onOpenListening)
        }
        remoteError?.takeIf { it.isNotBlank() }?.let { ConnectNote(it, error = true) }
        if (connected || hasSaved) EchoLinkModeSwitch(remoteMode, onRemoteModeChange)
        if (connected && librarySyncActions != null) OutlinedButton(onClick = { showLibrarySync = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(L10nR.string.sync_title))
        }
        when {
            busy -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                ConnectNote(stringResource(L10nR.string.feature_connect_keep_pc_echo_running_and_both_devices_on_7986ec))
                TextButton(onClick = onDisconnect) { Text(stringResource(L10nR.string.feature_connect_cancel_connection_fd085f)) }
            }
            connected -> {
                if (remoteMode) {
                RemoteNowPlaying(
                    title = trackTitle,
                    artist = trackArtist,
                    artworkUrl = trackArtworkUrl,
                    isPlaying = isPlaying,
                    controlsEnabled = true,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    volume = volume,
                    volumeControlEnabled = volumeControlEnabled,
                    volumeLockedReason = volumeLockedReason,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onStop = onStop,
                    onSeek = onSeek,
                    onVolume = onVolume,
                    onOpenLibrary = { musicPicker = "library" },
                    onOpenQueue = { musicPicker = "queue" },
                    outputMode = outputMode,
                    currentTrackId = currentTrackId,
                    queueCount = queueItems.size,
                    active = active && musicPicker == null,
                )
                if (onHandoffPhoneToPc != null) {
                    TextButton(onClick = onHandoffPhoneToPc) {
                        Text(stringResource(L10nR.string.echo_link_handoff_phone))
                    }
                }
                } else {
                    Button(onClick = onOpenPcLibrary, modifier = Modifier.fillMaxWidth(), shape = ConnectControlShape) {
                        Text(stringResource(L10nR.string.echo_link_open_pc_library))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (onOpenListening != null) {
                        TextButton(onClick = onOpenListening) {
                            Text(stringResource(L10nR.string.feature_connect_listen_together_title))
                        }
                    }
                    TextButton(onClick = onDisconnect) { Text(stringResource(L10nR.string.feature_connect_disconnect_pc_fd446c)) }
                }
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
                                EchoIcon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(pc.name, style = MaterialTheme.typography.bodyMedium)
                                    ConnectNote(pc.address)
                                }
                                TextButton(
                                    onClick = { selectPc(pc.address, pc.token.orEmpty(), false) },
                                    enabled = !busy,
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
                    Button(onClick = { selectPc(savedPcAddress.orEmpty(), savedPcToken.orEmpty(), false) }, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(L10nR.string.feature_connect_reconnect_pc_ea8c5f))
                    }
                }
                ConnectNote(stringResource(L10nR.string.echo_link_direct_address_hint))
                ConnectSection(stringResource(L10nR.string.feature_connect_nearby_pcs_b9b12a),
                    action = { TextButton(onClick = onRefreshLanDevices) { Text(stringResource(L10nR.string.feature_connect_refresh_828c69)) } }) {
                    if (discoveredLanDevices.isEmpty()) {
                        if (discoveryState == EchoLinkDiscoveryState.Searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                        ConnectNote(stringResource(when (discoveryState) {
                            EchoLinkDiscoveryState.Searching -> L10nR.string.echo_link_lan_searching
                            EchoLinkDiscoveryState.Failed -> L10nR.string.echo_link_lan_failed
                            else -> L10nR.string.feature_connect_no_pc_found_use_the_same_wi_fi_ce621e
                        }), error = discoveryState == EchoLinkDiscoveryState.Failed)
                    }
                    discoveredLanDevices.forEach { device ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).echoClickable(role = Role.Button) {
                            val saved = savedPcs.firstOrNull { EchoLinkDiscoveryPolicy.addressMatchesDevice(it.address, device) }
                            val selectedToken = saved?.token?.takeIf { it.isNotBlank() }
                                ?: EchoLinkDiscoveryPolicy.tokenAfterSelecting(device, savedPcAddress, savedPcToken)
                            selectPc(EchoLinkDiscoveryPolicy.addressLabel(device), selectedToken, device.requiresPairing)
                            onSelectLanDevice(device)
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EchoIcon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.bodyMedium)
                                ConnectNote(EchoLinkDiscoveryPolicy.addressLabel(device))
                            }
                            Text(stringResource(if (device.requiresPairing) L10nR.string.feature_connect_select_21bce2 else L10nR.string.feature_connect_connect_c7c091), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).echoClickable(role = Role.Button) { keyboard?.hide(); manual = !manual }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(L10nR.string.echo_link_pairing_entry), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        EchoIcon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, Modifier.echoExpandIndicator(manual))
                    }
                    EchoExpand(manual) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ConnectInput(stringResource(L10nR.string.feature_connect_pc_address_or_pairing_link_a3e680), address, { address = it; token = "" }, "192.168.1.20:26789", url = true, onDone = connect,
                                error = if (address.isNotBlank() && !validAddress) stringResource(L10nR.string.feature_connect_check_the_pc_address_or_paste_a_complete_0f216a) else null)
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

@Composable
private fun ListenTogetherEntry(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).echoClickable(role = Role.Button, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EchoIcon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(L10nR.string.feature_connect_listen_together_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            ConnectNote(stringResource(L10nR.string.feature_connect_listen_together_detail))
        }
    }
}

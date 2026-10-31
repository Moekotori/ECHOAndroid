package app.echo.android.feature.connect

import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoLinkModeSwitch

import app.echo.android.feature.connect.R as L10nR

import app.echo.android.connect.EchoLinkDiscoveryState
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkDiscoveryPolicy
import app.echo.android.connect.EchoPairingParser
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.connect.EchoSavedPcEndpoint

@Composable
internal fun PcLinkPanel(
    librarySyncActions: LibrarySyncActions? = null,
    phoneLibraryShareContent: @Composable () -> Unit = {},
    remoteState: EchoRemoteConnectionState,
    controlPending: Boolean = false,
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
    onOpenFullQueue: (() -> Unit)? = null,
    queueTotalCount: Int = queueItems.size,
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
    libraryOnly: Boolean = false,
    onRemoteModeChange: (Boolean) -> Unit = {},
    onOpenPcLibrary: () -> Unit = {},
    openQueueNonce: Int = 0,
    onQueueRequestHandled: () -> Unit = {},
    openConnectionNonce: Int = 0,
    onConnectionRequestHandled: () -> Unit = {},
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
    val connectionEntry = remember { BringIntoViewRequester() }
    LaunchedEffect(openConnectionNonce) {
        if (openConnectionNonce > 0 && !connected && !busy) {
            manual = true
            withFrameNanos { }
            connectionEntry.bringIntoView()
        }
        if (openConnectionNonce > 0) onConnectionRequestHandled()
    }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    var musicPicker by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openQueueNonce) {
        if (openQueueNonce > 0 && connected && remoteMode) {
            musicPicker = "queue"
            onQueueRequestHandled()
        }
    }
    LaunchedEffect(remoteMode) { if (!remoteMode) musicPicker = null }
    @Composable
    fun PlaybackControls(recovering: Boolean) {
                RemoteNowPlaying(
                    title = trackTitle,
                    artist = trackArtist,
                    artworkUrl = trackArtworkUrl,
                    isPlaying = isPlaying,
                    controlsEnabled = true,
                    recovering = recovering,
                    pending = controlPending,
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
                    onOpenQueue = { onOpenFullQueue?.invoke() ?: run { musicPicker = "queue" } },
                    outputMode = outputMode,
                    currentTrackId = currentTrackId,
                    queueCount = queueTotalCount,
                    active = active && musicPicker == null,
                )
    }
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
    val listenTitle = stringResource(L10nR.string.feature_connect_listen_together_title)
    val listenDetail = stringResource(L10nR.string.feature_connect_listen_together_detail)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PcLinkHero(
            title = if (connected || hasSaved) pcTitle else stringResource(L10nR.string.remote_connect_title),
            state = remoteState,
            showRequirement = !connected && !busy,
            error = remoteError,
        ) {
            when {
                busy -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    ConnectNote(stringResource(L10nR.string.feature_connect_keep_pc_echo_running_and_both_devices_on_7986ec))
                    TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(L10nR.string.feature_connect_cancel_connection_fd085f))
                    }
                }
                connected -> TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(L10nR.string.feature_connect_disconnect_pc_fd446c))
                }
                hasSaved && !manual && savedPcs.isEmpty() -> Button(
                    onClick = { selectPc(savedPcAddress.orEmpty(), savedPcToken.orEmpty(), false) },
                    shape = ConnectControlShape, modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(L10nR.string.feature_connect_reconnect_pc_ea8c5f)) }
            }
        }
        if (!libraryOnly && (connected || hasSaved)) {
            ConnectCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                EchoLinkModeSwitch(remoteMode, onRemoteModeChange)
            }
        }
        when {
            busy -> if (remoteState == EchoRemoteConnectionState.Reconnecting && remoteMode && currentTrackId != null) {
                ConnectCard { PlaybackControls(true) }
            }
            connected -> {
                if (remoteMode && !libraryOnly) {
                    ConnectCard { PlaybackControls(false) }
                } else {
                    Button(onClick = onOpenPcLibrary, modifier = Modifier.fillMaxWidth(), shape = ConnectControlShape) {
                        Text(stringResource(if (libraryOnly) L10nR.string.echo_bridge_open_library else L10nR.string.echo_link_open_pc_library))
                    }
                }
                val syncTitle = stringResource(L10nR.string.sync_title)
                val handoffTitle = stringResource(L10nR.string.echo_link_handoff_phone)
                ConnectActionList(buildList {
                    if (!libraryOnly && librarySyncActions != null) {
                        add(ConnectAction(Icons.Rounded.Sync, syncTitle, null) { showLibrarySync = true })
                    }
                    if (!libraryOnly && onOpenListening != null) {
                        add(ConnectAction(Icons.Rounded.Headphones, listenTitle, listenDetail, onOpenListening))
                    }
                    if (remoteMode && !libraryOnly && onHandoffPhoneToPc != null) {
                        add(ConnectAction(Icons.Rounded.SwapHoriz, handoffTitle, null, onHandoffPhoneToPc))
                    }
                })
            }
            else -> {
                if (savedPcs.isNotEmpty() && !manual) {
                    ConnectSection(stringResource(L10nR.string.feature_connect_saved_pcs_4d2e91), list = true) {
                        savedPcs.asReversed().forEachIndexed { index, pc ->
                            if (index > 0) ConnectRowDivider()
                            PcDeviceRow(
                                name = pc.name,
                                address = pc.address,
                                actionLabel = stringResource(L10nR.string.feature_connect_connect_c7c091),
                                onConnect = { selectPc(pc.address, pc.token.orEmpty(), false) },
                                onForget = { onForgetSavedPc(pc) },
                            )
                        }
                    }
                }
                ConnectSection(stringResource(L10nR.string.feature_connect_nearby_pcs_b9b12a), list = true,
                    action = {
                        IconButton(onClick = onRefreshLanDevices) {
                            EchoIcon(Icons.Rounded.Refresh, stringResource(L10nR.string.feature_connect_refresh_828c69))
                        }
                    }) {
                    if (discoveredLanDevices.isEmpty()) {
                        PcDiscoveryEmptyState(discoveryState)
                    }
                    discoveredLanDevices.forEachIndexed { index, device ->
                        if (index > 0) ConnectRowDivider()
                        PcDeviceRow(
                            name = device.name,
                            address = if (device.libraryOnly) stringResource(L10nR.string.echo_bridge_library_note)
                                else EchoLinkDiscoveryPolicy.addressLabel(device),
                            actionLabel = stringResource(if (device.requiresPairing) L10nR.string.feature_connect_select_21bce2 else L10nR.string.feature_connect_connect_c7c091),
                            onConnect = {
                                val saved = savedPcs.firstOrNull { EchoLinkDiscoveryPolicy.addressMatchesDevice(it.address, device) }
                                val selectedToken = if (device.libraryOnly) "" else saved?.token?.takeIf { it.isNotBlank() }
                                    ?: EchoLinkDiscoveryPolicy.tokenAfterSelecting(device, savedPcAddress, savedPcToken)
                                selectPc(EchoLinkDiscoveryPolicy.addressLabel(device), selectedToken, device.requiresPairing)
                                onSelectLanDevice(device)
                            },
                        )
                    }
                }
                PcManualEntry(
                    expanded = manual,
                    onToggle = { keyboard?.hide(); manual = !manual },
                    address = address,
                    onAddressChange = { address = it; token = "" },
                    validAddress = validAddress,
                    canConnect = endpoint != null,
                    onConnect = connect,
                    modifier = Modifier.bringIntoViewRequester(connectionEntry),
                )
            }
        }
        if (onOpenListening != null && !connected) {
            ConnectActionList(listOf(ConnectAction(Icons.Rounded.Headphones, listenTitle, listenDetail, onOpenListening)))
        }
        phoneLibraryShareContent()
        PcLinkPreferences(
            hasSaved = hasSaved,
            busy = busy,
            autoReconnectEnabled = autoReconnectEnabled,
            linkedLibraryDefault = linkedLibraryDefault,
            onAutoReconnectChange = onAutoReconnectChange,
            onLinkedLibraryDefaultChange = onLinkedLibraryDefaultChange,
            onForgetRequest = { confirmForget = true },
        )
        Box(Modifier.padding(horizontal = 4.dp)) { ConnectNote(stringResource(L10nR.string.echo_link_battery_hint)) }
    }
    if (confirmForget) ForgetConnectionDialog(
        stringResource(L10nR.string.feature_connect_forget_saved_pc_80b440),
        onDismiss = { confirmForget = false },
        onConfirm = { confirmForget = false; address = ""; token = ""; onForgetPc() },
    )
}

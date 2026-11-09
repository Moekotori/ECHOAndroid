package app.echo.android.feature.connect

import app.echo.android.design.EchoIcon

import app.echo.android.feature.connect.R as L10nR

import app.echo.android.connect.EchoLinkDiscoveryState
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkDiscoveryPolicy
import app.echo.android.connect.EchoManualConnectionInput
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.connect.EchoSavedPcEndpoint

@Composable
internal fun PcLinkPanel(
    librarySyncActions: LibrarySyncActions? = null,
    phoneLibraryShareContent: @Composable () -> Unit = {},
    phonePresenceContent: @Composable () -> Unit = {},
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
    onOpenPcLibrary: () -> Unit = {},
    openQueueNonce: Int = 0,
    onQueueRequestHandled: () -> Unit = {},
    openConnectionNonce: Int = 0,
    onConnectionRequestHandled: () -> Unit = {},
    onOpenWifiTransfer: (() -> Unit)? = null,
    onOpenLinkSongTransfer: (() -> Unit)? = null,
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
    var manual by rememberSaveable { mutableStateOf(false) }
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
    var savedPcToForget by rememberSaveable { mutableStateOf<String?>(null) }
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
    val currentPc = remember(savedPcAddress, savedPcToken, pcTitle) {
        savedPcAddress?.takeIf { it.isNotBlank() }?.let { EchoSavedPcEndpoint(it, pcTitle, token = savedPcToken) }
    }
    val endpoint = remember(address, savedPcs, currentPc) {
        EchoManualConnectionInput.resolve(address, savedPcs, currentPc)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val connect = { if (endpoint != null && !busy) { keyboard?.hide(); onConnectPc(endpoint.address, endpoint.token) } }
    val connectDevice: (String, String) -> Unit = { deviceAddress, token ->
        if (!busy) {
            keyboard?.hide()
            address = deviceAddress
            manual = false
            onConnectPc(deviceAddress, token)
        }
    }
    LaunchedEffect(remoteState) {
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
    val manualError = remoteError?.takeIf { manual && remoteState == EchoRemoteConnectionState.Error }
    val reconnect = !busy && !connected && hasSaved && savedPcs.isEmpty()
    val actionLabel = when {
        busy -> stringResource(L10nR.string.feature_connect_cancel_connection_fd085f)
        connected -> stringResource(L10nR.string.feature_connect_disconnect_pc_fd446c)
        reconnect -> stringResource(L10nR.string.feature_connect_reconnect_pc_ea8c5f)
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PcLinkSummary(
            title = if (connected || hasSaved) pcTitle else stringResource(L10nR.string.remote_connect_title),
            state = remoteState,
            showRequirement = !connected && !busy,
            error = if (manualError == null) remoteError else null,
            action = actionLabel?.let { label ->
                {
                    TextButton(onClick = {
                        if (reconnect) connectDevice(savedPcAddress.orEmpty(), savedPcToken.orEmpty())
                        else onDisconnect()
                    }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(label, color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current)
                    }
                }
            },
        )
        when {
            busy -> if (remoteState == EchoRemoteConnectionState.Reconnecting && remoteMode && currentTrackId != null) {
                PlaybackControls(true)
            }
            connected -> {
                if (remoteMode && !libraryOnly) PlaybackControls(false)
                val libraryTitle = stringResource(if (libraryOnly) L10nR.string.echo_bridge_open_library else L10nR.string.echo_link_open_pc_library)
                val libraryDetail = if (libraryOnly) null else stringResource(app.echo.android.design.R.string.echo_link_mode_normal_hint)
                val syncTitle = stringResource(L10nR.string.sync_title)
                val handoffTitle = stringResource(L10nR.string.echo_link_handoff_phone)
                ConnectActionList(buildList {
                    if (!remoteMode || libraryOnly) {
                        add(ConnectAction(Icons.Rounded.LibraryMusic, libraryTitle, libraryDetail, primary = true, onClick = onOpenPcLibrary))
                    }
                    if (!libraryOnly && librarySyncActions != null) {
                        add(ConnectAction(Icons.Rounded.Sync, syncTitle, null) { showLibrarySync = true })
                    }
                    if (!libraryOnly && onOpenListening != null) {
                        add(ConnectAction(Icons.Rounded.Headphones, listenTitle, listenDetail, onClick = onOpenListening))
                    }
                    if (remoteMode && !libraryOnly && onHandoffPhoneToPc != null) {
                        add(ConnectAction(Icons.Rounded.SwapHoriz, handoffTitle, null, onClick = onHandoffPhoneToPc))
                    }
                })
            }
            else -> {
                if (savedPcs.isNotEmpty()) {
                    ConnectSection(stringResource(L10nR.string.feature_connect_saved_pcs_4d2e91)) {
                        savedPcs.asReversed().forEachIndexed { index, pc ->
                            if (index > 0) ConnectRowDivider()
                            PcDeviceRow(
                                name = pc.name,
                                address = pc.address,
                                actionLabel = stringResource(L10nR.string.feature_connect_connect_c7c091),
                                onConnect = { connectDevice(pc.address, pc.token.orEmpty()) },
                                onForget = { savedPcToForget = pc.id },
                            )
                        }
                    }
                }
                ConnectSection(stringResource(L10nR.string.feature_connect_nearby_pcs_b9b12a),
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
                        val deviceAddress = EchoLinkDiscoveryPolicy.addressLabel(device)
                        PcDeviceRow(
                            name = device.name,
                            address = if (device.libraryOnly) stringResource(L10nR.string.echo_bridge_library_note)
                                else deviceAddress,
                            actionLabel = stringResource(L10nR.string.feature_connect_connect_c7c091),
                            onConnect = {
                                val token = if (device.libraryOnly) "" else
                                    EchoManualConnectionInput.resolve(deviceAddress, savedPcs, currentPc)?.token.orEmpty()
                                connectDevice(deviceAddress, token)
                                onSelectLanDevice(device)
                            },
                        )
                    }
                }
                PcManualEntry(
                    expanded = manual,
                    onToggle = {
                        if (manual) {
                            focus.clearFocus()
                            keyboard?.hide()
                        }
                        manual = !manual
                    },
                    address = address,
                    onAddressChange = { address = it },
                    canConnect = endpoint != null,
                    onConnect = connect,
                    modifier = Modifier.bringIntoViewRequester(connectionEntry),
                    connectionError = manualError,
                )
            }
        }
        val wifiTitle = stringResource(L10nR.string.wifi_transfer_title)
        val wifiDetail = stringResource(L10nR.string.wifi_transfer_entry_detail)
        val transferTitle = stringResource(L10nR.string.link_transfer_title)
        val otherWays = buildList {
            if (onOpenListening != null && !connected) {
                add(ConnectAction(Icons.Rounded.Headphones, listenTitle, listenDetail, onClick = onOpenListening))
            }
            // Receiving songs from a computer browser is independent of PC remote control, so it never lives in the PC status header.
            if (onOpenWifiTransfer != null) {
                add(ConnectAction(Icons.Rounded.FileDownload, wifiTitle, wifiDetail, onClick = onOpenWifiTransfer))
            }
            if (connected && !libraryOnly && onOpenLinkSongTransfer != null) {
                add(ConnectAction(Icons.Rounded.Folder, transferTitle, null, onClick = onOpenLinkSongTransfer))
            }
        }
        ConnectActionList(otherWays, title = stringResource(L10nR.string.connect_other_ways_title))
        phoneLibraryShareContent()
        phonePresenceContent()
        PcLinkPreferences(
            hasSaved = hasSaved,
            busy = busy,
            autoReconnectEnabled = autoReconnectEnabled,
            linkedLibraryDefault = linkedLibraryDefault,
            onAutoReconnectChange = onAutoReconnectChange,
            onLinkedLibraryDefaultChange = onLinkedLibraryDefaultChange,
            onForgetRequest = { confirmForget = true },
        )
        ConnectNote(stringResource(L10nR.string.echo_link_battery_hint))
    }
    if (confirmForget) ForgetConnectionDialog(
        stringResource(L10nR.string.feature_connect_forget_saved_pc_80b440),
        onDismiss = { confirmForget = false },
        onConfirm = { confirmForget = false; address = ""; onForgetPc() },
    )
    savedPcToForget?.let { id ->
        savedPcs.firstOrNull { it.id == id }?.let { pc ->
            ForgetConnectionDialog(
                stringResource(L10nR.string.feature_connect_remove_title_323067, pc.name),
                onDismiss = { savedPcToForget = null },
                onConfirm = { savedPcToForget = null; onForgetSavedPc(pc) },
            )
        }
    }
}

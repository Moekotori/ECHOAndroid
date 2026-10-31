package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import app.echo.android.connect.EchoLinkCastBlockReason
import app.echo.android.connect.EchoLinkDiscoveryState
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.echoShortLandscape
import androidx.compose.ui.text.style.TextOverflow
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPageBackgroundColor
import app.echo.android.design.animateSilkToPage
import app.echo.android.design.rememberContentPagerNestedScroll
import app.echo.android.design.rememberSilkPagerFlingBehavior
import kotlinx.coroutines.launch
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.LibraryScanProgress

@Composable
fun ConnectScreen(
    remoteState: EchoRemoteConnectionState,
    controlPending: Boolean = false,
    librarySyncActions: LibrarySyncActions? = null,
    pcTitle: String,
    trackTitle: String,
    trackArtist: String,
    trackArtworkUrl: String?,
    isPlaying: Boolean,
    remoteError: String?,
    savedPcAddress: String?,
    savedPcToken: String?,
    autoReconnectEnabled: Boolean,
    linkedLibraryDefault: Boolean,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    volume: Float = 1f,
    volumeControlEnabled: Boolean = true,
    volumeLockedReason: String? = null,
    outputMode: String = "",
    currentTrackId: String? = null,
    queueItems: List<EchoRemoteTrack> = emptyList(),
    subsonicServerUrl: String?,
    subsonicUsername: String?,
    subsonicPassword: String?,
    webDavServerUrl: String?,
    webDavUsername: String?,
    webDavPassword: String?,
    smbServerUrl: String? = null,
    smbUsername: String? = null,
    smbPassword: String? = null,
    onSyncSmbLibrary: (String, String, String) -> Unit = { _, _, _ -> },
    onSaveSmbCredentials: (String, String, String) -> Unit = { _, _, _ -> },
    onClearSmbCredentials: () -> Unit = {},
    jellyfinServerUrl: String?,
    jellyfinUsername: String?,
    jellyfinPassword: String?,
    savedPcs: List<app.echo.android.model.connect.EchoSavedPcEndpoint> = emptyList(),
    remoteScanState: LibraryScanProgress,
    onConnectPc: (String, String) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onVolume: (Float) -> Unit = {},
    onPlayQueueItem: (String) -> Unit = {},
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
    onSyncJellyfinLibrary: (String, String, String) -> Unit,
    onSaveJellyfinCredentials: (String, String, String) -> Unit,
    onClearJellyfinCredentials: () -> Unit,
    onForgetSavedPc: (app.echo.android.model.connect.EchoSavedPcEndpoint) -> Unit = {},
    onCancelRemoteSync: () -> Unit,
    discoveryState: EchoLinkDiscoveryState = EchoLinkDiscoveryState.Idle,
    discoveredLanDevices: List<EchoLinkLanDevice> = emptyList(),
    onSelectLanDevice: (EchoLinkLanDevice) -> Unit = {},
    onRefreshLanDevices: () -> Unit = {},
    onHandoffPhoneToPc: (() -> Unit)? = null,
    phoneTrackTitle: String? = null,
    phoneTrackArtist: String? = null,
    phoneTrackArtworkUrl: String? = null,
    phoneTrackFormat: String? = null,
    phoneTrackLossless: Boolean = false,
    castBlockedReason: EchoLinkCastBlockReason? = null,
    casting: Boolean = false,
    castSessionActive: Boolean = false,
    castSessionName: String? = null,
    sendingAddress: String? = null,
    connectedLanAddress: String? = null,
    onCastToAddress: (String, String) -> Unit = { _, _ -> },
    onCastToConnected: (() -> Unit)? = null,
    onStopCast: () -> Unit = {},
    onRequestPairing: () -> Unit = {},
    openCastTabNonce: Int = 0,
    castQueueCount: Int = 0,
    showDsdWarning: Boolean = false,
    lanRenderers: List<EchoLanRenderer> = emptyList(),
    lanRendererState: EchoLinkDiscoveryState = EchoLinkDiscoveryState.Idle,
    activeRendererId: String? = null,
    onCastToRenderer: (EchoLanRenderer) -> Unit = {},
    onSwipeToLibrary: () -> Unit = {},
    onSwipeToDiagnostics: () -> Unit = {},
    onOpenListening: (() -> Unit)? = null,
    remoteLibrary: EchoRemoteLibraryState = EchoRemoteLibraryState(),
    onSearchPcLibrary: (String) -> Unit = {},
    onLoadMorePcLibrary: () -> Unit = {},
    remoteControlsActive: Boolean = true,
    remoteMode: Boolean = true,
    libraryOnly: Boolean = false,
    onRemoteModeChange: (Boolean) -> Unit = {},
    onOpenPcLibrary: () -> Unit = {},
    openPcTabNonce: Int = 0,
    openPcQueueNonce: Int = 0,
    onPcQueueOpened: () -> Unit = {},
    onOpenFullQueue: (() -> Unit)? = null,
    queueTotalCount: Int = queueItems.size,
    networkPermissionContent: @Composable () -> Unit = {},
    onOpenWifiTransfer: () -> Unit = {},
    phoneLibraryShareStatus: String = "off",
    onPhoneLibraryShareChange: (Boolean) -> Unit = {},
) {
    val pagerState = rememberPagerState { 3 }
    val scrollStates = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
    val savedTabs = rememberSaveableStateHolder()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var openShareConnectionNonce by remember { mutableIntStateOf(0) }
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val innerFling = rememberSilkPagerFlingBehavior(pagerState)
    val innerNestedScroll = rememberContentPagerNestedScroll(pagerState, innerFling)
    fun selectTab(index: Int) {
        focus.clearFocus()
        keyboard?.hide()
        scope.launch { pagerState.animateSilkToPage(index, lightweight) }
    }
    var previousPage by remember { mutableIntStateOf(pagerState.targetPage) }
    var wasActive by remember { mutableStateOf(remoteControlsActive) }
    LaunchedEffect(pagerState.targetPage, remoteControlsActive) {
        // Swipes and external navigation must release the editor as well as tab clicks.
        // Precomposing an inactive neighbour must never steal focus from the visible page.
        if (wasActive && (previousPage != pagerState.targetPage || !remoteControlsActive)) {
            focus.clearFocus()
            keyboard?.hide()
        }
        previousPage = pagerState.targetPage
        wasActive = remoteControlsActive
    }
    LaunchedEffect(openCastTabNonce) {
        if (openCastTabNonce > 0) pagerState.animateSilkToPage(2, lightweight)
    }
    LaunchedEffect(openPcTabNonce, openPcQueueNonce) {
        if (openPcTabNonce > 0 || openPcQueueNonce > 0) pagerState.animateSilkToPage(0, lightweight)
    }
    val tabs = listOf(
        stringResource(L10nR.string.feature_connect_pc_link_4ca6bd),
        stringResource(L10nR.string.feature_connect_library_sources_09e6db),
        stringResource(L10nR.string.echo_link_cast_tab),
    )
    val shortLandscape = echoShortLandscape()
    Surface(Modifier.fillMaxSize(), color = echoPageBackgroundColor(), contentColor = scheme.onBackground) {
        Column(Modifier.statusBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            val heading: @Composable () -> Unit = {
                Text(stringResource(L10nR.string.feature_connect_connect_c7c091),
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            }
            val tabBar: @Composable () -> Unit = {
                ConnectTabBar(tabs, pagerState, onSelect = ::selectTab)
            }
            if (shortLandscape) {
                Row(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.widthIn(max = 160.dp)) { heading() }
                    Box(Modifier.weight(1f).padding(end = 24.dp)) { tabBar() }
                }
            } else {
                Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxWidth()) {
                    heading()
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) { tabBar() }
                }
            }
            networkPermissionContent()
            Box(Modifier.padding(horizontal = 16.dp)) { WifiTransferEntry(onOpenWifiTransfer) }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f).connectPagerEdgeSwipe(
                    pagerState = pagerState,
                    onSwipeToLibrary = onSwipeToLibrary,
                    onSwipeToDiagnostics = onSwipeToDiagnostics,
                ),
                beyondViewportPageCount = if (lightweight) 0 else 1,
                flingBehavior = innerFling,
                pageNestedScrollConnection = innerNestedScroll,
            ) { tab ->
                savedTabs.SaveableStateProvider(tab) {
                    Column(
                        Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxSize()
                            .verticalScroll(scrollStates[tab]).padding(horizontal = 24.dp)
                            .padding(top = 12.dp, bottom = if (shortLandscape) 100.dp else 188.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (tab) {
                            1 -> RemoteSourcesPanel(
                                subsonicServerUrl = subsonicServerUrl,
                                subsonicUsername = subsonicUsername,
                                subsonicPassword = subsonicPassword,
                                webDavServerUrl = webDavServerUrl,
                                webDavUsername = webDavUsername,
                                webDavPassword = webDavPassword,
                                smbServerUrl = smbServerUrl,
                                smbUsername = smbUsername,
                                smbPassword = smbPassword,
                                onSyncSmb = onSyncSmbLibrary,
                                onSaveSmb = onSaveSmbCredentials,
                                onClearSmb = onClearSmbCredentials,
                                jellyfinServerUrl = jellyfinServerUrl,
                                jellyfinUsername = jellyfinUsername,
                                jellyfinPassword = jellyfinPassword,
                                scanState = remoteScanState,
                                onSyncSubsonic = onSyncSubsonicLibrary,
                                onSaveSubsonic = onSaveSubsonicCredentials,
                                onClearSubsonic = onClearSubsonicCredentials,
                                onSyncWebDav = onSyncWebDavLibrary,
                                onSaveWebDav = onSaveWebDavCredentials,
                                onClearWebDav = onClearWebDavCredentials,
                                onSyncJellyfin = onSyncJellyfinLibrary,
                                onSaveJellyfin = onSaveJellyfinCredentials,
                                onClearJellyfin = onClearJellyfinCredentials,
                                onCancel = onCancelRemoteSync,
                            )
                            0 -> {
                                PcLinkPanel(
                                    librarySyncActions = librarySyncActions,
                                    phoneLibraryShareContent = {
                                        if (!libraryOnly) ConnectCard {
                                            PhoneLibraryShareControl(
                                                status = phoneLibraryShareStatus,
                                                connectionState = remoteState,
                                                active = remoteControlsActive && pagerState.settledPage == 0,
                                                onConnectRequested = { openShareConnectionNonce++ },
                                                onChange = onPhoneLibraryShareChange,
                                            )
                                        }
                                    },
                                    remoteState = remoteState,
                                    controlPending = controlPending,
                                    pcTitle = pcTitle,
                                    trackTitle = trackTitle,
                                    trackArtist = trackArtist,
                                    trackArtworkUrl = trackArtworkUrl,
                                    isPlaying = isPlaying,
                                    remoteError = remoteError,
                                    savedPcAddress = savedPcAddress,
                                    savedPcToken = savedPcToken,
                                    savedPcs = savedPcs,
                                    autoReconnectEnabled = autoReconnectEnabled,
                                    linkedLibraryDefault = linkedLibraryDefault,
                                    positionMs = positionMs,
                                    durationMs = durationMs,
                                    volume = volume,
                                    volumeControlEnabled = volumeControlEnabled,
                                    volumeLockedReason = volumeLockedReason,
                                    outputMode = outputMode,
                                    currentTrackId = currentTrackId,
                                    queueItems = queueItems,
                                    queueTotalCount = queueTotalCount,
                                    onConnectPc = onConnectPc,
                                    onPlayPause = onPlayPause,
                                    onPrevious = onPrevious,
                                    onNext = onNext,
                                    onStop = onStop,
                                    onSeek = onSeek,
                                    onVolume = onVolume,
                                    onPlayQueueItem = onPlayQueueItem,
                                    onOpenFullQueue = onOpenFullQueue,
                                    onHandoffPhoneToPc = onHandoffPhoneToPc,
                                    onDisconnect = onDisconnect,
                                    onForgetPc = onForgetPc,
                                    onForgetSavedPc = onForgetSavedPc,
                                    onAutoReconnectChange = onAutoReconnectChange,
                                    onLinkedLibraryDefaultChange = onLinkedLibraryDefaultChange,
                                    discoveryState = discoveryState,
                                    discoveredLanDevices = discoveredLanDevices,
                                    onSelectLanDevice = onSelectLanDevice,
                                    onRefreshLanDevices = onRefreshLanDevices,
                                    remoteLibrary = remoteLibrary,
                                    onSearchPcLibrary = onSearchPcLibrary,
                                    onLoadMorePcLibrary = onLoadMorePcLibrary,
                                    active = remoteControlsActive && pagerState.settledPage == 0,
                                    onOpenListening = onOpenListening,
                                    remoteMode = remoteMode,
                                    libraryOnly = libraryOnly,
                                    onRemoteModeChange = onRemoteModeChange,
                                    onOpenPcLibrary = onOpenPcLibrary,
                                    openQueueNonce = openPcQueueNonce,
                                    onQueueRequestHandled = onPcQueueOpened,
                                    openConnectionNonce = openShareConnectionNonce,
                                    onConnectionRequestHandled = { openShareConnectionNonce = 0 },
                                )
                            }
                            else -> CastDevicesPanel(
                                phoneTrackTitle = phoneTrackTitle,
                                phoneTrackArtist = phoneTrackArtist,
                                phoneTrackArtworkUrl = phoneTrackArtworkUrl,
                                phoneTrackFormat = phoneTrackFormat,
                                phoneTrackLossless = phoneTrackLossless,
                                blockedReason = castBlockedReason,
                                casting = casting,
                                castSessionActive = castSessionActive,
                                castSessionName = castSessionName,
                                sendingAddress = sendingAddress,
                                remoteError = remoteError,
                                discoveryState = discoveryState,
                                discoveredLanDevices = discoveredLanDevices.filterNot { it.libraryOnly },
                                savedPcs = savedPcs.filterNot { saved ->
                                    discoveredLanDevices.any { device -> device.libraryOnly &&
                                        app.echo.android.connect.EchoLinkDiscoveryPolicy.addressMatchesDevice(saved.address, device) } ||
                                        (libraryOnly && app.echo.android.connect.EchoLinkDiscoveryPolicy.sameLanEndpoint(saved.address, savedPcAddress))
                                },
                                savedPcAddress = savedPcAddress,
                                savedPcToken = savedPcToken,
                                connectedAddress = connectedLanAddress,
                                onCastToAddress = onCastToAddress,
                                onCastToConnected = if (libraryOnly) null else onCastToConnected,
                                onStopCast = onStopCast,
                                onSelectLanDevice = onSelectLanDevice,
                                connectedPcName = pcTitle,
                                castQueueCount = castQueueCount,
                                showDsdWarning = showDsdWarning,
                                lanRenderers = lanRenderers,
                                lanRendererState = lanRendererState,
                                activeRendererId = activeRendererId,
                                onCastToRenderer = onCastToRenderer,
                                onRequestPairing = {
                                    selectTab(0)
                                    onRequestPairing()
                                },
                                onRefreshLanDevices = onRefreshLanDevices,
                            )
                        }
                    }
                }
            }
        }
    }
}

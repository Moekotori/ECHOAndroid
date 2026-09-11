package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import app.echo.android.connect.EchoLinkCastBlockReason
import app.echo.android.connect.EchoLinkDiscoveryState
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLinkLanDevice
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.library.LibraryScanProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    remoteState: EchoRemoteConnectionState,
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
    queueTitles: List<String> = emptyList(),
    subsonicServerUrl: String?,
    subsonicUsername: String?,
    subsonicPassword: String?,
    webDavServerUrl: String?,
    webDavUsername: String?,
    webDavPassword: String?,
    jellyfinServerUrl: String?,
    jellyfinUsername: String?,
    jellyfinPassword: String?,
    savedPcs: List<app.echo.android.model.connect.EchoSavedPcEndpoint> = emptyList(),
    remoteScanState: LibraryScanProgress,
    onConnectPc: (String, String) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit = {},
    onVolume: (Float) -> Unit = {},
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
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollStates = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
    val savedTabs = rememberSaveableStateHolder()
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(openCastTabNonce) {
        if (openCastTabNonce > 0) selectedTab = 2
    }
    val tabs = listOf(
        stringResource(L10nR.string.feature_connect_library_sources_09e6db),
        stringResource(L10nR.string.feature_connect_pc_link_4ca6bd),
        stringResource(L10nR.string.echo_link_cast_tab),
    )
    Surface(Modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onBackground) {
        Column(Modifier.statusBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxWidth()) {
                Text(stringResource(L10nR.string.feature_connect_connect_c7c091),
                    Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                SecondaryTabRow(selectedTabIndex = selectedTab, containerColor = scheme.background) {
                    tabs.forEachIndexed { index, label ->
                        Tab(selected = selectedTab == index, onClick = { keyboard?.hide(); selectedTab = index },
                            unselectedContentColor = scheme.onSurfaceVariant,
                            text = { Text(label, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal) })
                    }
                }
            }
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.widthIn(max = LocalEchoContentMaxWidth.current)
                    .fillMaxWidth().weight(1f).clipToBounds(),
                transitionSpec = { EchoMotion.tabSwitch(targetState > initialState) },
                label = "ConnectTabs",
            ) { tab ->
                savedTabs.SaveableStateProvider(tab) {
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(scrollStates[tab]).padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 188.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        when (tab) {
                            0 -> RemoteSourcesPanel(
                                subsonicServerUrl = subsonicServerUrl,
                                subsonicUsername = subsonicUsername,
                                subsonicPassword = subsonicPassword,
                                webDavServerUrl = webDavServerUrl,
                                webDavUsername = webDavUsername,
                                webDavPassword = webDavPassword,
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
                            1 -> PcLinkPanel(
                                remoteState = remoteState,
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
                                queueTitles = queueTitles,
                                onConnectPc = onConnectPc,
                                onPlayPause = onPlayPause,
                                onPrevious = onPrevious,
                                onNext = onNext,
                                onSeek = onSeek,
                                onVolume = onVolume,
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
                            )
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
                                discoveredLanDevices = discoveredLanDevices,
                                savedPcs = savedPcs,
                                savedPcAddress = savedPcAddress,
                                savedPcToken = savedPcToken,
                                connectedAddress = connectedLanAddress,
                                onCastToAddress = onCastToAddress,
                                onCastToConnected = onCastToConnected,
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
                                    selectedTab = 1
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

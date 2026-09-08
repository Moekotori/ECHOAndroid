package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
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
    onHandoffPhoneToPc: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollStates = listOf(rememberScrollState(), rememberScrollState())
    val savedTabs = rememberSaveableStateHolder()
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = MaterialTheme.colorScheme
    val tabs = listOf(
        stringResource(L10nR.string.feature_connect_library_sources_09e6db),
        stringResource(L10nR.string.feature_connect_pc_link_4ca6bd),
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
                        if (tab == 0) {
                            RemoteSourcesPanel(
                                subsonicServerUrl = subsonicServerUrl,
                                subsonicUsername = subsonicUsername,
                                subsonicPassword = subsonicPassword,
                                webDavServerUrl = webDavServerUrl,
                                webDavUsername = webDavUsername,
                                webDavPassword = webDavPassword,
                                scanState = remoteScanState,
                                onSyncSubsonic = onSyncSubsonicLibrary,
                                onSaveSubsonic = onSaveSubsonicCredentials,
                                onClearSubsonic = onClearSubsonicCredentials,
                                onSyncWebDav = onSyncWebDavLibrary,
                                onSaveWebDav = onSaveWebDavCredentials,
                                onClearWebDav = onClearWebDavCredentials,
                                onCancel = onCancelRemoteSync,
                            )
                        } else {
                            PcLinkPanel(
                                remoteState = remoteState,
                                pcTitle = pcTitle,
                                trackTitle = trackTitle,
                                trackArtist = trackArtist,
                                trackArtworkUrl = trackArtworkUrl,
                                isPlaying = isPlaying,
                                remoteError = remoteError,
                                scanMessage = scanMessage,
                                scanMessageIsError = scanMessageIsError,
                                savedPcAddress = savedPcAddress,
                                savedPcToken = savedPcToken,
                                autoReconnectEnabled = autoReconnectEnabled,
                                linkedLibraryDefault = linkedLibraryDefault,
                                discordPresenceEnabled = discordPresenceEnabled,
                                discordPresenceReady = discordPresenceReady,
                                discordPresenceTrackTitle = discordPresenceTrackTitle,
                                onConnectPc = onConnectPc,
                                onScanPairingCode = onScanPairingCode,
                                onPlayPause = onPlayPause,
                                onPrevious = onPrevious,
                                onNext = onNext,
                                onDisconnect = onDisconnect,
                                onForgetPc = onForgetPc,
                                onAutoReconnectChange = onAutoReconnectChange,
                                onLinkedLibraryDefaultChange = onLinkedLibraryDefaultChange,
                                discoveredLanDevices = discoveredLanDevices,
                                onSelectLanDevice = onSelectLanDevice,
                                onRefreshLanDevices = onRefreshLanDevices,
                            )
                        }
                    }
                }
            }
        }
    }
}

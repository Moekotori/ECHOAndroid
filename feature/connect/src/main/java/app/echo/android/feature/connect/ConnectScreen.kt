package app.echo.android.feature.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassCyan
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoGlassViolet
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.EchoTextButton
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.PageChrome
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoGlassRowBrush
import app.echo.android.design.echoString
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
        title = echoString(en = "Connect", zh = "连接", ja = "接続"),
        subtitle = echoString(en = "Library sources · PC link", zh = "曲库来源 · PC 联动", ja = "ライブラリ接続 · PC 連携"),
        badge = echoString(en = "Link", zh = "互联", ja = "連携"),
        scrollable = true,
        scrollBottomPadding = 188.dp,
    ) {
        EchoSectionTitle(
            echoString(en = "Music services", zh = "音乐服务", ja = "音楽サービス"),
            echoString(en = "Connect your library sources", zh = "连接你的曲库来源", ja = "ライブラリの接続先"),
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
            name = echoString(en = "Local library", zh = "本地曲库", ja = "ローカルライブラリ"),
            subtitle = echoString(
                en = "Local audio files already scanned",
                zh = "已扫描本机音频文件",
                ja = "端末内の音声ファイルをスキャン済み",
            ),
            icon = Icons.Rounded.LibraryMusic,
            brandColor = Color(0xFF35C28E),
            statusLabel = echoString(en = "Connected", zh = "已连接", ja = "接続済み"),
            active = true,
            locked = false,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        ServiceCard(
            name = "Discord Rich Presence",
            subtitle = discordPresenceTrackTitle?.let {
                echoString(en = "Playing on phone: $it", zh = "手机播放：$it", ja = "スマホで再生中：$it")
            } ?: echoString(
                en = "Forward phone playback through PC ECHO",
                zh = "通过 PC ECHO 转发手机播放状态",
                ja = "PC ECHO 経由でスマホの再生状態を転送",
            ),
            icon = Icons.Rounded.GraphicEq,
            brandColor = Color(0xFF5865F2),
            statusLabel = when {
                !discordPresenceEnabled -> echoString(en = "Off", zh = "未开启", ja = "オフ")
                discordPresenceReady -> echoString(en = "Ready to send", zh = "待转发", ja = "転送待ち")
                else -> echoString(en = "Waiting for PC", zh = "等待 PC", ja = "PC 待ち")
            },
            active = discordPresenceEnabled && discordPresenceReady,
            locked = !discordPresenceEnabled,
            onClick = {},
        )
        Spacer(Modifier.height(20.dp))
        EchoSectionTitle(
            echoString(en = "Device link", zh = "设备联动", ja = "デバイス連携"),
            if (connected) {
                echoString(en = "Control on phone, output on PC", zh = "手机控制，PC 输出", ja = "スマホで操作、PC で出力")
            } else {
                echoString(
                    en = "Take over PC ECHO playback after pairing",
                    zh = "配对后接管 PC ECHO 播放",
                    ja = "ペアリング後に PC ECHO の再生を操作",
                )
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
                            .background(EchoHomeBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = Color.White,
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
                            echoString(en = "Paired", zh = "已配对", ja = "ペアリング済み")
                        } else {
                            echoString(en = "Unpaired", zh = "未配对", ja = "未ペアリング")
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
                            echoString(en = "Connected", zh = "已连接", ja = "接続済み")
                        } else {
                            echoString(en = "Connect PC", zh = "连接 PC", ja = "PC に接続")
                        },
                        onClick = { onConnectPc(pcAddressInput, pcTokenInput) },
                        enabled = !connected && canConnectPc,
                    )
                    if (connected) {
                        TextButton(onClick = onDisconnect) {
                            Text(echoString(en = "Disconnect", zh = "断开", ja = "切断"), color = EchoHomeBlue)
                        }
                    } else if (hasSavedPc) {
                        TextButton(
                            onClick = {
                                pcAddressInput = ""
                                pcTokenInput = ""
                                onForgetPc()
                            },
                        ) {
                            Text(echoString(en = "Forget", zh = "忘记", ja = "解除"), color = EchoHomeBlue)
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
                echoString(
                    en = "Nearby PCs, then address and pairing token",
                    zh = "附近 PC，然后填地址和配对 Token",
                    ja = "近くの PC、続けてアドレスとトークン",
                ),
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RemoteCompactAction(
                text = echoString(en = "Refresh", zh = "刷新", ja = "更新"),
                enabled = true,
                modifier = Modifier.width(72.dp),
                onClick = onRefreshLanDevices,
            )
        }
        if (discoveredLanDevices.isEmpty()) {
            Text(
                echoString(
                    en = "No LAN PCs yet. Scan a QR code or type the address.",
                    zh = "还没发现局域网 PC，可扫码或手动输入。",
                    ja = "LAN 上の PC は未検出。QR または手動入力。",
                ),
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
                                if (selected) EchoHomeBlue.copy(alpha = 0.18f)
                                else scheme.surface.copy(alpha = 0.35f),
                            )
                            .clickable { onSelectLanDevice(device) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = EchoHomeBlue,
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
            echoString(
                en = "Supports 192.168.1.12:26789 or a full http / https URL",
                zh = "支持 192.168.1.12:26789 或完整 http / https 地址",
                ja = "192.168.1.12:26789 または http / https の完全なアドレスに対応",
            ),
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
                echoString(
                    en = "Scan the QR code shown on PC to pair",
                    zh = "PC 端显示二维码后可直接扫码配对",
                    ja = "PC に表示された QR コードをスキャンしてペアリング",
                ),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RemoteCompactAction(
                text = echoString(en = "Scan QR", zh = "扫码配对", ja = "スキャン"),
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
            label = echoString(en = "PC address", zh = "PC 地址", ja = "PC アドレス"),
            value = address,
            placeholder = "192.168.1.12:26789",
            onValueChange = onAddressChange,
        )
        RemoteTextInput(
            label = echoString(en = "Pairing token", zh = "配对 Token", ja = "ペアリングトークン"),
            value = token,
            placeholder = echoString(
                en = "Copy from the PC ECHO link page",
                zh = "从 PC ECHO 联动页复制",
                ja = "PC ECHO の連携ページからコピー",
            ),
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
                    echoString(en = "Auto reconnect", zh = "自动重连", ja = "自動再接続"),
                    color = if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (hasSavedPc) {
                        echoString(
                            en = "Reconnect to the last PC when Connect opens",
                            zh = "下次打开 Connect 时尝试连接上次的 PC",
                            ja = "次回 Connect を開くと、前回の PC に接続します",
                        )
                    } else {
                        echoString(
                            en = "Available after a successful saved connection",
                            zh = "连接成功并保存后可用",
                            ja = "接続して保存すると利用できます",
                        )
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
                if (checked) EchoHomeBlue.copy(alpha = if (dark) 0.18f else 0.10f)
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
                echoString(en = "Use linked library by default", zh = "默认读取联动曲库", ja = "連携ライブラリを既定にする"),
                color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    checked && connected -> echoString(
                        en = "The linked ECHO library is shown separately, with PC ECHO as the current source",
                        zh = "联动 ECHO 曲库独立显示，已按 PC ECHO 作为当前联动源",
                        ja = "連携 ECHO ライブラリは独立表示され、現在の連携元は PC ECHO です",
                    )
                    checked -> echoString(
                        en = "After connecting, read PC ECHO automatically without merging into the local scan library",
                        zh = "连接后自动读取 PC ECHO，不并入本地扫描库",
                        ja = "接続後に PC ECHO を自動読み込みし、ローカルスキャンのライブラリには混ぜません",
                    )
                    else -> echoString(
                        en = "The local library stays default; refresh the linked library manually",
                        zh = "本地曲库保持默认，可手动刷新联动曲库",
                        ja = "ローカルライブラリを既定のままにし、連携ライブラリは手動で更新できます",
                    )
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
                        EchoHomeBlue.copy(alpha = if (dark) 0.20f else 0.12f),
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
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(EchoHomeBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        echoString(en = "Remote libraries", zh = "远程曲库", ja = "リモートライブラリ"),
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
                        echoString(en = "Syncing", zh = "同步中", ja = "同期中")
                    } else if (readyCount > 0) {
                        echoString(
                            en = "$readyCount ready",
                            zh = "${readyCount} 个可用",
                            ja = "${readyCount} 件利用可",
                        )
                    } else {
                        echoString(en = "Not set up", zh = "待配置", ja = "未設定")
                    },
                    active = readyCount > 0 && scanState.phase != LibraryScanPhase.Error,
                    locked = readyCount == 0 && !scanState.isScanning,
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        echoString(en = "Collapse remote libraries", zh = "折叠远程曲库", ja = "リモートライブラリを折りたたむ")
                    } else {
                        echoString(en = "Expand remote libraries", zh = "展开远程曲库", ja = "リモートライブラリを展開")
                    },
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (expanded) {
                RemoteSourceProviderSection(
                    title = "Subsonic / Navidrome",
                    subtitle = echoString(
                        en = "Sync the server library, artwork, and playback URLs",
                        zh = "同步服务器曲库、封面和播放地址",
                        ja = "サーバーのライブラリ、ジャケット、再生 URL を同期",
                    ),
                    serverLabel = echoString(en = "Server address", zh = "服务器地址", ja = "サーバーアドレス"),
                    serverPlaceholder = "https://music.example.com",
                    usernameLabel = echoString(en = "Username", zh = "用户名", ja = "ユーザー名"),
                    passwordLabel = echoString(en = "Password", zh = "密码", ja = "パスワード"),
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
                    title = echoString(en = "WebDAV / cloud drive", zh = "WebDAV / 网盘", ja = "WebDAV / クラウド"),
                    subtitle = echoString(
                        en = "Sync a NAS or cloud music folder",
                        zh = "按文件夹同步 NAS 或网盘音乐目录",
                        ja = "NAS やクラウドの音楽フォルダーを同期",
                    ),
                    serverLabel = echoString(en = "WebDAV address", zh = "WebDAV 地址", ja = "WebDAV アドレス"),
                    serverPlaceholder = "https://dav.example.com/music",
                    usernameLabel = echoString(en = "WebDAV username", zh = "WebDAV 用户名", ja = "WebDAV ユーザー名"),
                    passwordLabel = echoString(en = "WebDAV password", zh = "WebDAV 密码", ja = "WebDAV パスワード"),
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
        scanState.isScanning && expanded -> echoString(en = "Syncing", zh = "同步中", ja = "同期中")
        ready -> echoString(en = "Ready to sync", zh = "可同步", ja = "同期できます")
        hasInput -> echoString(en = "Incomplete", zh = "待补全", ja = "未入力あり")
        else -> echoString(en = "Not set up", zh = "待配置", ja = "未設定")
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
                .then(if (expandable) Modifier.clickable { onExpandedChange(!expanded) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (expanded) 38.dp else 30.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (ready) EchoHomeBlue else if (dark) Color.White.copy(alpha = 0.20f) else scheme.outlineVariant.copy(alpha = 0.82f)),
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
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        echoString(en = "Collapse", zh = "折叠", ja = "折りたたむ")
                    } else {
                        echoString(en = "Expand", zh = "展开", ja = "展開")
                    },
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (expanded) {
            RemoteTextInput(
                label = serverLabel,
                value = serverUrl,
                placeholder = serverPlaceholder,
                onValueChange = onServerUrlChange,
            )
            RemoteTextInput(
                label = usernameLabel,
                value = username,
                placeholder = echoString(en = "Username", zh = "用户名", ja = "ユーザー名"),
                onValueChange = onUsernameChange,
            )
            RemoteTextInput(
                label = passwordLabel,
                value = password,
                placeholder = echoString(
                    en = "Password or app password",
                    zh = "密码或应用专用密码",
                    ja = "パスワードまたはアプリパスワード",
                ),
                secret = true,
                onValueChange = onPasswordChange,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RemoteCompactAction(
                    text = echoString(en = "Save", zh = "保存", ja = "保存"),
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                )
                RemoteCompactAction(
                    text = if (scanState.isScanning) {
                        echoString(en = "Cancel", zh = "取消", ja = "キャンセル")
                    } else {
                        echoString(en = "Sync", zh = "同步", ja = "同期")
                    },
                    enabled = ready || scanState.isScanning,
                    modifier = Modifier.weight(1f),
                    onClick = { if (scanState.isScanning) onCancel() else onSync() },
                )
                RemoteCompactAction(
                    text = echoString(en = "Clear", zh = "清除", ja = "クリア"),
                    enabled = hasInput,
                    modifier = Modifier.weight(1f),
                    onClick = onClear,
                )
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
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
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
        scanState.phase == LibraryScanPhase.Completed -> echoString(
            en = "Sync complete: ${scanState.scannedCount} tracks, ${scanState.insertedCount} added, ${scanState.updatedCount} updated, ${scanState.deletedCount} removed",
            zh = "同步完成：${scanState.scannedCount} 首，新增 ${scanState.insertedCount}，更新 ${scanState.updatedCount}，删除 ${scanState.deletedCount}",
            ja = "同期完了：${scanState.scannedCount} 曲、追加 ${scanState.insertedCount}、更新 ${scanState.updatedCount}、削除 ${scanState.deletedCount}",
        )
        scanState.phase == LibraryScanPhase.Error -> scanState.error ?: echoString(
            en = "Remote library sync failed",
            zh = "远程曲库同步失败",
            ja = "リモートライブラリの同期に失敗しました",
        )
        ready -> echoString(
            en = "Ready to sync to the cloud album wall",
            zh = "可同步到云端专辑墙",
            ja = "クラウドのアルバムウォールに同期できます",
        )
        else -> echoString(
            en = "Enter the server, username, and password",
            zh = "填写服务器、用户名和密码",
            ja = "サーバー、ユーザー名、パスワードを入力",
        )
    }

@Composable
private fun remoteSourcesSummary(scanState: LibraryScanProgress, readyCount: Int): String =
    when {
        scanState.isScanning -> remoteLibraryDetail(scanState, ready = true)
        scanState.phase == LibraryScanPhase.Completed -> remoteLibraryDetail(scanState, ready = true)
        scanState.phase == LibraryScanPhase.Error -> remoteLibraryDetail(scanState, ready = false)
        readyCount > 0 -> echoString(
            en = "Tap to expand · Subsonic / WebDAV fold separately",
            zh = "点按展开 · Subsonic / WebDAV 可独立折叠",
            ja = "タップして展開 · Subsonic / WebDAV は個別に折りたためます",
        )
        else -> echoString(
            en = "Tap to set up Subsonic · WebDAV / cloud drive",
            zh = "点按配置 Subsonic · WebDAV / 网盘",
            ja = "タップして Subsonic · WebDAV / クラウドを設定",
        )
    }

@Composable
private fun remoteScanPhaseLabel(phase: LibraryScanPhase): String =
    when (phase) {
        LibraryScanPhase.Preparing -> echoString(en = "Preparing sync", zh = "准备同步", ja = "同期を準備中")
        LibraryScanPhase.QueryingMediaStore -> echoString(en = "Reading remote library", zh = "读取远程曲库", ja = "リモートライブラリを読み込み中")
        LibraryScanPhase.Diffing -> echoString(en = "Comparing index", zh = "对比索引", ja = "索引を照合中")
        LibraryScanPhase.WritingDatabase -> echoString(en = "Writing library", zh = "写入曲库", ja = "ライブラリに書き込み中")
        LibraryScanPhase.CleaningRemoved -> echoString(en = "Cleaning old index", zh = "清理旧索引", ja = "古い索引を整理中")
        LibraryScanPhase.Completed -> echoString(en = "Sync complete", zh = "同步完成", ja = "同期完了")
        LibraryScanPhase.Cancelled -> echoString(en = "Cancelled", zh = "已取消", ja = "キャンセル済み")
        LibraryScanPhase.Error -> echoString(en = "Sync failed", zh = "同步失败", ja = "同期失敗")
        LibraryScanPhase.Idle -> echoString(en = "Waiting to sync", zh = "等待同步", ja = "同期待ち")
    }


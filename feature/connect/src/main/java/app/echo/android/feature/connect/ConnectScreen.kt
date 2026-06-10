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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassCyan
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoGlassViolet
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoMetricTile
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.EchoSegmentChip
import app.echo.android.design.EchoTextButton
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.PageChrome
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoGlassRowBrush
import app.echo.android.design.formatDuration
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress

@Composable
fun ConnectScreen(
    remoteState: EchoRemoteConnectionState,
    pcTitle: String,
    trackTitle: String,
    trackArtist: String,
    isPlaying: Boolean,
    remoteError: String?,
    scanMessage: String?,
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
    PageChrome(title = "连接", subtitle = "串流服务 · PC 联动", badge = "互联", scrollable = true) {
        EchoSectionTitle("音乐服务", "连接你的曲库来源")
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
            name = "网易云音乐",
            subtitle = "歌单 · 每日推荐 · 私人 FM",
            icon = Icons.Rounded.CloudQueue,
            brandColor = Color(0xFFE0243A),
            statusLabel = "即将上线",
            active = false,
            locked = true,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        ServiceCard(
            name = "本地曲库",
            subtitle = "已扫描本机音频文件",
            icon = Icons.Rounded.LibraryMusic,
            brandColor = Color(0xFF35C28E),
            statusLabel = "已连接",
            active = true,
            locked = false,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        ServiceCard(
            name = "Discord Rich Presence",
            subtitle = discordPresenceTrackTitle?.let { "手机播放：$it" } ?: "通过 PC ECHO 转发手机播放状态",
            icon = Icons.Rounded.GraphicEq,
            brandColor = Color(0xFF5865F2),
            statusLabel = when {
                !discordPresenceEnabled -> "未开启"
                discordPresenceReady -> "待转发"
                else -> "等待 PC"
            },
            active = discordPresenceEnabled && discordPresenceReady,
            locked = !discordPresenceEnabled,
            onClick = {},
        )
        Spacer(Modifier.height(20.dp))
        EchoSectionTitle("设备联动", if (connected) "手机控制，PC 输出" else "配对后接管 PC ECHO 播放")
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
                        label = if (connected) "已配对" else "未配对",
                        active = connected,
                        locked = false,
                    )
                }
                if (connected) {
                    RemoteNowPlaying(
                        title = trackTitle,
                        artist = trackArtist,
                        isPlaying = isPlaying,
                        controlsEnabled = true,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                    )
                } else {
                    PcPairingInputs(
                        address = pcAddressInput,
                        token = pcTokenInput,
                        hasSavedPc = hasSavedPc,
                        autoReconnectEnabled = autoReconnectEnabled,
                        scanMessage = scanMessage,
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
                        text = if (connected) "已连接" else "连接 PC",
                        onClick = { onConnectPc(pcAddressInput, pcTokenInput) },
                        enabled = !connected && canConnectPc,
                    )
                    if (connected) {
                        TextButton(onClick = onDisconnect) {
                            Text("断开", color = EchoHomeBlue)
                        }
                    } else if (hasSavedPc) {
                        TextButton(
                            onClick = {
                                pcAddressInput = ""
                                pcTokenInput = ""
                                onForgetPc()
                            },
                        ) {
                            Text("忘记", color = EchoHomeBlue)
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
        Text(
            "输入 PC ECHO 地址和配对 Token",
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "支持 192.168.1.12:26789 或完整 http / https 地址",
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
                "PC 端显示二维码后可直接扫码配对",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RemoteCompactAction(
                text = "扫码配对",
                enabled = true,
                modifier = Modifier.width(92.dp),
                onClick = onScanPairingCode,
            )
        }
        scanMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                message,
                color = if (message.contains("不可用") || message.contains("没有识别")) scheme.error else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RemoteTextInput(
            label = "PC 地址",
            value = address,
            placeholder = "192.168.1.12:26789",
            onValueChange = onAddressChange,
        )
        RemoteTextInput(
            label = "配对 Token",
            value = token,
            placeholder = "从 PC ECHO 联动页复制",
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
                    "自动重连",
                    color = if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (hasSavedPc) "下次打开 Connect 时尝试连接上次的 PC" else "连接成功并保存后可用",
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
                "默认读取联动曲库",
                color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    checked && connected -> "联动 ECHO 曲库独立显示，已按 PC ECHO 作为当前联动源"
                    checked -> "连接后自动读取 PC ECHO，不并入本地扫描库"
                    else -> "本地曲库保持默认，可手动刷新联动曲库"
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
                        "远程曲库",
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
                    label = if (scanState.isScanning) "同步中" else if (readyCount > 0) "${readyCount} 个可用" else "待配置",
                    active = readyCount > 0 && scanState.phase != LibraryScanPhase.Error,
                    locked = readyCount == 0 && !scanState.isScanning,
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠远程曲库" else "展开远程曲库",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (expanded) {
                RemoteSourceProviderSection(
                    title = "Subsonic / Navidrome",
                    subtitle = "同步服务器曲库、封面和播放地址",
                    serverLabel = "服务器地址",
                    serverPlaceholder = "https://music.example.com",
                    usernameLabel = "用户名",
                    passwordLabel = "密码",
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
                    title = "WebDAV / 网盘",
                    subtitle = "按文件夹同步 NAS 或网盘音乐目录",
                    serverLabel = "WebDAV 地址",
                    serverPlaceholder = "https://dav.example.com/music",
                    usernameLabel = "WebDAV 用户名",
                    passwordLabel = "WebDAV 密码",
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
        scanState.isScanning && expanded -> "同步中"
        ready -> "可同步"
        hasInput -> "待补全"
        else -> "待配置"
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
                    contentDescription = if (expanded) "折叠" else "展开",
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
                placeholder = "用户名",
                onValueChange = onUsernameChange,
            )
            RemoteTextInput(
                label = passwordLabel,
                value = password,
                placeholder = "密码或应用专用密码",
                secret = true,
                onValueChange = onPasswordChange,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RemoteCompactAction(
                    text = "保存",
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                )
                RemoteCompactAction(
                    text = if (scanState.isScanning) "取消" else "同步",
                    enabled = ready || scanState.isScanning,
                    modifier = Modifier.weight(1f),
                    onClick = { if (scanState.isScanning) onCancel() else onSync() },
                )
                RemoteCompactAction(
                    text = "清除",
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

private fun remoteLibraryDetail(scanState: LibraryScanProgress, ready: Boolean): String =
    when {
        scanState.isScanning -> buildString {
            append(remoteScanPhaseLabel(scanState.phase))
            append(" · ")
            append(scanState.totalCount?.let { "${scanState.scannedCount}/$it" } ?: scanState.scannedCount.toString())
            scanState.currentTitle?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        scanState.phase == LibraryScanPhase.Completed -> {
            "同步完成：${scanState.scannedCount} 首，新增 ${scanState.insertedCount}，更新 ${scanState.updatedCount}，删除 ${scanState.deletedCount}"
        }
        scanState.phase == LibraryScanPhase.Error -> scanState.error ?: "远程曲库同步失败"
        ready -> "可同步到云端专辑墙"
        else -> "填写服务器、用户名和密码"
    }

private fun remoteSourcesSummary(scanState: LibraryScanProgress, readyCount: Int): String =
    when {
        scanState.isScanning -> remoteLibraryDetail(scanState, ready = true)
        scanState.phase == LibraryScanPhase.Completed -> remoteLibraryDetail(scanState, ready = true)
        scanState.phase == LibraryScanPhase.Error -> remoteLibraryDetail(scanState, ready = false)
        readyCount > 0 -> "点按展开 · Subsonic / WebDAV 可独立折叠"
        else -> "点按配置 Subsonic · WebDAV / 网盘"
    }

private fun remoteScanPhaseLabel(phase: LibraryScanPhase): String =
    when (phase) {
        LibraryScanPhase.Preparing -> "准备同步"
        LibraryScanPhase.QueryingMediaStore -> "读取远程曲库"
        LibraryScanPhase.Diffing -> "对比索引"
        LibraryScanPhase.WritingDatabase -> "写入曲库"
        LibraryScanPhase.CleaningRemoved -> "清理旧索引"
        LibraryScanPhase.Completed -> "同步完成"
        LibraryScanPhase.Cancelled -> "已取消"
        LibraryScanPhase.Error -> "同步失败"
        LibraryScanPhase.Idle -> "等待同步"
    }


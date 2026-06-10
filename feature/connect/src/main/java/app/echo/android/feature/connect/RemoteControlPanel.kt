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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
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
import app.echo.android.model.connect.EchoRemoteConnectionState

@Composable
internal fun ServiceCard(
    name: String,
    subtitle: String,
    icon: ImageVector,
    brandColor: Color,
    statusLabel: String,
    active: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (dark) EchoGlassPanel.copy(alpha = 0.58f) else scheme.surface.copy(alpha = 0.70f),
                        brandColor.copy(alpha = if (dark) if (locked) 0.16f else 0.25f else if (locked) 0.08f else 0.14f),
                        if (dark) EchoGlassViolet.copy(alpha = 0.12f) else EchoHomeMist.copy(alpha = 0.28f),
                    ),
                ),
            )
            .border(
                if (dark) echoDarkGlassBorder(active) else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)),
                RoundedCornerShape(20.dp),
            )
            .clickable(enabled = !locked, onClick = onClick)
            .padding(15.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(brandColor.copy(alpha = if (locked) 0.55f else 0.95f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ServiceStatusPill(label = statusLabel, active = active, locked = locked)
        }
    }
}

@Composable
internal fun ServiceStatusPill(
    label: String,
    active: Boolean,
    locked: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val background = when {
        locked -> if (dark) EchoGlassInk.copy(alpha = 0.50f) else scheme.surfaceVariant.copy(alpha = 0.52f)
        active -> Color(0xFF35C28E).copy(alpha = if (dark) 0.28f else 0.22f)
        else -> EchoAccent.copy(alpha = if (dark) 0.28f else 0.22f)
    }
    val foreground = when {
        locked -> scheme.onSurfaceVariant
        active -> Color(0xFF1A9B68)
        else -> EchoAccentText
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = BorderStroke(1.dp, if (dark) EchoDarkGlassBorder else Color.Transparent),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (locked) Icons.Rounded.Lock else Icons.Rounded.Check,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(13.dp),
            )
            Text(
                label,
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun PcLinkStatusStrip(connected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (dark) EchoGlassPanel.copy(alpha = 0.44f) else scheme.surface.copy(alpha = 0.56f),
        border = BorderStroke(
            1.dp,
            if (dark) EchoDarkGlassBorder else EchoGlassBorder.copy(alpha = 0.76f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    if (connected) "手机控制，PC 输出" else "等待 PC ECHO 配对",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (connected) "队列、音量、下一首将进入联动通道" else "配对后显示延迟、输出设备和队列状态",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PcHandoffPanel(connected: Boolean) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoSectionTitle(
                "接力控制台",
                if (connected) "本机和 PC 队列保持同步" else "完成配对后接管 PC 播放",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoSegmentChip("手机控制", selected = true, Modifier.weight(1f))
                EchoSegmentChip("PC 输出", selected = connected, Modifier.weight(1f))
                EchoSegmentChip("队列同步", selected = connected, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoMetricTile("延迟", if (connected) "24ms" else "--", Modifier.weight(1f), detail = "估算")
                EchoMetricTile("音量", if (connected) "同步" else "待机", Modifier.weight(1f), detail = "映射")
                EchoMetricTile("设备", if (connected) "桌面" else "未选", Modifier.weight(1f), detail = "输出")
            }
            EchoPlaceholderLine(
                if (connected) "下一首会同步到 PC ECHO" else "配对后显示 PC 队列和输出设备",
            )
            EchoPlaceholderLine(
                if (connected) "延迟监测和音量映射就绪" else "预留音量、延迟、输出设备联动",
            )
        }
    }
}

@Composable
internal fun RemoteNowPlaying(
    title: String,
    artist: String,
    isPlaying: Boolean,
    controlsEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (dark) EchoGlassPanel.copy(alpha = 0.44f) else scheme.surface.copy(alpha = 0.58f),
        border = BorderStroke(
            1.dp,
            if (dark) EchoDarkGlassBorder else EchoGlassBorder.copy(alpha = 0.76f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(null, Modifier.size(56.dp), accent = EchoColors.Coral, cornerRadius = 14.dp, elevation = 6.dp)
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlayPause, enabled = controlsEnabled) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "播放或暂停 PC")
            }
            IconButton(onClick = onNext, enabled = controlsEnabled) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "PC 下一首")
            }
        }
    }
}

@Composable
internal fun PairingPill(
    number: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (active) scheme.primary.copy(alpha = if (dark) 0.20f else 0.12f) else if (dark) EchoGlassPanel.copy(alpha = 0.38f) else scheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(
            1.dp,
            if (active) scheme.primary.copy(alpha = if (dark) 0.34f else 0.22f) else if (dark) EchoDarkGlassBorder else scheme.outlineVariant.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.24f else 0.14f)) {
                Text(
                    number,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun remoteConnectionLabel(state: EchoRemoteConnectionState): String =
    when (state) {
        EchoRemoteConnectionState.Disconnected -> "未连接"
        EchoRemoteConnectionState.Pairing -> "配对中"
        EchoRemoteConnectionState.Connecting -> "连接中"
        EchoRemoteConnectionState.Connected -> "已连接"
        EchoRemoteConnectionState.Reconnecting -> "重连中"
        EchoRemoteConnectionState.Error -> "错误"
    }


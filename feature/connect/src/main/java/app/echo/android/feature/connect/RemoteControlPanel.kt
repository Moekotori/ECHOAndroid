package app.echo.android.feature.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoGlassViolet
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoMetricTile
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.EchoSegmentChip
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoString
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
                    if (connected) {
                        echoString(en = "Control on phone, output on PC", zh = "手机控制，PC 输出", ja = "スマホで操作、PC で出力")
                    } else {
                        echoString(en = "Waiting for PC ECHO pairing", zh = "等待 PC ECHO 配对", ja = "PC ECHO のペアリング待ち")
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (connected) {
                        echoString(
                            en = "Queue, volume, and next track will use the link channel",
                            zh = "队列、音量、下一首将进入联动通道",
                            ja = "キュー、音量、次の曲は連携経路で扱います",
                        )
                    } else {
                        echoString(
                            en = "Latency, output device, and queue appear after pairing",
                            zh = "配对后显示延迟、输出设备和队列状态",
                            ja = "ペアリング後に遅延、出力デバイス、キューの状態を表示",
                        )
                    },
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
                echoString(en = "Handoff console", zh = "接力控制台", ja = "引き継ぎコンソール"),
                if (connected) {
                    echoString(en = "Phone and PC queues stay in sync", zh = "本机和 PC 队列保持同步", ja = "端末と PC のキューを同期します")
                } else {
                    echoString(en = "Become a remote after pairing", zh = "完成配对后进入遥控器", ja = "ペアリング後にリモコンとして使えます")
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoSegmentChip(echoString(en = "Phone control", zh = "手机控制", ja = "スマホ操作"), selected = true, Modifier.weight(1f))
                EchoSegmentChip(echoString(en = "PC output", zh = "PC 输出", ja = "PC 出力"), selected = connected, Modifier.weight(1f))
                EchoSegmentChip(echoString(en = "Queue sync", zh = "队列同步", ja = "キュー同期"), selected = connected, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoMetricTile(
                    echoString(en = "Latency", zh = "延迟", ja = "遅延"),
                    if (connected) "24ms" else "--",
                    Modifier.weight(1f),
                    detail = echoString(en = "Estimate", zh = "估算", ja = "推定"),
                )
                EchoMetricTile(
                    echoString(en = "Volume", zh = "音量", ja = "音量"),
                    if (connected) {
                        echoString(en = "Synced", zh = "同步", ja = "同期")
                    } else {
                        echoString(en = "Standby", zh = "待机", ja = "スタンバイ")
                    },
                    Modifier.weight(1f),
                    detail = echoString(en = "Mapping", zh = "映射", ja = "マッピング"),
                )
                EchoMetricTile(
                    echoString(en = "Device", zh = "设备", ja = "デバイス"),
                    if (connected) {
                        echoString(en = "Desktop", zh = "桌面", ja = "デスクトップ")
                    } else {
                        echoString(en = "None", zh = "未选", ja = "未選択")
                    },
                    Modifier.weight(1f),
                    detail = echoString(en = "Output", zh = "输出", ja = "出力"),
                )
            }
            EchoPlaceholderLine(
                if (connected) {
                    echoString(en = "The next track will sync to PC ECHO", zh = "下一首会同步到 PC ECHO", ja = "次の曲は PC ECHO に同期されます")
                } else {
                    echoString(
                        en = "PC queue and output device appear after pairing",
                        zh = "配对后显示 PC 队列和输出设备",
                        ja = "ペアリング後に PC のキューと出力デバイスを表示",
                    )
                },
            )
            EchoPlaceholderLine(
                if (connected) {
                    echoString(
                        en = "Latency monitoring and volume mapping are ready",
                        zh = "延迟监测和音量映射就绪",
                        ja = "遅延監視と音量マッピングの準備完了",
                    )
                } else {
                    echoString(
                        en = "Volume, latency, and output device linking are reserved",
                        zh = "预留音量、延迟、输出设备联动",
                        ja = "音量、遅延、出力デバイスの連携枠を用意しています",
                    )
                },
            )
        }
    }
}

@Composable
internal fun RemoteNowPlaying(
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    controlsEnabled: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
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
            EchoArtworkImage(
                artworkUri = artworkUrl,
                contentDescription = title,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                sizeClass = EchoArtworkSize.Thumbnail,
            )
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPrevious, enabled = controlsEnabled) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = echoString(en = "Previous on PC", zh = "PC 上一首", ja = "PC の前の曲"),
                )
            }
            IconButton(onClick = onPlayPause, enabled = controlsEnabled) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = echoString(en = "Play or pause PC", zh = "播放或暂停 PC", ja = "PC の再生 / 一時停止"),
                )
            }
            IconButton(onClick = onNext, enabled = controlsEnabled) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = echoString(en = "Next on PC", zh = "PC 下一首", ja = "PC の次の曲"),
                )
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

@Composable
internal fun remoteConnectionLabel(state: EchoRemoteConnectionState): String =
    when (state) {
        EchoRemoteConnectionState.Disconnected -> echoString(en = "Not connected", zh = "未连接", ja = "未接続")
        EchoRemoteConnectionState.Pairing -> echoString(en = "Pairing", zh = "配对中", ja = "ペアリング中")
        EchoRemoteConnectionState.Connecting -> echoString(en = "Connecting", zh = "连接中", ja = "接続中")
        EchoRemoteConnectionState.Connected -> echoString(en = "Connected", zh = "已连接", ja = "接続済み")
        EchoRemoteConnectionState.Reconnecting -> echoString(en = "Reconnecting", zh = "重连中", ja = "再接続中")
        EchoRemoteConnectionState.Error -> echoString(en = "Error", zh = "错误", ja = "エラー")
    }


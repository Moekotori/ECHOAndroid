package app.echo.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.PageChrome
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
fun SettingsScreen(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    showLyricsControlDeck: Boolean,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    var dynamicArtwork by rememberSaveable { mutableStateOf(true) }
    var compactMode by rememberSaveable { mutableStateOf(false) }
    var pcHandoff by rememberSaveable { mutableStateOf(true) }
    val sectionGap = if (compactMode) 8.dp else 14.dp

    PageChrome(
        title = "设置",
        subtitle = "移动端偏好与 ECHO 互联",
        badge = "设置",
        scrollable = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            SettingsHeroCard(
                status = status,
                trackCount = trackCount,
                albumCount = albumCount,
                artistCount = artistCount,
                dynamicArtwork = dynamicArtwork,
            )
            SettingsSectionCard(title = "界面") {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Palette,
                    title = "动态封面氛围",
                    detail = "播放页和迷你播放器跟随封面微调背景",
                    checked = dynamicArtwork,
                    onCheckedChange = { dynamicArtwork = it },
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Speed,
                    title = "紧凑显示",
                    detail = "为小屏幕减少卡片间距",
                    checked = compactMode,
                    onCheckedChange = { compactMode = it },
                )
            }
            SettingsSectionCard(title = "播放") {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "歌词同步工具",
                    detail = "在歌词页显示导入、格式和偏移微调面板",
                    checked = showLyricsControlDeck,
                    onCheckedChange = onShowLyricsControlDeckChange,
                )
            }
            SettingsSectionCard(title = "互联") {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Devices,
                    title = "PC 接力入口",
                    detail = "保留桌面端连接与远程控制入口",
                    checked = pcHandoff,
                    onCheckedChange = { pcHandoff = it },
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Devices,
                    title = "连接 PC ECHO",
                    detail = if (pcHandoff) "管理配对和远程播放状态" else "PC 接力入口已关闭",
                    enabled = pcHandoff,
                    onClick = onOpenConnect,
                )
            }
            SettingsSectionCard(title = "曲库") {
                SettingsActionRow(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "本地音乐",
                    detail = "查看歌曲、专辑、艺术家和扫描入口",
                    onClick = onOpenLibrary,
                )
            }
            Spacer(Modifier.height(156.dp))
        }
    }
}

@Composable
private fun SettingsHeroCard(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    dynamicArtwork: Boolean,
) {
    val heroColors = if (dynamicArtwork) {
        listOf(
            Color.White.copy(alpha = 0.88f),
            EchoHomeMist.copy(alpha = 0.82f),
            EchoAccentDeep.copy(alpha = 0.16f),
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.92f),
            EchoHomeMist.copy(alpha = 0.76f),
            Color.White.copy(alpha = 0.88f),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(heroColors),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder), RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                EchoSectionTitle(
                    title = "ECHO Mobile",
                    subtitle = status.track?.title ?: "本机播放就绪",
                    modifier = Modifier.weight(1f),
                )
                SettingsIconBubble(Icons.Rounded.Settings)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsStatTile("歌曲", trackCount.toString(), Modifier.weight(1f))
                SettingsStatTile("专辑", albumCount.toString(), Modifier.weight(1f))
                SettingsStatTile("艺术家", artistCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.74f))
            .border(BorderStroke(1.dp, EchoGlassBorder), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = RoonInk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRowShell(icon = icon, title = title, detail = detail) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsRowShell(
        icon = icon,
        title = title,
        detail = detail,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            if (enabled) "进入" else "关闭",
            color = if (enabled) EchoAccentText else RoonMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsRowShell(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.64f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.78f)), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBubble(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = RoonInk,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = RoonMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
private fun SettingsIconBubble(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(EchoHomeBlue.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, EchoAccent.copy(alpha = 0.20f)), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = EchoHomeBlue, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SettingsStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.64f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.78f)), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = RoonMuted, style = MaterialTheme.typography.labelMedium)
        Text(value, color = RoonInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

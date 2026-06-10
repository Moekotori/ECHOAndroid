package app.echo.android.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoTextButton
import app.echo.android.design.EmptyState
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.NeteaseAccountState
import app.echo.android.model.library.NeteaseAudioQuality
import app.echo.android.model.library.NeteaseImportState
import app.echo.android.model.library.NeteaseRemotePlaylist

@Composable
internal fun NeteasePlaylistPanel(
    accountState: NeteaseAccountState,
    importState: NeteaseImportState,
    importedPlaylists: List<EchoPlaylist>,
    selectedQuality: NeteaseAudioQuality,
    onLoginByPhone: (String, String) -> Unit,
    onLoginWithCookie: (String) -> Unit,
    onLogout: () -> Unit,
    onRefreshRemotePlaylists: () -> Unit,
    onOpenNeteaseApp: () -> Unit,
    onQualityChange: (NeteaseAudioQuality) -> Unit,
    onImportPlaylist: (Long) -> Unit,
    onOpenImportedPlaylist: (EchoPlaylist) -> Unit,
    onPlayImportedPlaylist: (EchoPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        item {
            NeteaseLoginCard(
                accountState = accountState,
                onLoginByPhone = onLoginByPhone,
                onLoginWithCookie = onLoginWithCookie,
                onLogout = onLogout,
                onRefreshRemotePlaylists = onRefreshRemotePlaylists,
                onOpenNeteaseApp = onOpenNeteaseApp,
            )
        }
        item {
            QualityCard(
                selectedQuality = selectedQuality,
                onQualityChange = onQualityChange,
            )
        }
        if (importState.message != null || importState.error != null || importState.importing) {
            item { ImportStatusCard(importState) }
        }
        if (accountState.loggedIn) {
            item { SectionTitle("账号歌单", "${accountState.playlists.size} 个可导入") }
            if (accountState.playlists.isEmpty() && !accountState.loading) {
                item { EmptyState("当前账号没有可导入歌单，或网易云接口暂时不可用。") }
            } else {
                items(
                    items = accountState.playlists,
                    key = { it.id },
                ) { playlist ->
                    RemotePlaylistRow(
                        playlist = playlist,
                        importing = importState.importing,
                        onImport = { onImportPlaylist(playlist.id) },
                    )
                }
            }
        }
        item { SectionTitle("已导入歌单", "${importedPlaylists.size} 个") }
        if (importedPlaylists.isEmpty()) {
            item { EmptyState("导入网易云歌单后会出现在这里，并可直接播放。") }
        } else {
            items(
                items = importedPlaylists,
                key = { it.id },
            ) { playlist ->
                ImportedPlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpenImportedPlaylist(playlist) },
                    onPlay = { onPlayImportedPlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
private fun NeteaseLoginCard(
    accountState: NeteaseAccountState,
    onLoginByPhone: (String, String) -> Unit,
    onLoginWithCookie: (String) -> Unit,
    onLogout: () -> Unit,
    onRefreshRemotePlaylists: () -> Unit,
    onOpenNeteaseApp: () -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var cookie by rememberSaveable { mutableStateOf("") }
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoIconBadge(Icons.AutoMirrored.Rounded.Login)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (accountState.loggedIn) "网易云已登录" else "网易云登录",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        accountState.nickname?.takeIf { accountState.loggedIn } ?: "手机号接口遇到验证码时，可用 MUSIC_U cookie 兜底",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButtonLite(icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = onOpenNeteaseApp)
                IconButtonLite(icon = Icons.Rounded.Refresh, onClick = onRefreshRemotePlaylists)
            }
            if (!accountState.loggedIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("手机号") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = { onLoginByPhone(phone, password) },
                    enabled = !accountState.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (accountState.loading) "登录中" else "手机号登录")
                }
                OutlinedTextField(
                    value = cookie,
                    onValueChange = { cookie = it },
                    label = { Text("MUSIC_U 或完整 Cookie") },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                EchoTextButton(
                    text = "使用 Cookie 登录",
                    onClick = { onLoginWithCookie(cookie) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EchoTextButton(text = "刷新歌单", onClick = onRefreshRemotePlaylists)
                    EchoTextButton(text = "退出网易云", onClick = onLogout)
                }
            }
            accountState.message?.let { StatusText(it, error = false) }
            accountState.error?.let { StatusText(it, error = true) }
        }
    }
}

@Composable
private fun QualityCard(
    selectedQuality: NeteaseAudioQuality,
    onQualityChange: (NeteaseAudioQuality) -> Unit,
) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "播放音质",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NeteaseAudioQuality.entries.forEach { quality ->
                    QualityChip(
                        quality = quality,
                        selected = selectedQuality == quality,
                        onClick = { onQualityChange(quality) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityChip(
    quality: NeteaseAudioQuality,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (selected) scheme.primary.copy(alpha = 0.16f) else EchoHomeMist.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, if (selected) scheme.primary.copy(alpha = 0.36f) else EchoGlassBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                quality.label,
                color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RemotePlaylistRow(
    playlist: NeteaseRemotePlaylist,
    importing: Boolean,
    onImport: () -> Unit,
) {
    PlaylistRowShell(
        artworkUri = playlist.artworkUri,
        title = playlist.name,
        subtitle = "${playlist.trackCount} 首 · ${playlist.creatorName ?: "网易云"}",
        trailing = {
            EchoTextButton(
                text = if (importing) "导入中" else "导入",
                onClick = onImport,
            )
        },
    )
}

@Composable
private fun ImportedPlaylistRow(
    playlist: EchoPlaylist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    PlaylistRowShell(
        artworkUri = playlist.artworkUri,
        title = playlist.name,
        subtitle = "${playlist.trackCount} 首 · 已导入曲库",
        onClick = onOpen,
        trailing = {
            IconButtonLite(icon = Icons.Rounded.PlayArrow, onClick = onPlay)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
private fun PlaylistRowShell(
    artworkUri: String?,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(EchoHomeMist.copy(alpha = 0.46f))
            .border(BorderStroke(1.dp, EchoGlassBorder), RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(
            artworkUri = artworkUri,
            modifier = Modifier.size(58.dp),
            accent = EchoAccent,
            cornerRadius = 12.dp,
            elevation = 3.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
private fun ImportStatusCard(importState: NeteaseImportState) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                importState.playlistName ?: "网易云导入",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            StatusText(
                text = importState.error
                    ?: importState.message
                    ?: "正在导入 ${importState.scannedCount} 首",
                error = importState.error != null,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusText(text: String, error: Boolean) {
    Text(
        text,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun IconButtonLite(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EchoAccentDeep.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

package app.echo.android.feature.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.model.library.LibraryScanProgress

private data class ScanGlassColors(
    val surface: Color,
    val optionSurface: Color,
    val border: Color,
    val content: Color,
    val muted: Color,
)

@Composable
private fun rememberScanGlassColors(): ScanGlassColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    return remember(scheme, dark) {
        ScanGlassColors(
            surface = if (dark) EchoGlassPanel.copy(alpha = 0.54f) else Color.White.copy(alpha = 0.96f),
            optionSurface = if (dark) EchoGlassPanel.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.74f),
            border = if (dark) EchoDarkGlassBorder else EchoGlassBorder,
            content = if (dark) scheme.onSurface else RoonInk,
            muted = if (dark) scheme.onSurfaceVariant.copy(alpha = 0.90f) else RoonMuted,
        )
    }
}

@Composable
internal fun LibraryScanAction(
    hasPermission: Boolean,
    scanState: LibraryScanProgress,
    onRequestPermission: () -> Unit,
    onScanFolder: () -> Unit,
    onScanAll: () -> Unit,
    onCancelScan: () -> Unit,
) {
    var showScanOptions by remember { mutableStateOf(false) }
    val colors = rememberScanGlassColors()
    val description = when {
        !hasPermission -> "授权音乐权限"
        scanState.isScanning -> "取消扫描曲库"
        else -> "扫描曲库"
    }
    val label = when {
        !hasPermission -> "授权"
        scanState.isScanning -> "停止"
        else -> "扫描"
    }
    val accent = when {
        scanState.error != null -> Color(0xFFE0796E)
        else -> colors.content
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = when {
                    !hasPermission -> onRequestPermission
                    scanState.isScanning -> onCancelScan
                    else -> {
                        { showScanOptions = true }
                    }
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.LibraryMusic,
            contentDescription = description,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }

    if (showScanOptions) {
        LibraryScanOptionsDialog(
            onDismiss = { showScanOptions = false },
            onScanFolder = {
                showScanOptions = false
                onScanFolder()
            },
            onScanAll = {
                showScanOptions = false
                onScanAll()
            },
        )
    }
}

@Composable
private fun LibraryScanOptionsDialog(
    onDismiss: () -> Unit,
    onScanFolder: () -> Unit,
    onScanAll: () -> Unit,
) {
    val colors = rememberScanGlassColors()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "扫描曲库",
                            color = colors.content,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "选择本次索引范围",
                            color = colors.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = colors.muted)
                    }
                }

                LibraryScanOption(
                    icon = Icons.Rounded.FolderOpen,
                    title = "扫描单个文件夹",
                    subtitle = "适合刚拷入音乐，只更新选中的目录",
                    onClick = onScanFolder,
                    accent = EchoHomeBlue,
                )
                LibraryScanOption(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "全盘扫描",
                    subtitle = "重新同步本机所有音乐，并清理已删除项目",
                    onClick = onScanAll,
                    accent = EchoAccentDeep,
                )
            }
        }
    }
}

@Composable
private fun LibraryScanOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accent: Color,
) {
    val colors = rememberScanGlassColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.16f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.optionSurface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = colors.content,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = colors.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Rounded.Scanner, contentDescription = null, tint = EchoAccent, modifier = Modifier.size(19.dp))
    }
}

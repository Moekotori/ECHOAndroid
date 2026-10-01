package app.echo.android.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.model.playback.PlaybackHistoryEntry

@Composable
internal fun PlaybackHistoryRow(
    entry: PlaybackHistoryEntry,
    time: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember(entry.id) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(enabled = entry.canReplay, onClick = onPlay).drawWithCache {
                val x = 54.dp.toPx()
                val stroke = 1.dp.toPx()
                val radius = 3.dp.toPx()
                onDrawBehind {
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
                    drawCircle(if (entry.canReplay) accent else line, radius, Offset(x, size.height / 2))
                }
            }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp), maxLines = 2)
            Spacer(Modifier.width(20.dp))
            EchoArtworkImage(entry.artworkUri, null, Modifier.size(52.dp).alpha(if (entry.canReplay) 1f else 0.55f),
                shape = RoundedCornerShape(6.dp), sizeClass = EchoArtworkSize.Thumbnail)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(entry.artist.takeIf { it.isNotBlank() }, entry.album?.takeIf { it.isNotBlank() })
                    .joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (entry.canReplay) Icon(Icons.Rounded.Headphones, null, Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (entry.canReplay) stringResource(R.string.playback_history_listened, historyDuration(entry.listenedMs))
                        else stringResource(R.string.playback_history_unavailable), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.playback_history_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.playback_history_delete)) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                    onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

private fun historyDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1_000L
    return if (seconds >= 3_600L) "%d:%02d:%02d".format(seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
    else "%d:%02d".format(seconds / 60L, seconds % 60L)
}

package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
    firstInDay: Boolean,
    lastInDay: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember(entry.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val line = scheme.outlineVariant.copy(alpha = 0.65f)
    val dot = scheme.onSurfaceVariant.copy(alpha = 0.65f)
    val metadata = remember(entry.artist, entry.album) {
        listOfNotNull(entry.artist.takeIf { it.isNotBlank() }, entry.album?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
    }
    val duration = remember(entry.listenedMs) { historyDuration(entry.listenedMs) }
    val moreDescription = stringResource(R.string.playback_history_more)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).drawWithCache {
                // Logical start keeps the rail beside the artwork in RTL layouts too.
                val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) 4.dp.toPx()
                    else size.width - 4.dp.toPx()
                val center = 36.dp.toPx()
                val stroke = 1.dp.toPx()
                val radius = 2.5.dp.toPx()
                onDrawBehind {
                    if (!firstInDay) drawLine(line, Offset(x, 0f), Offset(x, center - radius * 2), stroke)
                    if (!lastInDay) drawLine(line, Offset(x, center + radius * 2), Offset(x, size.height), stroke)
                    drawCircle(dot, radius, Offset(x, center))
                }
            }.clip(RoundedCornerShape(12.dp)).clickable(enabled = entry.canReplay && !busy,
                role = Role.Button, onClick = onPlay).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                EchoArtworkImage(entry.artworkUri, null, Modifier.size(48.dp).alpha(if (entry.canReplay) 1f else 0.55f),
                    shape = RoundedCornerShape(10.dp), sizeClass = EchoArtworkSize.Thumbnail)
                Text(time, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (metadata.isNotEmpty()) Text(metadata, style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 3.dp).clip(RoundedCornerShape(6.dp))
                    .background(scheme.onSurface.copy(alpha = 0.04f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (entry.canReplay) EchoIcon(Icons.Rounded.Headphones, null, Modifier.size(12.dp),
                        tint = scheme.onSurfaceVariant)
                    Text(if (entry.canReplay) stringResource(R.string.playback_history_listened, duration)
                        else stringResource(R.string.playback_history_unavailable), style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                EchoIcon(Icons.Rounded.MoreHoriz, "${entry.title} · $moreDescription",
                    tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp), containerColor = scheme.surface, tonalElevation = 0.dp) {
                DropdownMenuItem(text = { Text(stringResource(R.string.playback_history_delete)) },
                    leadingIcon = { EchoIcon(Icons.Rounded.DeleteOutline, null) }, enabled = !busy,
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

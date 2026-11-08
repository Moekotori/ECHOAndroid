package app.echo.android.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.PlaybackHistoryEntry

/** One listen on the timeline: artwork and clock on a rail, details beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackHistoryTimelineRow(
    entry: PlaybackHistoryEntry,
    time: String,
    firstInDay: Boolean,
    lastInDay: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.outlineVariant.copy(alpha = 0.65f)
    val dot = scheme.primary.copy(alpha = 0.7f)
    val metadata = remember(entry.artist, entry.album) {
        listOfNotNull(entry.artist.takeIf { it.isNotBlank() }, entry.album?.takeIf { it.isNotBlank() }).joinToString(" · ")
    }
    val duration = remember(entry.listenedMs) { PlaybackHistoryMarkdown.clock(entry.listenedMs) }
    val moreDescription = stringResource(R.string.playback_history_more)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).drawWithCache {
                // Logical start keeps the rail beside the artwork in RTL layouts too.
                val x = if (layoutDirection == LayoutDirection.Ltr) 4.dp.toPx() else size.width - 4.dp.toPx()
                val center = 38.dp.toPx()
                val stroke = 1.dp.toPx()
                val radius = 3.dp.toPx()
                onDrawBehind {
                    if (!firstInDay) drawLine(line, Offset(x, 0f), Offset(x, center - radius * 2), stroke)
                    if (!lastInDay) drawLine(line, Offset(x, center + radius * 2), Offset(x, size.height), stroke)
                    drawCircle(dot, radius, Offset(x, center))
                }
            }.clip(RoundedCornerShape(16.dp))
                .combinedClickable(enabled = !busy, role = Role.Button, onLongClick = onMore, onClick = { if (entry.canReplay) onPlay() else onMore() })
                .semantics { customActions = listOf(CustomAccessibilityAction(moreDescription) { onMore(); true }) }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                EchoArtworkImage(
                    entry.artworkUri, null, Modifier.size(52.dp).alpha(if (entry.canReplay) 1f else 0.55f),
                    shape = RoundedCornerShape(12.dp), sizeClass = EchoArtworkSize.Thumbnail,
                )
                Text(
                    time, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (metadata.isNotEmpty()) Text(
                    metadata, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = 3.dp).clip(RoundedCornerShape(8.dp)).background(scheme.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (entry.canReplay) EchoIcon(Icons.Rounded.Headphones, null, Modifier.size(12.dp), tint = scheme.onSurfaceVariant)
                    Text(
                        if (entry.canReplay) stringResource(R.string.playback_history_listened, duration)
                        else stringResource(R.string.playback_history_unavailable),
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onMore, enabled = !busy, modifier = Modifier.size(48.dp)) {
            EchoIcon(Icons.Rounded.MoreHoriz, "${entry.title} · $moreDescription", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * A ranked row for the track, album and artist views. [share] (0..1) draws a thin bar relative to the top
 * row when the list is sorted by plays, so the ranking can be read at a glance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackHistoryGroupedRow(
    entry: PlaybackHistoryEntry,
    rank: Int?,
    share: Float?,
    subtitle: String,
    busy: Boolean,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val moreDescription = stringResource(R.string.playback_history_more)
    val listened = formatListeningDuration(entry.listenedMs)
    val playsText = listeningPlayCount(entry.plays)
    val accent = scheme.primary
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(20.dp))
            .background(scheme.onSurface.copy(alpha = 0.04f))
            .combinedClickable(enabled = !busy, role = Role.Button, onLongClick = onMore, onClick = { if (entry.canReplay) onPlay() else onMore() })
            .semantics { customActions = listOf(CustomAccessibilityAction(moreDescription) { onMore(); true }) }
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) Text(
            rank.toString(), Modifier.width(28.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1,
        )
        EchoArtworkImage(
            entry.artworkUri, null, Modifier.size(56.dp).alpha(if (entry.canReplay) 1f else 0.55f),
            shape = RoundedCornerShape(14.dp), sizeClass = EchoArtworkSize.Thumbnail,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$playsText · $listened", style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (share != null) Box(
                Modifier.padding(top = 3.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50))
                    .background(scheme.onSurface.copy(alpha = 0.07f)),
            ) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0.04f, 1f)).height(3.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.75f)))
            }
        }
        IconButton(onClick = onMore, enabled = !busy, modifier = Modifier.size(48.dp)) {
            EchoIcon(Icons.Rounded.MoreHoriz, "${entry.title} · $moreDescription", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun PlaybackHistoryDateHeader(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 1.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

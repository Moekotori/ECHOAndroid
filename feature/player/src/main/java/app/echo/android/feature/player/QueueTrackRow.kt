package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoClickable
import app.echo.android.design.echoIconColor
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoTrackRef

@Composable
internal fun queueSurfaceColor(): Color =
    if (LocalEchoDarkTheme.current) Color(0xFF202124) else Color(0xFFFAFAFA)

@Composable
internal fun QueueTrackRow(
    track: EchoTrackRef,
    index: Int,
    lastIndex: Int,
    active: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    dropTarget: Boolean = false,
    interactive: Boolean = true,
    playing: Boolean = false,
    reorderable: Boolean = true,
) {
    val ink = echoIconColor()
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                onDrawBehind {
                    if (dropTarget) drawLine(ink, Offset.Zero, Offset(size.width, 0f), 2.dp.toPx())
                }
            }
            .then(if (interactive) Modifier.echoClickable(onClick = onPlay) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (active) {
                EchoIcon(
                    if (playing) Icons.Rounded.Equalizer else Icons.Rounded.Pause,
                    stringResource(R.string.feature_player_now_playing_214a7c),
                    Modifier.size(20.dp), tint = ink,
                )
            } else Text(
                (index + 1).toString().padStart(2, '0'),
                color = ink.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
        ArtworkTile(
            artworkUri = track.artworkUri,
            modifier = Modifier.size(44.dp),
            accent = ink.copy(alpha = 0.14f),
            cornerRadius = 0.dp,
            placeholderIconSize = 20.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title, color = ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                queueTrackDetail(track), color = ink.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!active) {
            if (reorderable) EchoIcon(
                Icons.Rounded.DragHandle, null, Modifier.size(20.dp), tint = ink.copy(alpha = 0.38f),
            )
            if (interactive) Box {
                IconButton(onClick = { menuExpanded = true }) {
                    EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.queue_actions), tint = ink)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (reorderable && index > 0) DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_player_move_up_e6d961)) },
                        leadingIcon = { EchoIcon(Icons.Rounded.KeyboardArrowUp, null) },
                        onClick = { menuExpanded = false; onMoveUp() },
                    )
                    if (reorderable && index < lastIndex) DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_player_move_down_cf81ae)) },
                        leadingIcon = { EchoIcon(Icons.Rounded.KeyboardArrowDown, null) },
                        onClick = { menuExpanded = false; onMoveDown() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_player_remove_track_60c516)) },
                        leadingIcon = { EchoIcon(Icons.Rounded.DeleteOutline, null) },
                        onClick = { menuExpanded = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
private fun queueTrackDetail(track: EchoTrackRef): String {
    val album = track.album?.takeIf { it.isNotBlank() }
    val duration = track.durationMs.takeIf { it > 0L }?.let(::formatDuration)
    return listOfNotNull(track.artist.takeIf { it.isNotBlank() }, album, duration).joinToString(" · ")
        .ifBlank { stringResource(R.string.feature_player_local_queue_c7d54b) }
}

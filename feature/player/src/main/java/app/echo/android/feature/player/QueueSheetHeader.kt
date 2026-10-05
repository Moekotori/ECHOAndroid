package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuDivider
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoIconColor
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRemotePinPolicy
import app.echo.android.model.playback.EchoSleepTimerMode
import app.echo.android.model.playback.PlaybackQueueState

@Composable
internal fun QueueSheetHeader(
    queueState: PlaybackQueueState,
    status: EchoPlaybackStatus,
    sections: QueueSections,
    showPrevious: Boolean,
    onDismiss: () -> Unit,
    onClearQueue: () -> Unit,
    onClearNextUp: () -> Unit,
    onLocateCurrent: () -> Unit,
    onTogglePrevious: () -> Unit,
    onPinQueueOffline: (() -> Unit)?,
    onSetSleepTimerEndOfTrack: (() -> Unit)?,
    onCancelSleepTimer: (() -> Unit)?,
) {
    val ink = echoIconColor()
    var expanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val canPin = remember(queueState.items) {
        queueState.items.any { EchoRemotePinPolicy.canPin(it.sourceId, it.id, it.uri) }
    }
    val stopAfterCurrent = status.sleepTimerMode == EchoSleepTimerMode.EndOfTrack
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.feature_player_queue_37fa6a), color = ink,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (queueState.isEmpty) status.track?.title ?: stringResource(R.string.feature_player_nothing_playing_3beac0)
                else stringResource(
                    R.string.feature_player_size_tracks_now_playing_current_7d4647,
                    queueState.items.size.toString(), (queueState.currentIndex + 1).coerceAtLeast(0).toString(),
                ),
                color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (stopAfterCurrent) Text(
                stringResource(R.string.queue_stop_after_current), color = ink.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (!queueState.isEmpty) Box {
            IconButton(onClick = { expanded = true }) {
                EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.queue_actions), Modifier.size(22.dp), tint = ink)
            }
            EchoDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                if (queueState.currentItem != null) EchoDropdownMenuItem(
                    text = { Text(stringResource(R.string.queue_locate_current)) }, selected = false,
                    leadingIcon = { EchoIcon(Icons.Rounded.MyLocation, null) },
                    onClick = { expanded = false; onLocateCurrent() },
                )
                if (sections.previous.isNotEmpty()) EchoDropdownMenuItem(
                    text = { Text(stringResource(if (showPrevious) R.string.queue_hide_previous else R.string.queue_show_previous)) },
                    selected = showPrevious, leadingIcon = { EchoIcon(Icons.Rounded.History, null) },
                    onClick = { expanded = false; onTogglePrevious() },
                )
                if (queueState.currentItem != null && onSetSleepTimerEndOfTrack != null && onCancelSleepTimer != null) EchoDropdownMenuItem(
                    text = { Text(stringResource(R.string.queue_stop_after_current)) }, selected = stopAfterCurrent,
                    leadingIcon = { EchoIcon(Icons.Rounded.Bedtime, null) },
                    onClick = {
                        expanded = false
                        if (stopAfterCurrent) onCancelSleepTimer() else onSetSleepTimerEndOfTrack()
                    },
                )
                if (canPin && onPinQueueOffline != null) EchoDropdownMenuItem(
                    text = { Text(stringResource(R.string.queue_keep_offline)) }, selected = false,
                    leadingIcon = { EchoIcon(Icons.Rounded.Download, null) },
                    onClick = { expanded = false; onPinQueueOffline() },
                )
                EchoDropdownMenuDivider()
                if (sections.nextUp.isNotEmpty()) EchoDropdownMenuItem(
                    text = { Text(stringResource(R.string.queue_clear_next_up)) }, selected = false,
                    leadingIcon = { EchoIcon(Icons.Rounded.ClearAll, null) },
                    onClick = { expanded = false; onClearNextUp() },
                )
                EchoDropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_player_clear_queue_eae952)) }, selected = false,
                    leadingIcon = { EchoIcon(Icons.Rounded.DeleteOutline, null) },
                    onClick = { expanded = false; confirmClear = true },
                )
            }
        }
        IconButton(onClick = onDismiss) {
            EchoIcon(Icons.Rounded.Close, stringResource(R.string.feature_player_close_queue_a65caf), Modifier.size(22.dp), tint = ink)
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.feature_player_clear_queue_eae952)) },
        text = { Text(stringResource(R.string.queue_clear_confirmation)) },
        confirmButton = {
            TextButton(onClick = { confirmClear = false; onClearQueue() }) {
                Text(stringResource(R.string.feature_player_clear_queue_eae952))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.track_tools_cancel)) }
        },
    )
}

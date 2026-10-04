package app.echo.android.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

internal enum class LibraryBatchCommand { Favorite, Unfavorite, Playlist, Queue, Next, NextUp, Tags, Delete }

/** Covers the normal page header without moving the list or replacing the player dock. */
@Composable
internal fun LibraryBatchToolbar(
    count: Int,
    busy: Boolean,
    error: Boolean,
    canEditTags: Boolean,
    canDelete: Boolean,
    deleting: Boolean,
    onExit: () -> Unit,
    onClear: () -> Unit,
    onCommand: (LibraryBatchCommand) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    var expanded by remember { mutableStateOf(false) }
    val enabled = !busy && count > 0
    Surface(color = scheme.surface, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 360.dp
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BatchToolbarButton(Icons.Rounded.Close, R.string.batch_cancel, !busy, onExit)
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    AnimatedContent(
                        targetState = count,
                        transitionSpec = {
                            if (lightweight) ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                            else EchoMotion.stateChange()
                        },
                        label = "library-selected-count",
                    ) { displayedCount ->
                        Text(
                            pluralStringResource(R.plurals.batch_selected_count, displayedCount, displayedCount),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (deleting) stringResource(R.string.batch_deleting)
                        else stringResource(R.string.batch_selection_limit, LibraryBatchSelection.Limit),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (compact) BatchToolbarButton(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.batch_playlist, enabled) {
                    onCommand(LibraryBatchCommand.Playlist)
                } else TextButton(
                    onClick = { onCommand(LibraryBatchCommand.Playlist) },
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface),
                    modifier = Modifier.widthIn(max = 160.dp),
                ) {
                    EchoIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(20.dp),
                        tint = scheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
                    Text(stringResource(R.string.batch_playlist), Modifier.weight(1f, fill = false).padding(start = 6.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    BatchToolbarButton(Icons.Rounded.MoreHoriz, R.string.batch_actions, enabled) { expanded = true }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        val commands = remember {
                            listOf(
                                Triple(R.string.batch_playlist, Icons.AutoMirrored.Rounded.PlaylistAdd, LibraryBatchCommand.Playlist),
                                Triple(R.string.batch_next, Icons.Rounded.SkipNext, LibraryBatchCommand.Next),
                                Triple(R.string.batch_favorite, Icons.Rounded.FavoriteBorder, LibraryBatchCommand.Favorite),
                                Triple(R.string.batch_unfavorite, Icons.Rounded.FavoriteBorder, LibraryBatchCommand.Unfavorite),
                                Triple(R.string.batch_queue, Icons.Rounded.Queue, LibraryBatchCommand.Queue),
                                Triple(R.string.batch_next_up, Icons.Rounded.Queue, LibraryBatchCommand.NextUp),
                                Triple(R.string.batch_tags, Icons.Rounded.Edit, LibraryBatchCommand.Tags),
                            )
                        }
                        commands.forEach { (label, icon, command) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                leadingIcon = { EchoIcon(icon, null) },
                                enabled = enabled && (command != LibraryBatchCommand.Tags || canEditTags),
                                onClick = { expanded = false; onCommand(command) },
                            )
                        }
                        if (canDelete) DropdownMenuItem(
                            text = { Text(stringResource(R.string.batch_delete), color = scheme.error) },
                            leadingIcon = { EchoIcon(Icons.Rounded.DeleteOutline, null) },
                            onClick = { expanded = false; onCommand(LibraryBatchCommand.Delete) },
                            enabled = enabled,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.batch_clear_selection)) },
                            leadingIcon = { EchoIcon(Icons.Rounded.ClearAll, null) },
                            onClick = { expanded = false; onClear() },
                            enabled = enabled,
                        )
                    }
                }
            }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error) {
                Text(
                    stringResource(R.string.library_tool_error),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = scheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BatchToolbarButton(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        EchoIcon(
            icon,
            stringResource(label),
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

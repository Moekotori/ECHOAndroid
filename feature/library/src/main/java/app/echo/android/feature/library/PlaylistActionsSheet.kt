package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.EchoSheetRow
import app.echo.android.design.echoClickable
import app.echo.android.model.library.EchoPlaylist

/** Catalog actions use the same sheet language as songs, with playback kept within easy reach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistActionsSheet(
    playlist: EchoPlaylist,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onExport: () -> Unit,
    onEnqueue: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onPinShortcut: (() -> Unit)?,
    onAdd: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onArtwork: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onEditRules: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    // Bound the content rather than the sheet so its anchors and safe insets remain correct.
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { windowHeight.toDp() } * 0.82f
    EchoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            EchoSheetHeader(
                title = playlistDisplayName(playlist),
                subtitle = playlistCaption(playlist),
                leading = {
                    ArtworkTile(playlist.artworkUri, Modifier.size(56.dp),
                        accent = rememberLibraryArtworkAccent(), cornerRadius = 12.dp, elevation = 0.dp)
                },
                trailing = {
                    IconButton(onClick = onDismiss) {
                        EchoIcon(Icons.Rounded.Close, stringResource(R.string.feature_library_cancel_4c5fa5))
                    }
                },
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState(), overscrollEffect = null)
                .padding(bottom = 16.dp)) {
                if (playlist.trackCount > 0) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaylistPlaybackAction(stringResource(R.string.feature_library_play_all_55c80e),
                            Icons.Rounded.PlayArrow, onPlay, Modifier.weight(1f))
                        PlaylistPlaybackAction(stringResource(R.string.feature_library_shuffle_34e7ce),
                            Icons.Rounded.Shuffle, onShuffle, Modifier.weight(1f))
                    }
                    onEnqueue?.let { EchoSheetRow(stringResource(R.string.playlist_enqueue),
                        Icons.AutoMirrored.Rounded.QueueMusic, it, chevron = false, compact = true) }
                    PlaylistActionDivider()
                }
                EchoSheetRow(stringResource(R.string.playlist_import_view), Icons.AutoMirrored.Rounded.OpenInNew, onOpen, compact = true)
                onAdd?.let { EchoSheetRow(stringResource(R.string.playlist_add_tracks), Icons.AutoMirrored.Rounded.PlaylistAdd, it, compact = true) }
                if (playlist.trackCount > 0) {
                    onShare?.let { EchoSheetRow(stringResource(R.string.playlist_share), Icons.Rounded.Share, it, compact = true) }
                    onCopy?.let { EchoSheetRow(stringResource(R.string.playlist_copy), Icons.Rounded.ContentCopy, it, compact = true) }
                    EchoSheetRow(stringResource(R.string.feature_library_export_m3u_6f18a4), Icons.Rounded.FileDownload, onExport, compact = true)
                }
                if (onArtwork != null || onRename != null || onEditRules != null || onPin != null || onPinShortcut != null) {
                    PlaylistActionDivider()
                    onPinShortcut?.let { EchoSheetRow(stringResource(R.string.library_desktop_shortcut), Icons.Rounded.Home, it, compact = true) }
                    onArtwork?.let { EchoSheetRow(stringResource(R.string.playlist_change_cover), Icons.Rounded.Image, it, compact = true) }
                    onRename?.let { EchoSheetRow(stringResource(R.string.feature_library_rename_playlist_757bb7), Icons.Rounded.Edit, it, compact = true) }
                    onEditRules?.let { EchoSheetRow(stringResource(R.string.smart_edit_rules), Icons.Rounded.Tune, it, compact = true) }
                    onPin?.let { EchoSheetRow(
                        stringResource(if (playlist.pinnedToHome) R.string.smart_unpin_home else R.string.smart_pin_home),
                        Icons.Rounded.PushPin, it, chevron = false, compact = true)
                    }
                }
                onDelete?.let {
                    PlaylistActionDivider()
                    EchoSheetRow(stringResource(playlist.removalLabel), Icons.Rounded.DeleteOutline, it,
                        danger = true, chevron = false, compact = true)
                }
            }
        }
    }
}

@Composable
private fun PlaylistPlaybackAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    ) {
        Row(Modifier.echoClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            val tint = MaterialTheme.colorScheme.onSurface
            EchoIcon(icon, null, Modifier.size(22.dp), tint = tint)
            Text(label, color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PlaylistActionDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

internal val EchoPlaylist.canRemoveFromCatalog: Boolean
    get() = !isLikedSongs && (canEdit || isSmartPlaylist)

internal val EchoPlaylist.removalLabel: Int
    get() = if (smartKind != null) R.string.smart_hide_builtin else R.string.feature_library_delete_playlist_4d9753

@Composable
internal fun PlaylistRemovalDialog(playlist: EchoPlaylist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val builtin = playlist.smartKind != null
    val name = playlistDisplayName(playlist)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(playlist.removalLabel)) },
        text = { Text(stringResource(if (builtin) R.string.smart_hide_builtin_message
            else R.string.feature_library_delete_playlist_name_songs_in_the_library_will_3541db, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (builtin) R.string.smart_hide_confirm else R.string.feature_library_delete_138ccf))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) }
        },
    )
}

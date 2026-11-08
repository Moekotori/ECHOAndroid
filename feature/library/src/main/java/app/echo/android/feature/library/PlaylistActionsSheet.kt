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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaylistPlaybackAction(stringResource(R.string.feature_library_play_all_55c80e),
                        Icons.Rounded.PlayArrow, playlist.trackCount > 0, onPlay, Modifier.weight(1f))
                    PlaylistPlaybackAction(stringResource(R.string.feature_library_shuffle_34e7ce),
                        Icons.Rounded.Shuffle, playlist.trackCount > 0, onShuffle, Modifier.weight(1f))
                }
                PlaylistActionDivider()
                EchoSheetRow(stringResource(R.string.playlist_import_view), Icons.AutoMirrored.Rounded.OpenInNew, onOpen)
                onAdd?.let { EchoSheetRow(stringResource(R.string.playlist_add_tracks), Icons.AutoMirrored.Rounded.PlaylistAdd, it) }
                onCopy?.let { EchoSheetRow(stringResource(R.string.playlist_copy), Icons.Rounded.ContentCopy, it,
                    enabled = playlist.trackCount > 0) }
                if (onArtwork != null || onRename != null || onEditRules != null || onPin != null) {
                    PlaylistActionDivider()
                    onArtwork?.let { EchoSheetRow(stringResource(R.string.playlist_change_cover), Icons.Rounded.Image, it) }
                    onRename?.let { EchoSheetRow(stringResource(R.string.feature_library_rename_playlist_757bb7), Icons.Rounded.Edit, it) }
                    onEditRules?.let { EchoSheetRow(stringResource(R.string.smart_edit_rules), Icons.Rounded.Tune, it) }
                    onPin?.let { EchoSheetRow(
                        stringResource(if (playlist.pinnedToHome) R.string.smart_unpin_home else R.string.smart_pin_home),
                        Icons.Rounded.PushPin, it, chevron = false)
                    }
                }
                PlaylistActionDivider()
                EchoSheetRow(stringResource(R.string.feature_library_export_m3u_6f18a4), Icons.Rounded.FileDownload, onExport,
                    enabled = playlist.trackCount > 0)
                onDelete?.let { EchoSheetRow(stringResource(R.string.feature_library_delete_playlist_4d9753),
                    Icons.Rounded.DeleteOutline, it, danger = true, chevron = false) }
            }
        }
    }
}

@Composable
private fun PlaylistPlaybackAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    ) {
        Row(Modifier.echoClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
            EchoIcon(icon, null, Modifier.size(22.dp), tint = tint)
            Text(label, color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PlaylistActionDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

package app.echo.android.feature.player

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoTrackRef

/** The app supplies cross-feature navigation and source-specific capabilities. */
class NowPlayingSongActions(
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val retry: (() -> Unit)? = null,
    val addToPlaylist: (() -> Unit)? = null,
    val share: (() -> Unit)? = null,
    val equalizer: (() -> Unit)? = null,
    val album: (() -> Unit)? = null,
    val info: (() -> Unit)? = null,
    val onlineAssets: (() -> Unit)? = null,
    val folder: (() -> Unit)? = null,
    val hide: (() -> Unit)? = null,
    val delete: (() -> Unit)? = null,
)

@Composable
internal fun NowPlayingSongActionRows(
    actions: NowPlayingSongActions,
    onAction: (() -> Unit) -> Unit,
    onOpenQueue: () -> Unit,
    track: EchoTrackRef?,
) {
    if (actions.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
    if (actions.loadFailed) {
        Text(stringResource(R.string.song_menu_load_failed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        actions.retry?.let { retry -> TextButton(onClick = retry, enabled = !actions.loading) {
            Text(stringResource(R.string.song_menu_retry))
        } }
    }
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.song_menu_copy_info)
    val copiedLabel = stringResource(R.string.song_menu_info_copied)
    val copyInfo: (() -> Unit)? = track?.let { selected ->
        {
            context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, songClipboardText(selected)))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val rows = listOf(
        Triple(R.string.song_menu_add_playlist, Icons.AutoMirrored.Rounded.PlaylistAdd, actions.addToPlaylist),
        Triple(R.string.song_menu_share, Icons.Rounded.Share, actions.share),
        Triple(R.string.song_menu_equalizer, Icons.Rounded.Equalizer, actions.equalizer),
        Triple(R.string.song_menu_album, Icons.Rounded.Album, actions.album),
        Triple(R.string.song_menu_info, Icons.Rounded.Info, actions.info),
        Triple(R.string.song_menu_copy_info, Icons.Rounded.ContentCopy, copyInfo),
        Triple(R.string.song_menu_online_assets, Icons.Rounded.CloudDownload, actions.onlineAssets),
        Triple(R.string.song_menu_folder, Icons.Rounded.FolderOpen, actions.folder),
    )
    rows.forEach { (label, icon, action) ->
        if (action != null) PlaybackActionRow(
            icon = icon, text = stringResource(label), framed = false, compact = true,
            onClick = { onAction(action) },
        )
    }
    PlaybackActionRow(
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        text = stringResource(R.string.feature_player_queue_37fa6a),
        framed = false, compact = true, onClick = onOpenQueue,
    )
    if (actions.hide != null || actions.delete != null) {
        HorizontalDivider(Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    actions.hide?.let { action -> PlaybackActionRow(
        icon = Icons.Rounded.VisibilityOff, text = stringResource(R.string.song_menu_hide),
        framed = false, compact = true, onClick = { onAction(action) },
    ) }
    actions.delete?.let { action -> PlaybackActionRow(
        icon = Icons.Rounded.DeleteOutline, text = stringResource(R.string.song_menu_delete),
        framed = false, compact = true, danger = true, onClick = { onAction(action) },
    ) }
}

internal fun songClipboardText(track: EchoTrackRef): String = listOf(
    listOf(track.title.trim(), track.artist.trim()).filter(String::isNotBlank).joinToString(" - "),
    track.album?.trim().orEmpty(),
).filter(String::isNotBlank).joinToString("\n")

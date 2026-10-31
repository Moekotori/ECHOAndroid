package app.echo.android.feature.player

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
import androidx.compose.ui.res.stringResource

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
)

@Composable
internal fun NowPlayingSongActionRows(actions: NowPlayingSongActions, onAction: (() -> Unit) -> Unit) {
    if (actions.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
    if (actions.loadFailed) {
        Text(stringResource(R.string.song_menu_load_failed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        actions.retry?.let { retry -> TextButton(onClick = retry, enabled = !actions.loading) {
            Text(stringResource(R.string.song_menu_retry))
        } }
    }
    val rows = listOf(
        Triple(R.string.song_menu_add_playlist, Icons.AutoMirrored.Rounded.PlaylistAdd, actions.addToPlaylist),
        Triple(R.string.song_menu_share, Icons.Rounded.Share, actions.share),
        Triple(R.string.song_menu_equalizer, Icons.Rounded.Equalizer, actions.equalizer),
        Triple(R.string.song_menu_album, Icons.Rounded.Album, actions.album),
        Triple(R.string.song_menu_info, Icons.Rounded.Info, actions.info),
        Triple(R.string.song_menu_online_assets, Icons.Rounded.CloudDownload, actions.onlineAssets),
        Triple(R.string.song_menu_folder, Icons.Rounded.FolderOpen, actions.folder),
    )
    rows.forEach { (label, icon, action) ->
        if (action != null) PlaybackActionRow(
            icon = icon, text = stringResource(label), framed = false,
            onClick = { onAction(action) },
        )
    }
}

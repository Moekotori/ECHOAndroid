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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.echo.android.model.settings.EchoPlayerMenuAction
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
internal fun NowPlayingSongActionStatus(actions: NowPlayingSongActions) {
    if (actions.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
    if (actions.loadFailed) {
        Text(stringResource(R.string.song_menu_load_failed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        actions.retry?.let { retry -> TextButton(onClick = retry, enabled = !actions.loading) {
            Text(stringResource(R.string.song_menu_retry))
        } }
    }
}

@Composable
internal fun NowPlayingSongActionRow(
    item: EchoPlayerMenuAction,
    actions: NowPlayingSongActions,
    onAction: (() -> Unit) -> Unit,
    onOpenQueue: () -> Unit,
    track: EchoTrackRef?,
) {
    val action: (() -> Unit)? = when (item) {
        EchoPlayerMenuAction.AddToPlaylist -> actions.addToPlaylist
        EchoPlayerMenuAction.Share -> actions.share
        EchoPlayerMenuAction.Equalizer -> actions.equalizer
        EchoPlayerMenuAction.Album -> actions.album
        EchoPlayerMenuAction.Info -> actions.info
        EchoPlayerMenuAction.OnlineAssets -> actions.onlineAssets
        EchoPlayerMenuAction.Folder -> actions.folder
        EchoPlayerMenuAction.Hide -> actions.hide
        EchoPlayerMenuAction.Delete -> actions.delete
        EchoPlayerMenuAction.Queue -> onOpenQueue
        EchoPlayerMenuAction.CopyInfo -> {
            val context = LocalContext.current
            val copyLabel = stringResource(R.string.song_menu_copy_info)
            val copiedLabel = stringResource(R.string.song_menu_info_copied)
            track?.let { selected -> {
                context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                    clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, songClipboardText(selected)))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                    }
                }
            } }
        }
        else -> null
    }
    if (action != null) PlaybackActionRow(
        icon = item.icon(), text = stringResource(item.labelRes()), framed = false, compact = true,
        danger = item == EchoPlayerMenuAction.Delete,
        onClick = { if (item == EchoPlayerMenuAction.Queue) action() else onAction(action) },
    )
}

internal fun songClipboardText(track: EchoTrackRef): String = listOf(
    listOf(track.title.trim(), track.artist.trim()).filter(String::isNotBlank).joinToString(" - "),
    track.album?.trim().orEmpty(),
).filter(String::isNotBlank).joinToString("\n")

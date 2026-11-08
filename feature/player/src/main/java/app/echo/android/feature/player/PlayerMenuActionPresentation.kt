package app.echo.android.feature.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import app.echo.android.model.settings.EchoPlayerMenuAction

internal fun EchoPlayerMenuAction.labelRes(): Int = when (this) {
    EchoPlayerMenuAction.Appearance -> R.string.player_appearance_title
    EchoPlayerMenuAction.AddToPlaylist -> R.string.song_menu_add_playlist
    EchoPlayerMenuAction.Share -> R.string.song_menu_share
    EchoPlayerMenuAction.Equalizer -> R.string.song_menu_equalizer
    EchoPlayerMenuAction.Album -> R.string.song_menu_album
    EchoPlayerMenuAction.Info -> R.string.song_menu_info
    EchoPlayerMenuAction.CopyInfo -> R.string.song_menu_copy_info
    EchoPlayerMenuAction.OnlineAssets -> R.string.song_menu_online_assets
    EchoPlayerMenuAction.Folder -> R.string.song_menu_folder
    EchoPlayerMenuAction.Queue -> R.string.feature_player_queue_37fa6a
    EchoPlayerMenuAction.Hide -> R.string.song_menu_hide
    EchoPlayerMenuAction.Delete -> R.string.song_menu_delete
    EchoPlayerMenuAction.Repeat -> R.string.feature_player_playback_mode
    EchoPlayerMenuAction.Shuffle -> R.string.feature_player_shuffle
    EchoPlayerMenuAction.SleepTimer -> R.string.feature_player_sleep_timer_108738
    EchoPlayerMenuAction.MoreSettings -> R.string.playback_more_settings
}

internal fun EchoPlayerMenuAction.icon(): ImageVector = when (this) {
    EchoPlayerMenuAction.Appearance -> Icons.Rounded.Palette
    EchoPlayerMenuAction.AddToPlaylist -> Icons.AutoMirrored.Rounded.PlaylistAdd
    EchoPlayerMenuAction.Share -> Icons.Rounded.Share
    EchoPlayerMenuAction.Equalizer -> Icons.Rounded.Equalizer
    EchoPlayerMenuAction.Album -> Icons.Rounded.Album
    EchoPlayerMenuAction.Info -> Icons.Rounded.Info
    EchoPlayerMenuAction.CopyInfo -> Icons.Rounded.ContentCopy
    EchoPlayerMenuAction.OnlineAssets -> Icons.Rounded.CloudDownload
    EchoPlayerMenuAction.Folder -> Icons.Rounded.FolderOpen
    EchoPlayerMenuAction.Queue -> Icons.AutoMirrored.Rounded.QueueMusic
    EchoPlayerMenuAction.Hide -> Icons.Rounded.VisibilityOff
    EchoPlayerMenuAction.Delete -> Icons.Rounded.DeleteOutline
    EchoPlayerMenuAction.Repeat -> Icons.Rounded.Repeat
    EchoPlayerMenuAction.Shuffle -> Icons.Rounded.Shuffle
    EchoPlayerMenuAction.SleepTimer -> Icons.Rounded.Bedtime
    EchoPlayerMenuAction.MoreSettings -> Icons.Rounded.Settings
}

internal fun EchoPlayerMenuAction.available(actions: NowPlayingSongActions, hasTrack: Boolean): Boolean = when (this) {
    EchoPlayerMenuAction.AddToPlaylist -> actions.addToPlaylist != null
    EchoPlayerMenuAction.Share -> actions.share != null
    EchoPlayerMenuAction.Equalizer -> actions.equalizer != null
    EchoPlayerMenuAction.Album -> actions.album != null
    EchoPlayerMenuAction.Info -> actions.info != null
    EchoPlayerMenuAction.CopyInfo -> hasTrack
    EchoPlayerMenuAction.OnlineAssets -> actions.onlineAssets != null
    EchoPlayerMenuAction.Folder -> actions.folder != null
    EchoPlayerMenuAction.Hide -> actions.hide != null
    EchoPlayerMenuAction.Delete -> actions.delete != null
    else -> true
}

package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import app.echo.android.model.connect.EchoRemotePlaylist

@Composable
internal fun rememberLinkedPlaylistArtwork(
    playlist: EchoRemotePlaylist,
    enabled: Boolean,
    loadArtwork: suspend (EchoRemotePlaylist) -> String?,
): String? {
    if (playlist.artworkUrl != null || !enabled || playlist.trackCount <= 0) return playlist.artworkUrl
    return produceState<String?>(null, playlist, loadArtwork) {
        value = null
        value = loadArtwork(playlist)
    }.value
}

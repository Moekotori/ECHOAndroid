package app.echo.android.feature.library

import androidx.compose.runtime.*
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

internal data class TrackInfoPlaylistState(
    val playlists: List<EchoPlaylist>? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

@Composable
internal fun rememberTrackInfoPlaylists(track: EchoTrack, tools: LibraryMediaTools?, retry: Int): State<TrackInfoPlaylistState> {
    val flow = remember(track.id, tools) { tools?.containingPlaylists?.invoke(track) }
    return key(track.id, flow) {
        produceState(TrackInfoPlaylistState(loading = flow != null), retry) {
            if (flow == null) { value = TrackInfoPlaylistState(); return@produceState }
            value = TrackInfoPlaylistState(loading = true)
            try { flow.collect { value = TrackInfoPlaylistState(playlists = it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = TrackInfoPlaylistState(failed = true) }
        }
    }
}

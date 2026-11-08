package app.echo.android.data

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import app.echo.android.model.library.EchoPlaylist

data class TrackPlaylistMembershipRow(val id: String, val name: String, val source: String)

/** Only actual memberships and favorites; no per-playlist queries or track-list loading. */
fun EchoLibraryRepository.observeTrackPlaylists(trackId: String) =
    database.playlistDao().observeContainingPlaylists(trackId)
        .map { rows -> rows.map { EchoPlaylist(id = it.id, name = it.name, source = it.source) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

/** Reads memberships for one song without loading every playlist's tracks. */
fun EchoLibraryRepository.observeTrackPlaylistIds(trackId: String) =
    database.playlistDao().observeContainingPlaylistIds(trackId)
        .map { it.toSet() }
        .distinctUntilChanged()

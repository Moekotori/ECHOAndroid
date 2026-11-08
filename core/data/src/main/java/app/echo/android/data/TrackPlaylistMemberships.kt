package app.echo.android.data

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import app.echo.android.model.library.EchoPlaylist

data class TrackPlaylistMembershipRow(val id: String, val name: String, val source: String)

/** Only actual memberships and favorites; no per-playlist queries or track-list loading. */
fun EchoLibraryRepository.observeTrackPlaylists(trackId: String) = kotlinx.coroutines.flow.flow {
    val uriKey = if (FilePlaylistStore.ownsTrack(trackId)) trackId.substringAfterLast(':')
        else database.trackDao().getTrackById(trackId)?.contentUri?.let(FilePlaylistStore::uriKey)
    val ordinary = database.playlistDao().observeContainingPlaylists(trackId)
    if (uriKey == null) emitAll(ordinary.map { rows -> rows.map { EchoPlaylist(it.id, it.name, source = it.source) } })
    else emitAll(kotlinx.coroutines.flow.combine(ordinary, database.playlistDao().observePrivateContainingPlaylists(uriKey)) { normal, files ->
        (normal + files).distinctBy { it.id }.map { EchoPlaylist(it.id, it.name, source = it.source) }
    })
}.distinctUntilChanged().flowOn(Dispatchers.IO)

fun EchoLibraryRepository.observeTrackPlaylistIds(trackId: String) = observeTrackPlaylists(trackId).map { rows -> rows.mapTo(hashSetOf()) { it.id } }.distinctUntilChanged()

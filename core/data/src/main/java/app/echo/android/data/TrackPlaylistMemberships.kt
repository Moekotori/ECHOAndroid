package app.echo.android.data

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Reads memberships for one song without loading every playlist's tracks. */
fun EchoLibraryRepository.observeTrackPlaylistIds(trackId: String) =
    database.playlistDao().observeContainingPlaylistIds(trackId)
        .map { it.toSet() }
        .distinctUntilChanged()

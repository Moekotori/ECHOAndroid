package app.echo.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun EchoLibraryRepository.searchUserPlaylists(query: String) = withContext(Dispatchers.IO) {
    database.playlistDao().searchEveryPlaylist(query.trim()).map { it.toEchoPlaylist() }
}

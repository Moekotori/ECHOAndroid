package app.echo.android.nfc

import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.feature.library.NfcMusicChoice
import app.echo.android.playback.EchoPlaybackLibraryIds
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded local catalog lookups, independent of whichever library page is currently visible. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NfcMusicLibrary(private val database: EchoLibraryDatabase) {
    suspend fun available(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        if (EchoPlaybackLibraryIds.albumKey(mediaId) == null && EchoPlaybackLibraryIds.playlistId(mediaId) == null)
            return@withContext false
        EchoPlaybackProcessRuntime.catalog().children(mediaId, 0, 1).isNotEmpty()
    }

    suspend fun choices(query: String): List<NfcMusicChoice> = withContext(Dispatchers.IO) {
        val albums = if (query.isBlank()) database.trackDao().listAlbumsForBrowse(40, 0)
            else database.trackDao().searchAlbums(query.take(100), 40)
        val playlists = EchoPlaybackProcessRuntime.catalog().widgetPlaylists(query.take(100)).take(40)
        albums.map { NfcMusicChoice(EchoPlaybackLibraryIds.album(it.albumKey), it.title, true, it.albumArtist ?: it.artist.orEmpty()) } +
            playlists.map { NfcMusicChoice(it.mediaId, it.title, false) }
    }
}

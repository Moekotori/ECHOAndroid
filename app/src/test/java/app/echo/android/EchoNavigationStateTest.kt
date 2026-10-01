package app.echo.android

import androidx.compose.runtime.saveable.SaverScope
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.EchoPlaylist
import org.junit.Assert.*
import org.junit.Test

class EchoNavigationStateTest {
    private val scope = SaverScope { true }

    @Test
    fun albumDetailRestoresNullableMetadataAndCanBeClosed() {
        val album = AlbumSummary("album", "Title", null, "Artist", null, 12, 1234L, null, 567L)
        val saved = with(AlbumNavigationSaver) { scope.save(album) }
        assertNotNull(saved)
        assertEquals(album, AlbumNavigationSaver.restore(saved!!))
        assertNull(with(AlbumNavigationSaver) { scope.save(null) })
    }

    @Test
    fun playlistRouteSnapshotDoesNotSaveTheTrackCollection() {
        val playlist = EchoPlaylist("playlist", "Playlist", List(10_000) { "track-$it" },
            artworkUri = "cover", updatedAtEpochMs = 42L, pinnedToHome = true)
        val saved = with(PlaylistNavigationSaver) { scope.save(playlist) }!!
        val restored = PlaylistNavigationSaver.restore(saved)!!
        assertEquals(playlist.copy(trackIds = emptyList()), restored)
        assertEquals(7, (saved as List<*>).size)
    }
}

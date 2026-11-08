package app.echo.android.feature.player

import app.echo.android.model.playback.EchoTrackRef
import org.junit.Assert.*
import org.junit.Test

class MusicFavoriteAvailabilityTest {
    @Test fun privateFilesAndOldQueueSnapshotsCannotEnterMusicFavorites() {
        val song = EchoTrackRef("mediastore:1", "content://media/1", "Song", "Artist")
        assertTrue(song.supportsMusicFavorite())
        assertFalse(song.copy(sourceId = "file-playlist").supportsMusicFavorite())
        assertFalse(song.copy(id = "file:playlist:entry", sourceId = null).supportsMusicFavorite())
    }
}

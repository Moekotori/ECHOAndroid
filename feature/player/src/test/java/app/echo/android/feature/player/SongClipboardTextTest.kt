package app.echo.android.feature.player

import app.echo.android.model.playback.EchoTrackRef
import org.junit.Assert.assertEquals
import org.junit.Test

class SongClipboardTextTest {
    @Test
    fun copiesSongMetadataWithoutSourceUri() {
        val track = EchoTrackRef("song", "https://example.test/song?token=private", " Faded ", " Alan Walker ", album = " Different World ")
        assertEquals("Faded - Alan Walker\nDifferent World", songClipboardText(track))
    }

    @Test
    fun omitsMissingMetadata() {
        assertEquals("Faded", songClipboardText(EchoTrackRef("song", "file:///music/song.flac", "Faded", " ", album = null)))
    }
}

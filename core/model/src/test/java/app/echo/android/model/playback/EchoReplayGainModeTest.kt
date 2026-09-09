package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoReplayGainModeTest {
    private val tags = EchoReplayGainTags(trackGainDb = -6f, albumGainDb = -3f)

    @Test
    fun autoPrefersTrackThenAlbum() {
        assertEquals(-6f, tags.selectedGainDb(EchoReplayGainMode.Auto))
        assertEquals(
            -3f,
            EchoReplayGainTags(albumGainDb = -3f).selectedGainDb(EchoReplayGainMode.Auto),
        )
        assertNull(EchoReplayGainTags().selectedGainDb(EchoReplayGainMode.Auto))
    }

    @Test
    fun trackAndAlbumModes() {
        assertEquals(-6f, tags.selectedGainDb(EchoReplayGainMode.Track))
        assertEquals(-3f, tags.selectedGainDb(EchoReplayGainMode.Album))
        assertEquals(
            -6f,
            EchoReplayGainTags(trackGainDb = -6f).selectedGainDb(EchoReplayGainMode.Album),
        )
    }

    @Test
    fun fromIdFallsBackToAuto() {
        assertEquals(EchoReplayGainMode.Album, EchoReplayGainMode.fromId("album"))
        assertEquals(EchoReplayGainMode.Auto, EchoReplayGainMode.fromId("nope"))
        assertEquals(EchoReplayGainMode.Auto, EchoReplayGainMode.fromId(null))
    }
}

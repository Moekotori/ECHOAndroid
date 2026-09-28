package app.echo.android.feature.player

import app.echo.android.model.playback.EchoTrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioPlayerPresentationTest {
    private fun track(title: String, artist: String) = EchoTrackRef(
        id = "radio:echo-official-radio", uri = "https://echonext.moe/radio/stream",
        title = title, artist = artist,
    )

    @Test fun stationWithoutIcyKeepsItsIdentity() {
        val display = radioPlayerPresentation(track("ECHO Radio", "echonext.moe"))
        assertEquals("ECHO Radio", display.station)
        assertEquals("echonext.moe", display.host)
        assertNull(display.liveTitle)
    }

    @Test fun icySongDoesNotReplaceTheStationHeading() {
        val display = radioPlayerPresentation(track("Artist — Live song", "ECHO Radio"))
        assertEquals("ECHO Radio", display.station)
        assertEquals("Artist — Live song", display.liveTitle)
    }

    @Test fun repeatedStationNameIsNotAProgramTitle() {
        assertNull(radioPlayerPresentation(track("ECHO Radio", "ECHO Radio")).liveTitle)
    }
}

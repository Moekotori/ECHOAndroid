package app.echo.android.model.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoRadioDirectoryTest {
    @Test
    fun directoryHitMapsToAPlayableSavedStation() {
        val hit = EchoRadioDirectoryStation(
            id = "uuid-1",
            name = "BBC Radio 6",
            url = "https://radio.example/live",
            country = "United Kingdom",
            bitrateKbps = 128,
            codec = "MP3",
        )
        val station = hit.toStation()
        assertEquals("uuid-1", station.id)
        assertEquals("BBC Radio 6", station.name)
        assertEquals("https://radio.example/live", station.url)
        assertEquals("radio:uuid-1", station.toTrack().id)
        assertTrue(EchoRadioStation.isRadio(station.toTrack().id))
    }

    @Test
    fun emptySearchIsInactiveUntilAQueryStarts() {
        assertFalse(EchoRadioDirectorySearch().active)
        assertTrue(EchoRadioDirectorySearch(query = "bbc").active)
        assertTrue(EchoRadioDirectorySearch(loading = true).active)
        assertTrue(EchoRadioDirectorySearch(failed = true).active)
    }
}

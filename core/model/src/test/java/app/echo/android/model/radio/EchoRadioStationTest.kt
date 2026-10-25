package app.echo.android.model.radio

import org.junit.Assert.*
import org.junit.Test

class EchoRadioStationTest {
    @Test fun selectedAlternativeUsesItsOwnCodecAndKeepsStationIdentity() {
        val primary = "https://radio.example/live.m3u8"
        val alternate = "https://radio.example/live.mp3"
        val station = EchoRadioStation("id", "My station", primary, codec = "HLS",
            streams = listOf(EchoRadioStream(alternate, "MP3")), defaultStreamUrl = alternate)
        assertEquals(alternate, station.toTrack().uri)
        assertNull(station.toTrack().mimeType)
        assertEquals("application/x-mpegURL", station.toTrack(primary).mimeType)
        assertEquals("radio:id", station.toTrack(alternate).id)
    }
    @Test fun acceptsDirectStreamsAndHlsWithQueryParameters() {
        listOf("http://radio.example:8000/live", "https://radio.example/live.m3u8?token=abc", " https://radio.example/stream ").forEach {
            assertTrue(it, EchoRadioStation.validUrl(it))
        }
    }
    @Test fun rejectsUnsupportedOrMalformedAddresses() {
        listOf("", "radio.example/live", "ftp://radio.example/live", "file:///music.mp3", "https://", "https://radio.example:99999/live", "https://user:pass@radio.example/live", "https://radio.example/live#fragment", "https://radio.example/a b").forEach {
            assertFalse(it, EchoRadioStation.validUrl(it))
        }
    }
    @Test fun radioIdentitySurvivesTrackMappingAndDoesNotCollideWithSongs() {
        val track = EchoRadioStation("station-1", "My Radio", "https://radio.example/live").toTrack()
        assertEquals("radio:station-1", track.id)
        assertEquals("My Radio", track.title)
        assertEquals(0L, track.durationMs)
        assertTrue(EchoRadioStation.isRadio(track.id))
        assertFalse(EchoRadioStation.isRadio("mediastore:1"))
        assertFalse(EchoRadioStation.isRadio(null))
        assertFalse(track.source.isLocalAudioFile)
    }
}

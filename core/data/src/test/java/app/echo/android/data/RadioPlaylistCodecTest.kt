package app.echo.android.data

import app.echo.android.model.radio.*
import org.junit.Assert.*
import org.junit.Test

class RadioPlaylistCodecTest {
    @Test fun pcCompatibleJsonRoundTripsAllStationFields() {
        val station = EchoRadioStation("id", "Radio", "https://example.com/live", "https://example.com/logo.png", true, 1234,
            "Description", "jazz", "https://example.com/", "MP3", 128, "JP", "japanese",
            listOf(EchoRadioStream("https://example.com/alternate", "AAC", 256)), "https://example.com/alternate", "https://example.com/video.m3u8")
        assertEquals(station, RadioPlaylistCodec.parse(RadioPlaylistCodec.export(listOf(station))).single())
        val playlist = RadioPlaylistCodec.parse(RadioPlaylistCodec.exportM3u(listOf(station))).single()
        assertEquals(station.name, playlist.name)
        assertEquals(station.url, playlist.url)
    }
    @Test fun importsM3uPlsAndRelativeUrlsWithoutPlaying() {
        val m3u = RadioPlaylistCodec.parse("#EXTM3U\n#EXTINF:-1,Jazz\n/live.mp3\nfile:///private/file\n", "https://example.com/stations/list.m3u")
        assertEquals("Jazz", m3u.single().name)
        assertEquals("https://example.com/live.mp3", m3u.single().url)
        val pls = RadioPlaylistCodec.parse("[playlist]\nFile1=https://example.com/live\nTitle1=My station\nNumberOfEntries=1")
        assertEquals("My station", pls.single().name)
    }
    @Test fun filtersAndPaginationAreSentToTheDirectory() {
        val url = RadioBrowserClient.browseUrl("https://de1.api.radio-browser.info", RadioDirectoryQuery("jazz", "jp", "japanese", "vocaloid", 2))
        assertEquals("JP", url.queryParameter("countrycode"))
        assertEquals("40", url.queryParameter("offset"))
        assertEquals("21", url.queryParameter("limit"))
        assertEquals("vocaloid", url.queryParameter("tag"))
    }
    @Test fun rejectsInvalidAlternativeAndKeepsArtworkFromDirectory() {
        val json = """[{"name":"Invalid","url":"https://example.com/","streams":[{"url":"file:///private"}]}]"""
        assertTrue(runCatching { RadioPlaylistCodec.parse(json) }.isFailure)
        val hit = RadioBrowserParser.parseStations("""[{"stationuuid":"id","name":"Good","url":"https://example.com/live","favicon":"https://example.com/logo.png"}]""").single()
        assertEquals("https://example.com/logo.png", hit.toStation().toTrack().artworkUri)
        assertEquals("radio:id", hit.toStation().toTrack().id)
    }
}

package app.echo.android.playback

import app.echo.android.model.playback.EchoTrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoRadioNowPlayingTest {
    @Test
    fun songReplacesTitleAndStationBecomesSubtitle() {
        val display = EchoRadioNowPlaying.resolve(
            stationName = "My Radio",
            host = "radio.example",
            liveTitle = "DJ SHARPNEL - Touch the angel",
            rememberedTitle = null,
        )
        assertEquals("DJ SHARPNEL - Touch the angel", display.title)
        assertEquals("My Radio", display.artist)
    }

    @Test
    fun missingSongKeepsStationAndHost() {
        val display = EchoRadioNowPlaying.resolve(
            stationName = " My Radio ",
            host = "radio.example",
            liveTitle = "   ",
            rememberedTitle = null,
        )
        assertEquals("My Radio", display.title)
        assertEquals("radio.example", display.artist)
    }

    @Test
    fun stationNameInStreamIsNotASong() {
        val display = EchoRadioNowPlaying.resolve(
            stationName = "My Radio",
            host = "radio.example",
            liveTitle = " My   Radio ",
            rememberedTitle = "Old song",
        )
        assertEquals("Old song", display.title)
        assertEquals("My Radio", display.artist)
    }

    @Test
    fun liveSongWinsOverRememberedSong() {
        val display = EchoRadioNowPlaying.resolve(
            stationName = "My Radio",
            host = "radio.example",
            liveTitle = "Next song",
            rememberedTitle = "Old song",
        )
        assertEquals("Next song", display.title)
    }

    @Test
    fun rememberedSongSurvivesBlankLiveTitle() {
        val display = EchoRadioNowPlaying.resolve(
            stationName = "My Radio",
            host = "radio.example",
            liveTitle = "",
            rememberedTitle = "Artist - First Song",
        )
        assertEquals("Artist - First Song", display.title)
        assertEquals("My Radio", display.artist)
    }

    @Test
    fun cleanDecodesIcyHtmlAndCollapsesSpace() {
        assertEquals("穴", EchoRadioNowPlaying.clean("&#31348;"))
        assertEquals("東京", EchoRadioNowPlaying.clean("&#26481;&#20140;"))
        assertEquals("東京", EchoRadioNowPlaying.clean("&#x6771;&#x4eac;"))
        assertEquals("Fish & Chips", EchoRadioNowPlaying.clean("Fish &amp; Chips"))
        assertEquals("A \"B\" <C>", EchoRadioNowPlaying.clean("A &quot;B&quot; &lt;C&gt;"))
        assertEquals("Rock 'n' Roll", EchoRadioNowPlaying.clean("  Rock  'n'  Roll  "))
        assertEquals("A B", EchoRadioNowPlaying.clean("A&nbsp;&nbsp;B"))
        assertEquals("東", EchoRadioNowPlaying.clean("&amp;#26481;"))
        assertEquals("東京", EchoRadioNowPlaying.clean("&amp;#26481;&#20140;"))
    }

    @Test
    fun cleanLeavesInvalidEntities() {
        assertEquals("&#x;", EchoRadioNowPlaying.clean("&#x;"))
        assertEquals("&#999999999;", EchoRadioNowPlaying.clean("&#999999999;"))
        assertEquals("&#0;", EchoRadioNowPlaying.clean("&#0;"))
    }

    @Test
    fun cleanCapsLengthWithoutSplittingASurrogate() {
        val long = "穴".repeat(EchoRadioNowPlaying.MaxChars)
        assertEquals(EchoRadioNowPlaying.MaxChars, EchoRadioNowPlaying.clean(long + "more").length)
        val capped = EchoRadioNowPlaying.clean("a".repeat(EchoRadioNowPlaying.MaxChars) + "穴")
        assertEquals(EchoRadioNowPlaying.MaxChars, capped.length)
        assertEquals("a".repeat(EchoRadioNowPlaying.MaxChars), capped)
        val splitPair = EchoRadioNowPlaying.clean("a".repeat(EchoRadioNowPlaying.MaxChars - 1) + "\uD83C\uDFB5b")
        assertEquals("a".repeat(EchoRadioNowPlaying.MaxChars - 1), splitPair)
    }

    @Test
    fun rememberedUpdateIgnoresBlankRepeatAndStationName() {
        assertNull(EchoRadioNowPlaying.rememberedUpdate("My Radio", "Old", "   "))
        assertNull(EchoRadioNowPlaying.rememberedUpdate("My Radio", "Old", "My Radio"))
        assertNull(EchoRadioNowPlaying.rememberedUpdate("My Radio", "Old", "Old"))
        assertEquals("New", EchoRadioNowPlaying.rememberedUpdate("My Radio", "Old", " New "))
        assertEquals("穴", EchoRadioNowPlaying.rememberedUpdate("My Radio", null, "&#31348;"))
    }

    @Test
    fun trackOverlayKeepsFallbacksWhenDisplayIsBlank() {
        val track = EchoTrackRef(id = "radio:1", uri = "https://radio.example/live", title = "Station", artist = "host")
        assertEquals(track, track.withRadioNowPlaying(null))
        val shown = track.withRadioNowPlaying(EchoRadioDisplay(title = "Song", artist = "Station"))
        assertEquals("Song", shown.title)
        assertEquals("Station", shown.artist)
        val blank = track.withRadioNowPlaying(EchoRadioDisplay(title = "", artist = ""))
        assertEquals("Station", blank.title)
        assertEquals("host", blank.artist)
    }
}

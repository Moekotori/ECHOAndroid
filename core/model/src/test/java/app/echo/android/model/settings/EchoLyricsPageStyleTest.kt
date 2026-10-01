package app.echo.android.model.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoLyricsPageStyleTest {
    @Test fun afterglowStylesRoundTripWithoutChangingExistingDefaults() {
        assertEquals(EchoLyricsPageStyle.AfterglowMist, EchoLyricsPageStyle.fromId("afterglow_mist"))
        assertEquals(EchoLyricsPageStyle.AfterglowNight, EchoLyricsPageStyle.fromId("afterglow_night"))
        assertEquals("center", EchoLyricsPageStyle.AfterglowMist.defaultAlignment)
        assertEquals("center", EchoLyricsPageStyle.AfterglowNight.defaultAlignment)
        assertEquals(true, EchoLyricsPageStyle.AfterglowMist.isAfterglow)
        assertEquals(false, EchoLyricsPageStyle.Mist.isAfterglow)
        assertEquals(false, EchoLyricsPageStyle.Paper.isAfterglow)
    }
    @Test fun unknownAndMissingStylesUseMist() {
        assertEquals(EchoLyricsPageStyle.Mist, EchoLyricsPageStyle.fromId(null))
        assertEquals(EchoLyricsPageStyle.Mist, EchoLyricsPageStyle.fromId("future_style"))
        assertEquals(EchoLyricsPageStyle.Mist, EchoLyricsPageStyle.fromId(""))
    }

    @Test fun storedStylesRetainTheirLayout() {
        val paper = EchoLyricsPageStyle.fromId("paper")
        assertEquals(EchoLyricsPageStyle.Paper, paper)
        assertEquals("serif", paper.defaultFontFamily)
        assertEquals("center", paper.defaultAlignment)
        val mist = EchoLyricsPageStyle.fromId("mist")
        assertEquals("system", mist.defaultFontFamily)
        assertEquals("start", mist.defaultAlignment)
    }
}

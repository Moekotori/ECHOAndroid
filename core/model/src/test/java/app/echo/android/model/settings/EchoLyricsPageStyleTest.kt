package app.echo.android.model.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoLyricsPageStyleTest {
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

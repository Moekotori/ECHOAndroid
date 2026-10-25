package app.echo.android.model.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoPlayerPageStyleTest {
    @Test fun everyStyleRoundTripsThroughItsStableId() {
        EchoPlayerPageStyle.entries.forEach { assertEquals(it, EchoPlayerPageStyle.fromId(it.id)) }
        assertEquals(EchoPlayerPageStyle.entries.size, EchoPlayerPageStyle.entries.map { it.id }.toSet().size)
    }

    @Test fun unknownIdsKeepFallingBackToClassic() {
        assertEquals(EchoPlayerPageStyle.Classic, EchoPlayerPageStyle.fromId("from_the_future"))
        assertEquals(EchoPlayerPageStyle.RecordSleeve, EchoPlayerPageStyle.fromId("paper"))
    }

    @Test fun brightnessMatchesEachSurface() {
        assertEquals(
            listOf(false, true, true, true, true, false, false, true, true, true, false, false, false, false, false),
            listOf(
                EchoPlayerPageStyle.Classic, EchoPlayerPageStyle.RecordSleeve, EchoPlayerPageStyle.PixelHandheld,
                EchoPlayerPageStyle.TypePoster, EchoPlayerPageStyle.AfterglowMist, EchoPlayerPageStyle.AfterglowNight,
                EchoPlayerPageStyle.Terminal, EchoPlayerPageStyle.Scrapbook,
                EchoPlayerPageStyle.MetroLine, EchoPlayerPageStyle.ChatThread, EchoPlayerPageStyle.Halftone,
                EchoPlayerPageStyle.PixelMosaic, EchoPlayerPageStyle.Contours,
                EchoPlayerPageStyle.Dither, EchoPlayerPageStyle.GlyphField,
            ).map { it.isLight },
        )
    }

    @Test fun newStylesReuseBackupCompatibleLyricPresets() {
        assertEquals(EchoLyricsPageStyle.Mist, EchoPlayerPageStyle.Terminal.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Paper, EchoPlayerPageStyle.Scrapbook.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Paper, EchoPlayerPageStyle.MetroLine.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Paper, EchoPlayerPageStyle.ChatThread.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Mist, EchoPlayerPageStyle.Halftone.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Mist, EchoPlayerPageStyle.PixelMosaic.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Mist, EchoPlayerPageStyle.Contours.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Mist, EchoPlayerPageStyle.Dither.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Mist, EchoPlayerPageStyle.GlyphField.lyricsPreset)
    }
}

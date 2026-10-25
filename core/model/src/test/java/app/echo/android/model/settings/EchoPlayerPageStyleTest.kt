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
            listOf(false, true, true, true, true, false, false, true),
            listOf(
                EchoPlayerPageStyle.Classic, EchoPlayerPageStyle.RecordSleeve, EchoPlayerPageStyle.PixelHandheld,
                EchoPlayerPageStyle.TypePoster, EchoPlayerPageStyle.AfterglowMist, EchoPlayerPageStyle.AfterglowNight,
                EchoPlayerPageStyle.Terminal, EchoPlayerPageStyle.Scrapbook,
            ).map { it.isLight },
        )
    }

    @Test fun newStylesReuseBackupCompatibleLyricPresets() {
        assertEquals(EchoLyricsPageStyle.Mist, EchoPlayerPageStyle.Terminal.lyricsPreset)
        assertEquals(EchoLyricsPageStyle.Paper, EchoPlayerPageStyle.Scrapbook.lyricsPreset)
    }
}

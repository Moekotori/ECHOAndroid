package app.echo.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsFrameRateTest {
    @Test fun leavingLyricsClearsTheRequestEvenDuringAnUnfinishedFling() {
        assertEquals(LyricsFrameRateHint.High, lyricsFrameRateHint(true, false, true, false, false))
        assertEquals(LyricsFrameRateHint.None, lyricsFrameRateHint(false, false, true, false, false))
    }
    @Test fun staticLyricsDoNotKeepThePreviousHighPerformanceRequest() {
        assertEquals(LyricsFrameRateHint.None, lyricsFrameRateHint(true, false, false, false, true))
    }
    @Test fun powerSavingWinsOverMotionAndHighPerformance() {
        assertEquals(LyricsFrameRateHint.Normal, lyricsFrameRateHint(true, true, true, true, true))
    }
}

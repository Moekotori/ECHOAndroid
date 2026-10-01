package app.echo.android.feature.player

import app.echo.android.model.settings.EchoEffectivePerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFrameGateTest {
    @Test fun defaultBudgetStaysAtSixtyOnHighRefreshDisplays() {
        val fps = lyricsInterpolationFps(EchoEffectivePerformanceMode.Balanced)
        assertEquals(60, fps)
        for (displayFps in listOf(60, 90, 120, 144, 240)) {
            val gate = LyricsFrameGate(fps)
            val published = (0 until displayFps * 2).count {
                gate.accept(it * 1_000_000_000L / displayFps)
            }
            assertEquals("display=$displayFps", 120, published)
        }
    }

    @Test fun explicitModesUseTheirOwnBudgets() {
        assertEquals(0, lyricsInterpolationFps(EchoEffectivePerformanceMode.Lightweight))
        val fps = lyricsInterpolationFps(EchoEffectivePerformanceMode.HighPerformance)
        assertEquals(120, fps)
        val gate = LyricsFrameGate(fps)
        assertEquals(240, (0 until 480).count { gate.accept(it * 1_000_000_000L / 240) })
    }

    @Test fun stalledFramesResumeWithoutCatchUpBursts() {
        val gate = LyricsFrameGate(60)
        assertTrue(gate.accept(0))
        assertFalse(gate.accept(8_333_333))
        assertTrue(gate.accept(1_000_000_000))
        assertFalse(gate.accept(1_000_000_000))
        assertFalse(gate.accept(1_008_333_333))
        assertTrue(gate.accept(1_016_666_666))
    }
}

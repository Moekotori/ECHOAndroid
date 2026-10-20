package app.echo.android.feature.player

import app.echo.android.model.settings.EchoEffectivePerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FluidBackdropClockTest {
    @Test
    fun performanceModesBoundDecorativeUpdates() {
        assertEquals(0, fluidBackdropFps(EchoEffectivePerformanceMode.Lightweight))
        assertEquals(24, fluidBackdropFps(EchoEffectivePerformanceMode.Balanced))
        assertEquals(30, fluidBackdropFps(EchoEffectivePerformanceMode.HighPerformance))
    }

    @Test
    fun resumePreservesPhaseWithoutCountingHiddenTime() {
        val clock = FluidBackdropClock()
        assertEquals(0L, clock.advance(1_000_000_000L))
        assertEquals(40L, clock.advance(1_040_000_000L))
        val resumed = FluidBackdropClock(40L)
        assertEquals(40L, resumed.advance(90_000_000_000L))
        assertEquals(80L, resumed.advance(90_040_000_000L))
    }

    @Test
    fun rateSlowsDecorativeTimeWhilePaused() {
        val clock = FluidBackdropClock()
        clock.advance(1_000_000_000L)
        assertEquals(40L, clock.advance(1_040_000_000L, 1f))
        assertEquals(41L, clock.advance(1_080_000_000L, 0.04f))
        assertEquals(41L, clock.advance(1_120_000_000L, 0f))
    }

    @Test
    fun stalledFramesDoNotJumpTheSurface() {
        val clock = FluidBackdropClock()
        clock.advance(1_000_000_000L)
        assertEquals(100L, clock.advance(10_000_000_000L))
        assertEquals(140L, clock.advance(10_040_000_000L))
    }
}

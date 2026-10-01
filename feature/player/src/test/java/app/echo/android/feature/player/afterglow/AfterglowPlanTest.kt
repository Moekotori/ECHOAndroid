package app.echo.android.feature.player.afterglow

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import org.junit.Assert.*
import org.junit.Test

class AfterglowPlanTest {
    @Test fun typographyChoosesReadableLayoutsWithoutRandomizingOnSeek() {
        assertEquals(AfterglowTypography.Hero, AfterglowTypography.choose(5, true, 87, false))
        assertEquals(AfterglowTypography.Vertical, AfterglowTypography.choose(10, true, 2, false))
        for (seed in 0..30) {
            assertNotEquals(AfterglowTypography.Vertical, AfterglowTypography.choose(10, false, seed, false))
            assertNotEquals(AfterglowTypography.Vertical, AfterglowTypography.choose(24, true, seed, false))
            assertEquals(AfterglowTypography.Subtitle, AfterglowTypography.choose(10, true, seed, true))
            assertEquals(AfterglowTypography.choose(24, true, seed, false), AfterglowTypography.choose(24, true, seed, false))
        }
    }
    @Test fun allSceneLayersShareOneRasterPixelBudget() {
        for (scene in AfterglowScene.entries) for (side in listOf(360, 540, 720)) {
            for ((width, height) in listOf(1080 to 2400, 2400 to 1080, 1600 to 1600)) {
                val raster = afterglowRasterSize(width, height, side, scene)
                assertTrue("$scene $side $width x $height", raster.totalPixels <= side * side)
                assertTrue(raster.width <= side && raster.height <= side)
            }
        }
    }
    @Test fun balancedDoesNotPublishAtTheDisplayRefreshRate() {
        for (displayFps in listOf(60, 90, 120, 240)) {
            val gate = AfterglowFrameGate(30)
            val frames = (0 until displayFps * 2).count { gate.accept(it * 1_000_000_000L / displayFps) }
            assertEquals("display=$displayFps", 60, frames)
        }
        val high = AfterglowFrameGate(60)
        assertEquals(120, (0 until 240).count { high.accept(it * 1_000_000_000L / 120) })
    }
    @Test fun seekAndOffsetFindTheSameCutInBothDirections() {
        val lyrics = EchoLyrics(lines = listOf(
            EchoLyricLine(6000, text = "three"), EchoLyricLine(1000, text = "one", endMs = 1800),
            EchoLyricLine(-1, text = "metadata"), EchoLyricLine(3000, text = "two"),
        ), offsetMs = 400)
        val plan = AfterglowPlan.build(lyrics, 12000)!!
        assertEquals(-1, plan.indexAt(500))
        assertEquals(2, plan.indexAt(8000))
        assertEquals(0, plan.indexAt(600 + lyrics.offsetMs))
        assertEquals(1800, plan.endAt(0))
        assertEquals(6000, plan.endAt(1))
        assertEquals(12000, plan.endAt(2))
        assertEquals(0, plan.indexAt(2200)) // instrumental gap retains context
        val blankMarker = AfterglowPlan.build(EchoLyrics(listOf(
            EchoLyricLine(0, text = "sing"), EchoLyricLine(2000, text = ""), EchoLyricLine(4000, text = "resume"),
        )), 9000)!!
        assertEquals(2000, blankMarker.endAt(0))
        assertEquals(0, blankMarker.indexAt(3000))
    }

    @Test fun invalidOrOversizedDocumentsFallBackWithoutDroppingLines() {
        assertNull(AfterglowPlan.build(EchoLyrics(listOf(EchoLyricLine(-1, text = "plain"))), 0))
        assertNull(AfterglowPlan.build(EchoLyrics(List(4097) { EchoLyricLine(it.toLong(), text = "line") }), 5000))
        assertNull(AfterglowPlan.build(EchoLyrics(listOf(EchoLyricLine(0, text = "x".repeat(1025)))), 5000))
    }

    @Test fun repeatedTimestampsAndMissingEndsRemainBounded() {
        val plan = AfterglowPlan.build(EchoLyrics(listOf(
            EchoLyricLine(0, text = "main"), EchoLyricLine(0, text = "backing"),
            EchoLyricLine(3000, endMs = 2000, text = "bad end"),
        )), 9000)!!
        assertEquals(0, plan.indexAt(0))
        assertEquals("main\nbacking", plan.lines[0].text)
        assertEquals(3000, plan.endAt(0))
        assertEquals(3001, plan.endAt(1))
    }

    @Test fun graphemesAndLiteralTimedWordsKeepTheirBoundaries() {
        val line = EchoLyricLine(0, text = "你好 🎵 e\u0301", words = listOf(
            EchoLyricWord(0, 1000, "你好"), EchoLyricWord(1000, 2000, "🎵"), EchoLyricWord(2000, 3000, "e\u0301"),
        ))
        val glyphs = afterglowGlyphs(line, 3000)
        assertEquals(listOf("你", "好", "🎵", "e\u0301"), glyphs.map { line.text.substring(it.first, it.end) })
        assertEquals(listOf(0L, 500L, 1000L, 2000L), glyphs.map { it.startMs })
        assertEquals(listOf(500L, 1000L, 2000L, 3000L), glyphs.map { it.endMs })
        assertTrue(afterglowGlyphs(line.copy(words = listOf(EchoLyricWord(0, 1000, "wrong"))), 3000).all { it.startMs == null })
    }

    @Test fun pauseSeekRateAndStalledHostCannotMakeTheDisplayRunAway() {
        val clock = AfterglowClock()
        assertEquals(1000, clock.position(1000, 10, true, 1f))
        assertEquals(1200, clock.position(1000, 110, true, 2f))
        assertEquals(2300, clock.position(1000, 9999, true, 2f))
        assertEquals(400, clock.position(400, 10000, true, 1f))
        assertEquals(400, clock.position(400, 11000, false, 1f))
        assertEquals(400, clock.position(400, 11000, true, Float.NaN))
        assertEquals(500, clock.position(400, 11100, true, Float.NaN))
    }

    @Test fun mobileBudgetsAndRandomizationStayFiniteAndReproducible() {
        val light = AfterglowBudget.forMode(EchoEffectivePerformanceMode.Lightweight)
        assertEquals(0, light.framesPerSecond)
        assertEquals(0, light.particles)
        assertFalse(light.glyphMotion)
        val balanced = AfterglowBudget.forMode(EchoEffectivePerformanceMode.Balanced)
        assertEquals(30, balanced.framesPerSecond)
        assertEquals(12, balanced.particles)
        val high = AfterglowBudget.forMode(EchoEffectivePerformanceMode.HighPerformance)
        assertEquals(60, high.framesPerSecond)
        assertEquals(24, high.particles)
        val beforeSeek = afterglowComposition(16, 42)
        afterglowComposition(100, 42)
        assertEquals(beforeSeek, afterglowComposition(16, 42))
        assertNotEquals(beforeSeek, afterglowComposition(16, 43))
    }
}

package app.echo.android.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkColorGridTest {
    private fun channel(color: Int, shift: Int) = color shr shift and 0xFF

    @Test
    fun vividKeepsHueAndClampsLightness() {
        val grid = ArtworkColorGrid.fromPixels(intArrayOf(0xFFFF0000.toInt(), 0xFF000000.toInt(), 0xFF808080.toInt(), 0xFF3366CC.toInt()), 2, 2)
        val red = grid.colorAt(0, 0)
        assertEquals(209, channel(red, 16))
        assertEquals(0, channel(red, 8))
        // Black is lifted to the lightness floor and stays neutral.
        val black = grid.colorAt(1, 0)
        assertEquals(82, channel(black, 16))
        assertEquals(channel(black, 16), channel(black, 8))
        // Mid gray has no hue to boost.
        assertEquals(0x80, channel(grid.colorAt(0, 1), 16))
        // A blue stays blue-dominant.
        val blue = grid.colorAt(1, 1)
        assertTrue(channel(blue, 0) > channel(blue, 16))
    }

    @Test
    fun sampleAveragesNeighborsAndEqualityIsByContent() {
        val a = ArtworkColorGrid(2, 1, intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        assertEquals(0x7F, channel(a.sample(0f, 0f), 16))
        assertEquals(a, ArtworkColorGrid(2, 1, intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())))
        assertNotEquals(a, ArtworkColorGrid(2, 1, intArrayOf(0xFF000000.toInt(), 0xFFFFFFFE.toInt())))
    }
}

package app.echo.android.design

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class EchoBackgroundBlurTest {
    @Test
    fun horizontalBlurClampsEdgesAndAveragesNeighbors() {
        val source = intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff000000.toInt())
        val result = IntArray(3)
        blurBackgroundPixels(source, result, 3, 1, 1, horizontal = true)
        assertArrayEquals(IntArray(3) { 0xff555555.toInt() }, result)
    }

    @Test
    fun verticalBlurDoesNotMixColumns() {
        val source = intArrayOf(0xffff0000.toInt(), 0xff0000ff.toInt(), 0xffff0000.toInt(), 0xff0000ff.toInt())
        val result = IntArray(4)
        blurBackgroundPixels(source, result, 2, 2, 8, horizontal = false)
        assertArrayEquals(source, result)
    }

    @Test
    fun transparentPixelsDoNotIntroduceColorFringes() {
        val source = intArrayOf(0x00ff0000, 0xff0000ff.toInt(), 0x00ff0000)
        val result = IntArray(3)
        blurBackgroundPixels(source, result, 3, 1, 1, horizontal = true)
        assertArrayEquals(IntArray(3) { 0x550000ff }, result)
    }
}

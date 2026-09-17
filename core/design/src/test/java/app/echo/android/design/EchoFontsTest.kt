package app.echo.android.design

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoFontsTest {
    @Test
    fun outfitRendersHeavierThanRequested() {
        assertEquals(700, outfitRenderedWeight(100))
        assertEquals(700, outfitRenderedWeight(400))
        assertEquals(700, outfitRenderedWeight(500))
        assertEquals(700, outfitRenderedWeight(600))
        assertEquals(700, outfitRenderedWeight(700))
        assertEquals(800, outfitRenderedWeight(800))
        assertEquals(900, outfitRenderedWeight(900))
    }
}

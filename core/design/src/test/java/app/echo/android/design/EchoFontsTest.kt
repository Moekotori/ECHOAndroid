package app.echo.android.design

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoFontsTest {
    @Test
    fun outfitPreservesBodyAndHeadingWeightHierarchy() {
        val fonts = (EchoOutfitFontFamily as FontListFontFamily).fonts.map { it as ResourceFont }
        assertEquals(listOf(400, 500, 600, 700, 800, 900), fonts.map { it.weight.weight })
        assertEquals(
            listOf(
                R.font.outfit_400, R.font.outfit_500, R.font.outfit_600,
                R.font.outfit_700, R.font.outfit_800, R.font.outfit_900,
            ),
            fonts.map { it.resId },
        )
    }
}

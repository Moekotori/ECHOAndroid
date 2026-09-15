package app.echo.android.design

import androidx.compose.ui.graphics.Color
import app.echo.android.model.settings.EchoColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSwitchColorsTest {
    @Test
    fun darkSwitchDropsTheMaterialOutlineAndKeepsAFilledOffTrack() {
        val tokens = echoThemeTokens(EchoColorTheme.Echo, dark = true)
        val colors = echoSwitchColors(tokens)
        assertEquals(Color.Transparent, colors.uncheckedBorderColor)
        assertEquals(Color.Transparent, colors.checkedBorderColor)
        assertEquals(Color.Transparent, colors.disabledUncheckedBorderColor)
        assertEquals(tokens.accent, colors.checkedTrackColor)
        assertEquals(tokens.onAccent, colors.checkedThumbColor)
        assertTrue(
            "off thumb on off track ${contrastRatio(colors.uncheckedThumbColor, colors.uncheckedTrackColor)}",
            contrastRatio(colors.uncheckedThumbColor, colors.uncheckedTrackColor) >= 3.0f,
        )
        assertTrue(
            "on thumb on on track ${contrastRatio(colors.checkedThumbColor, colors.checkedTrackColor)}",
            contrastRatio(colors.checkedThumbColor, colors.checkedTrackColor) >= 4.4f,
        )
        assertTrue(
            "off track should sit above the dark surface ${contrastRatio(colors.uncheckedTrackColor, tokens.ink)}",
            contrastRatio(colors.uncheckedTrackColor, tokens.ink) >= 1.15f,
        )
    }

    @Test
    fun everyPaletteSwitchIsFilledAndReadable() {
        for (theme in EchoColorTheme.entries) {
            for (dark in listOf(true, false)) {
                val tokens = echoThemeTokens(theme, dark)
                val colors = echoSwitchColors(tokens)
                val mode = if (dark) "dark" else "light"
                assertEquals("${theme.id} $mode unchecked border", Color.Transparent, colors.uncheckedBorderColor)
                assertEquals("${theme.id} $mode checked border", Color.Transparent, colors.checkedBorderColor)
                assertEquals(tokens.accent, colors.checkedTrackColor)
                assertTrue(
                    "${theme.id} $mode off thumb ${contrastRatio(colors.uncheckedThumbColor, colors.uncheckedTrackColor)}",
                    contrastRatio(colors.uncheckedThumbColor, colors.uncheckedTrackColor) >= 3.0f,
                )
                assertTrue(
                    "${theme.id} $mode on thumb ${contrastRatio(colors.checkedThumbColor, colors.checkedTrackColor)}",
                    contrastRatio(colors.checkedThumbColor, colors.checkedTrackColor) >= 4.4f,
                )
            }
        }
    }
}

package app.echo.android.design

import androidx.compose.ui.graphics.Color
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.model.settings.EchoCustomColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoCustomThemeTest {
    @Test
    fun presetsIgnoreCustomSeeds() {
        assertEquals(
            echoThemeTokens(EchoColorTheme.Echo, dark = true),
            echoThemeTokens(EchoColorTheme.Echo, dark = true, custom = RedGreenBlue),
        )
        assertEquals(0xFF080B12.toInt(), echoStartupWindowColor(EchoColorTheme.Echo, dark = true, RedGreenBlue))
    }

    @Test
    fun seedHueSurvivesBothModes() {
        val dark = echoThemeTokens(EchoColorTheme.Custom, dark = true, custom = RedGreenBlue)
        val light = echoThemeTokens(EchoColorTheme.Custom, dark = false, custom = RedGreenBlue)
        assertEquals(EchoColorTheme.Custom.id, dark.id)
        assertTrue(hueNear(dark.accent.toEchoHsl().hue, 0f))
        assertTrue(hueNear(dark.secondary.toEchoHsl().hue, 120f))
        assertTrue(hueNear(dark.bgTop.toEchoHsl().hue, 240f))
        assertTrue(hueNear(light.accent.toEchoHsl().hue, 0f))
        assertTrue(light.bgTop.toEchoHsl().lightness > dark.bgTop.toEchoHsl().lightness)
    }

    @Test
    fun extremeSeedsStayReadable() {
        val seeds = listOf(
            EchoCustomColors.Default,
            EchoCustomColors(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            EchoCustomColors(0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt()),
            RedGreenBlue,
            EchoCustomColors(0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt()),
        )
        for (seed in seeds) {
            for (dark in listOf(true, false)) {
                val tokens = echoThemeTokens(EchoColorTheme.Custom, dark, seed)
                val scheme = echoColorScheme(tokens)
                val colors = echoSwitchColors(tokens)
                val mode = if (dark) "dark" else "light"
                val label = "${seed.accent.toUInt().toString(16)} $mode"
                assertTrue("$label heading", contrastRatio(tokens.heading, tokens.panel) >= 4.4f)
                assertTrue("$label body", contrastRatio(tokens.onSurface, tokens.panel) >= 4.4f)
                assertTrue("$label muted", contrastRatio(tokens.muted, tokens.panel) >= 3.0f)
                assertTrue("$label page", contrastRatio(tokens.heading, tokens.bgTop) >= 4.4f)
                assertTrue("$label accent", contrastRatio(tokens.onAccent, tokens.accent) >= 4.4f)
                assertTrue(
                    "$label container",
                    contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.4f,
                )
                assertTrue(
                    "$label switch",
                    contrastRatio(colors.checkedThumbColor, colors.checkedTrackColor) >= 4.4f &&
                        contrastRatio(colors.uncheckedThumbColor, colors.uncheckedTrackColor) >= 3.0f,
                )
                assertTrue(scheme.primaryContainer != Color(0xFF4F378B))
            }
        }
    }

    private fun hueNear(actual: Float, expected: Float): Boolean {
        val delta = ((actual - expected + 540f) % 360f) - 180f
        return kotlin.math.abs(delta) <= 12f
    }

    private companion object {
        val RedGreenBlue = EchoCustomColors(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
    }
}

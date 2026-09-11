package app.echo.android.design

import app.echo.android.model.settings.EchoColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoThemeTokensTest {
    @Test
    fun unknownIdFallsBackToEcho() {
        assertEquals(EchoColorTheme.Echo, EchoColorTheme.fromId(null))
        assertEquals(EchoColorTheme.Echo, EchoColorTheme.fromId(""))
        assertEquals(EchoColorTheme.Echo, EchoColorTheme.fromId("nyanCat"))
        assertEquals(EchoColorTheme.Twilight, EchoColorTheme.fromId("twilight"))
    }

    @Test
    fun everyPaletteIdRoundTrips() {
        val ids = EchoColorTheme.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (theme in EchoColorTheme.entries) {
            assertEquals(theme, EchoColorTheme.fromId(theme.id))
            val dark = echoThemeTokens(theme, true)
            val light = echoThemeTokens(theme, false)
            assertEquals(theme.id, dark.id)
            assertEquals(theme.id, light.id)
            assertEquals(true, dark.dark)
            assertEquals(false, light.dark)
        }
    }

    @Test
    fun defaultPaletteKeepsCurrentEchoColors() {
        val dark = echoThemeTokens(EchoColorTheme.Echo, dark = true)
        val light = echoThemeTokens(EchoColorTheme.Echo, dark = false)
        assertEquals(EchoAccent, dark.accent)
        assertEquals(EchoAccentDeep, dark.accentDeep)
        assertEquals(EchoGlassNight, dark.night)
        assertEquals(EchoGlassInk, dark.ink)
        assertEquals(EchoGlassPanel, dark.panel)
        assertEquals(EchoBgTop, light.bgTop)
        assertEquals(RoonInk, light.heading)
        assertEquals(RoonMuted, light.muted)
        val darkScheme = echoColorScheme(dark)
        val lightScheme = echoColorScheme(light)
        assertEquals(EchoColors.Sky, darkScheme.primary)
        assertEquals(EchoColors.Night, darkScheme.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF925568), lightScheme.primary)
    }

    @Test
    fun everyPaletteHasReadableTextAndAccent() {
        for (theme in EchoColorTheme.entries) {
            for (dark in listOf(true, false)) {
                val tokens = echoThemeTokens(theme, dark)
                val mode = if (dark) "dark" else "light"
                val panel = tokens.panel
                val page = tokens.bgTop
                assertTrue(
                    "${theme.id} $mode heading on panel ${contrastRatio(tokens.heading, panel)}",
                    contrastRatio(tokens.heading, panel) >= 4.4f,
                )
                assertTrue(
                    "${theme.id} $mode body on panel ${contrastRatio(tokens.onSurface, panel)}",
                    contrastRatio(tokens.onSurface, panel) >= 4.4f,
                )
                assertTrue(
                    "${theme.id} $mode muted on panel ${contrastRatio(tokens.muted, panel)}",
                    contrastRatio(tokens.muted, panel) >= 3.0f,
                )
                assertTrue(
                    "${theme.id} $mode heading on page ${contrastRatio(tokens.heading, page)}",
                    contrastRatio(tokens.heading, page) >= 4.4f,
                )
                assertTrue(
                    "${theme.id} $mode onAccent ${contrastRatio(tokens.onAccent, tokens.accent)}",
                    contrastRatio(tokens.onAccent, tokens.accent) >= 4.4f,
                )
            }
        }
    }

    @Test
    fun defaultStartupWindowKeepsExistingSplashColors() {
        assertEquals(0xFF080B12.toInt(), echoStartupWindowColor(EchoColorTheme.Echo, dark = true))
        assertEquals(0xFFF1F1F3.toInt(), echoStartupWindowColor(EchoColorTheme.Echo, dark = false))
    }

    @Test
    fun echoDarkSchemeDoesNotUseMaterialBaselinePurple() {
        val scheme = echoColorScheme(echoThemeTokens(EchoColorTheme.Echo, dark = true))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3A2C32), scheme.primaryContainer)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFE4C4CC), scheme.onPrimaryContainer)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF202126), scheme.surfaceContainer)
        assertTrue(scheme.primaryContainer != MaterialDarkPrimaryContainer)
        assertTrue(scheme.secondaryContainer != MaterialDarkSecondaryContainer)
        assertTrue(scheme.surfaceContainer != MaterialDarkSurfaceContainer)
    }

    @Test
    fun everyPaletteSchemeFillsContainersWithoutMaterialPurple() {
        for (theme in EchoColorTheme.entries) {
            for (dark in listOf(true, false)) {
                val tokens = echoThemeTokens(theme, dark)
                val scheme = echoColorScheme(tokens)
                val mode = if (dark) "dark" else "light"
                assertTrue(
                    "${theme.id} $mode primaryContainer ${scheme.primaryContainer}",
                    scheme.primaryContainer != MaterialDarkPrimaryContainer,
                )
                assertTrue(
                    "${theme.id} $mode surfaceContainer ${scheme.surfaceContainer}",
                    scheme.surfaceContainer != MaterialDarkSurfaceContainer,
                )
                assertTrue(
                    "${theme.id} $mode onPrimaryContainer ${contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer)}",
                    contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.4f,
                )
            }
        }
    }

    companion object {
        // androidx.compose.material3 darkColorScheme() unspecified roles.
        private val MaterialDarkPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF4F378B)
        private val MaterialDarkSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF4A4458)
        private val MaterialDarkSurfaceContainer = androidx.compose.ui.graphics.Color(0xFF211F26)
    }
}

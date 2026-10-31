package app.echo.android.design

import app.echo.android.model.settings.EchoColorTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
        assertEquals(Color(0xFF925568), lightScheme.primary)
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
    fun defaultStartupWindowMatchesLightPageAndPreservesDarkSplash() {
        assertEquals(0xFF080B12.toInt(), echoStartupWindowColor(EchoColorTheme.Echo, dark = true))
        assertEquals(0xFFF5F5F3.toInt(), echoStartupWindowColor(EchoColorTheme.Echo, dark = false))
    }

    @Test
    fun echoLightKeepsTheNeutralPaper() {
        val tokens = echoThemeTokens(EchoColorTheme.Echo, dark = false)
        val scheme = echoColorScheme(tokens)
        assertEquals(Color(0xFFF5F5F3), scheme.background)
        assertEquals(Color(0xFFFAFAF8), scheme.surface)
        assertEquals(0xFFF5F5F3.toInt(), echoStartupWindowColor(EchoColorTheme.Echo, false))
    }

    @Test
    fun otherLightPresetsWashThePageWithTheirOwnHue() {
        val paper = Color(0xFFF5F5F3)
        for (theme in EchoColorTheme.entries.filter { it != EchoColorTheme.Echo && it != EchoColorTheme.Custom }) {
            val tokens = echoThemeTokens(theme, dark = false)
            val scheme = echoColorScheme(tokens)
            assertTrue("${theme.id} page is not the shared paper", tokens.bgTop != paper)
            assertEquals("${theme.id} scheme follows page", tokens.bgTop, scheme.background)
            assertEquals("${theme.id} startup matches page", tokens.bgTop.toArgb(), echoStartupWindowColor(theme, false))
            assertTrue("${theme.id} stays a light page", relativeLuminance(tokens.bgBottom) > 0.7f)
            assertTrue("${theme.id} cards lift off the page", relativeLuminance(tokens.panel) > relativeLuminance(tokens.bgTop))
            assertTrue("${theme.id} keeps accent", tokens.accent == scheme.primary)
            for (page in listOf(tokens.bgTop, tokens.bgBottom, tokens.panel)) {
                assertTrue("${theme.id} body on page ${contrastRatio(tokens.onSurface, page)}", contrastRatio(tokens.onSurface, page) >= 4.5f)
                assertTrue("${theme.id} secondary text on page ${contrastRatio(tokens.muted, page)}", contrastRatio(tokens.muted, page) >= 4.5f)
            }
        }
    }

    @Test
    fun presetThemesLookDistinctFromEachOther() {
        for (dark in listOf(true, false)) {
            val pages = EchoColorTheme.entries.filter { it != EchoColorTheme.Custom }
                .map { echoThemeTokens(it, dark).bgMid.toArgb() }
            assertEquals("dark=$dark every theme has its own page tone", pages.size, pages.toSet().size)
        }
    }

    @Test
    fun echoDarkSchemeDoesNotUseMaterialBaselinePurple() {
        val scheme = echoColorScheme(echoThemeTokens(EchoColorTheme.Echo, dark = true))
        assertEquals(Color(0xFF3A2C32), scheme.primaryContainer)
        assertEquals(Color(0xFFE4C4CC), scheme.onPrimaryContainer)
        assertEquals(Color(0xFF202126), scheme.surfaceContainer)
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
        private val MaterialDarkPrimaryContainer = Color(0xFF4F378B)
        private val MaterialDarkSecondaryContainer = Color(0xFF4A4458)
        private val MaterialDarkSurfaceContainer = Color(0xFF211F26)
    }
}

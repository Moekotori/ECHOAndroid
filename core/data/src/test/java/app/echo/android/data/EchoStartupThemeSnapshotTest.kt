package app.echo.android.data

import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.model.settings.EchoCustomColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoStartupThemeSnapshotTest {
    @Test
    fun themeSnapshotDoesNotCarryRemotePlaybackCredentials() {
        val settings = EchoStartupThemeSnapshot().toAppSettings()
        assertNull(settings.webDavServerUrl)
        assertNull(settings.webDavUsername)
        assertNull(settings.webDavPassword)
        assertNull(settings.subsonicServerUrl)
        assertNull(settings.subsonicUsername)
        assertNull(settings.subsonicPassword)
    }

    @Test
    fun defaultThemeModeIsLight() {
        assertEquals(EchoThemeMode.Light, EchoStartupThemeSnapshot().themeMode)
        assertEquals(EchoThemeMode.Light, EchoAppSettings().themeMode)
        assertEquals(EchoColorTheme.Default.id, EchoStartupThemeSnapshot().colorTheme)
        assertEquals(EchoColorTheme.Default.id, EchoAppSettings().colorTheme)
        assertEquals(EchoCustomColors.Default, EchoAppSettings().customColors)
        assertNull(EchoStartupThemeSnapshot().startupBackgroundUri)
        assertEquals("mist", EchoStartupThemeSnapshot().toAppSettings().lyricsPageStyle)
        assertEquals("mist", EchoStartupThemeSnapshot(themeMode = EchoThemeMode.Dark).toAppSettings().lyricsPageStyle)
    }

    @Test
    fun legacyPalettesFallBackToFixedColorsWithoutChangingDarkMode() {
        val colors = EchoCustomColors(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        for (theme in EchoColorTheme.entries) {
            val snapshot = EchoAppSettings(
                themeMode = EchoThemeMode.Dark,
                colorTheme = theme.id,
                customColors = colors,
                dynamicColorEnabled = true,
            ).toStartupThemeSnapshot()
            assertEquals(EchoColorTheme.Default.id, snapshot.colorTheme)
            assertEquals(EchoCustomColors.Default, snapshot.customColors)
            // Also cover a directly constructed legacy launch snapshot.
            val restored = snapshot.copy(colorTheme = theme.id, customColors = colors).toAppSettings()
            assertEquals(EchoThemeMode.Dark, restored.themeMode)
            assertEquals(EchoColorTheme.Default.id, restored.colorTheme)
            assertEquals(EchoCustomColors.Default, restored.customColors)
            assertEquals(false, restored.dynamicColorEnabled)
            assertEquals(emptyList<app.echo.android.model.settings.EchoSavedColorTheme>(), restored.savedColorThemes)
        }
    }

    @Test
    fun startupBackgroundRemainsIndependentOfAppBackground() {
        val settings = EchoAppSettings(
            startupBackgroundUri = "content://images/startup",
            customBackgroundUri = "content://images/app",
        )
        val restored = settings.toStartupThemeSnapshot().toAppSettings()
        assertEquals("content://images/startup", restored.startupBackgroundUri)
        assertNull(restored.customBackgroundUri)
    }

    @Test
    fun normalizeThemeModeKeepsExplicitChoices() {
        assertEquals(EchoThemeMode.Light, normalizeThemeMode(EchoThemeMode.Light))
        assertEquals(EchoThemeMode.Dark, normalizeThemeMode(EchoThemeMode.Dark))
        assertEquals(EchoThemeMode.System, normalizeThemeMode(EchoThemeMode.System))
    }

    @Test
    fun normalizeThemeModeFallsBackToLight() {
        assertEquals(EchoThemeMode.Light, normalizeThemeMode(null))
        assertEquals(EchoThemeMode.Light, normalizeThemeMode(""))
        assertEquals(EchoThemeMode.Light, normalizeThemeMode("auto"))
    }
}

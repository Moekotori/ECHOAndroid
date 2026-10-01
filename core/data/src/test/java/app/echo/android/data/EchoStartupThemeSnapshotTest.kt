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
        assertEquals("paper", EchoStartupThemeSnapshot().toAppSettings().lyricsPageStyle)
        assertEquals("mist", EchoStartupThemeSnapshot(themeMode = EchoThemeMode.Dark).toAppSettings().lyricsPageStyle)
    }

    @Test
    fun customColorsSurviveStartupSnapshotButSavedThemesDoNot() {
        val colors = EchoCustomColors(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        val restored = EchoAppSettings(
            colorTheme = EchoColorTheme.Custom.id,
            customColors = colors,
        ).toStartupThemeSnapshot().toAppSettings()
        assertEquals(EchoColorTheme.Custom.id, restored.colorTheme)
        assertEquals(colors, restored.customColors)
        assertEquals(emptyList<app.echo.android.model.settings.EchoSavedColorTheme>(), restored.savedColorThemes)
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

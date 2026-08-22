package app.echo.android.data

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
    fun defaultThemeModeIsDark() {
        assertEquals(EchoThemeMode.Dark, EchoStartupThemeSnapshot().themeMode)
        assertEquals(EchoThemeMode.Dark, EchoAppSettings().themeMode)
    }

    @Test
    fun normalizeThemeModeKeepsExplicitChoices() {
        assertEquals(EchoThemeMode.Light, normalizeThemeMode(EchoThemeMode.Light))
        assertEquals(EchoThemeMode.Dark, normalizeThemeMode(EchoThemeMode.Dark))
        assertEquals(EchoThemeMode.System, normalizeThemeMode(EchoThemeMode.System))
    }

    @Test
    fun normalizeThemeModeFallsBackToDark() {
        assertEquals(EchoThemeMode.Dark, normalizeThemeMode(null))
        assertEquals(EchoThemeMode.Dark, normalizeThemeMode(""))
        assertEquals(EchoThemeMode.Dark, normalizeThemeMode("auto"))
    }
}

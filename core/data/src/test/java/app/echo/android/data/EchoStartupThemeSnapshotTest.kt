package app.echo.android.data

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
}

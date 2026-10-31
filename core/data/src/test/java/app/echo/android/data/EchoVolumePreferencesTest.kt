package app.echo.android.data

import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.playback.EchoVolumeSettings
import org.junit.Assert.*
import org.junit.Test

class EchoVolumePreferencesTest {
    @Test fun settingsAndPortableBackupRetainVolumeAndMute() {
        val volume = EchoVolumeSettings(-18.5f, 3f, -6f, 0.1f, true)
        assertEquals(volume, EchoVolumePreferences.decode(EchoVolumePreferences.encode(volume)))
        val document = EchoBackupDocument(settings = EchoAppSettings(volume = volume).toBackupSettings())
        assertEquals(volume, EchoBackupCodec.decode(EchoBackupCodec.encode(document)).settings.volume)
    }

    @Test fun missingAndCorruptSettingsUseNeutralDefaults() {
        for (value in listOf(null, "invalid", "{}", "x".repeat(1025))) {
            assertEquals(EchoVolumeSettings(), EchoVolumePreferences.decode(value))
        }
        val restored = EchoVolumePreferences.decode("""{"levelDb":-100,"gainDb":100,"limitDb":100,"stepDb":9}""")
        assertEquals(EchoVolumeSettings(-60f, 12f, 30f), restored)
    }
}

package app.echo.android.data

import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.playback.EchoVolumeSettings
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class EchoVolumePreferencesTest {
    @Test fun restoreAtomicallyRejectsDelayedWritesFromBeforeTheRestore() {
        val prefs = mutablePreferencesOf()
        val initial = EchoVolumeSettings(levelDb = -10f)
        assertTrue(EchoVolumePreferences.write(prefs, initial, 0L))
        val before = EchoVolumePreferences.snapshot(prefs)
        val restored = EchoVolumeSettings(levelDb = -30f)
        EchoVolumePreferences.restore(prefs, restored)
        val after = EchoVolumePreferences.snapshot(prefs)
        assertEquals(restored, after.volume)
        assertNotEquals(before.restoreVersion, after.restoreVersion)
        assertFalse(EchoVolumePreferences.write(prefs, initial.adjusted(-1), before.restoreVersion))
        assertEquals(after, EchoVolumePreferences.snapshot(prefs))
        assertTrue(EchoVolumePreferences.write(prefs, restored.adjusted(-1), after.restoreVersion))
        assertEquals(restored.adjusted(-1), EchoVolumePreferences.read(prefs))
        EchoVolumePreferences.restore(prefs, restored.adjusted(-1))
        assertNotEquals(after.restoreVersion, EchoVolumePreferences.snapshot(prefs).restoreVersion)
    }

    @Test fun settingsAndPortableBackupRetainVolumeAndMute() {
        val volume = EchoVolumeSettings(-18.5f, 3f, -6f, 0.1f, true, rememberPerDevice = true).forDevice("speaker")
            .forDevice("usb:1:2").copy(levelDb = -25f, gainDb = -3f).rememberDevice()
        assertEquals(volume, EchoVolumePreferences.decode(EchoVolumePreferences.encode(volume)))
        val document = EchoBackupDocument(settings = EchoAppSettings(volume = volume).toBackupSettings())
        assertEquals(volume, EchoBackupCodec.decode(EchoBackupCodec.encode(document)).settings.volume)
        assertEquals(3f, EchoVolumePreferences.decode(EchoVolumePreferences.encode(volume)).forDevice("speaker").gainDb, 0f)
        val shared = volume.copy(rememberPerDevice = false).normalized
        val sharedBackup = EchoBackupDocument(settings = EchoAppSettings(volume = shared).toBackupSettings())
        assertEquals(shared, EchoBackupCodec.decode(EchoBackupCodec.encode(sharedBackup)).settings.volume)
    }

    @Test fun missingAndCorruptSettingsUseNeutralDefaults() {
        for (value in listOf(null, "invalid", "{}", "x".repeat(1025))) {
            assertEquals(EchoVolumeSettings(), EchoVolumePreferences.decode(value))
        }
        val restored = EchoVolumePreferences.decode("""{"levelDb":-100,"gainDb":100,"limitDb":100,"stepDb":9}""")
        assertEquals(EchoVolumeSettings(-60f, 12f, 30f), restored)
    }

    @Test fun legacySettingsAndInvalidDeviceRecordsAreNormalized() {
        val legacy = EchoVolumePreferences.decode("""{"levelDb":-12,"gainDb":4,"stepDb":1}""")
        assertFalse(legacy.rememberPerDevice)
        assertEquals(legacy, legacy.forDevice("speaker").forDevice("usb:1:2"))
        val speaker = legacy.copy(rememberPerDevice = true).forDevice("speaker")
        assertEquals(4f, speaker.gainDb, 0f)
        assertEquals(4f, speaker.forDevice("bluetooth:name:New").gainDb, 0f)
        val json = """{"rememberPerDevice":true,"deviceKey":"bad","devices":{"bad":{"gainDb":20},"speaker":{"gainDb":999,"levelDb":-999}}}"""
        val decoded = EchoVolumePreferences.decode(json)
        assertNull(decoded.deviceKey)
        assertEquals(1, decoded.deviceLevels.size)
        assertEquals(-60f, decoded.forDevice("speaker").levelDb, 0f)
        assertEquals(12f, decoded.forDevice("speaker").gainDb, 0f)
    }
}

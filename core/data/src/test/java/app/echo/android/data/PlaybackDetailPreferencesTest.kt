package app.echo.android.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.playback.EchoTrackTransitionOptions
import app.echo.android.model.settings.EchoLibraryLayout
import org.junit.Assert.*
import org.junit.Test

class PlaybackDetailPreferencesTest {
    @Test fun taskRemovalChoiceSurvivesBackupAndOldBackupsKeepDefault() {
        val settings = EchoAppSettings(stopOnTaskRemoved = true).toBackupSettings()
        val decoded = EchoBackupCodec.decode(EchoBackupCodec.encode(EchoBackupDocument(settings = settings))).settings
        assertEquals(true, decoded.stopOnTaskRemoved)
        assertNull(EchoBackupCodec.decode("{\"version\":1,\"settings\":{}}").settings.stopOnTaskRemoved)
        assertFalse(EchoAppSettings().stopOnTaskRemoved)
    }

    @Test fun newDisplayAndFadeChoicesSurvivePreferencesAndBackup() {
        val layout = EchoLibraryLayout(showFileNames = true)
        val preferences = mutablePreferencesOf()
        LibraryLayoutPreferences.write(preferences, layout)
        assertTrue(LibraryLayoutPreferences.read(preferences).showFileNames)
        val settings = EchoAppSettings(libraryLayout = layout, trackTransitions = EchoTrackTransitionOptions(playPauseFadeEnabled = false)).toBackupSettings()
        val decoded = EchoBackupCodec.decode(EchoBackupCodec.encode(EchoBackupDocument(settings = settings))).settings!!
        assertTrue(decoded.libraryLayout!!.showFileNames)
        assertFalse(decoded.trackTransitions!!.playPauseFadeEnabled)
    }
}

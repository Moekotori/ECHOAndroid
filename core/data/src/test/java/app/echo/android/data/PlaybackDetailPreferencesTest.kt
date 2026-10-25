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
        val layout = EchoLibraryLayout(showFileNames = true, showPlayNext = true)
        val preferences = mutablePreferencesOf()
        LibraryLayoutPreferences.write(preferences, layout)
        assertTrue(LibraryLayoutPreferences.read(preferences).showFileNames)
        assertTrue(LibraryLayoutPreferences.read(preferences).showPlayNext)
        assertFalse(LibraryLayoutPreferences.read(mutablePreferencesOf()).showPlayNext)
        LibraryLayoutPreferences.write(preferences, layout.copy(showPlayNext = false))
        assertFalse(LibraryLayoutPreferences.read(preferences).showPlayNext)
        val oldLayout = BackupExperienceCodec.library(org.json.JSONObject("{\"libraryLayout\":{\"showFileNames\":true}}"))!!
        assertFalse(oldLayout.showPlayNext)
        val settings = EchoAppSettings(libraryLayout = layout, trackTransitions = EchoTrackTransitionOptions(playPauseFadeEnabled = false)).toBackupSettings()
        val decoded = EchoBackupCodec.decode(EchoBackupCodec.encode(EchoBackupDocument(settings = settings))).settings!!
        assertTrue(decoded.libraryLayout!!.showFileNames)
        assertTrue(decoded.libraryLayout!!.showPlayNext)
        assertFalse(decoded.trackTransitions!!.playPauseFadeEnabled)
    }
}

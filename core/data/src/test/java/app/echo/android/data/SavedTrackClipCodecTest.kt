package app.echo.android.data

import app.echo.android.model.library.EchoSavedTrackClip
import app.echo.android.model.settings.*
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class SavedTrackClipCodecTest {
    @Test fun rangeAndUnicodeNameSurviveSerializationAndKeepTrackIdentity() {
        val clip = EchoSavedTrackClip("clip", "cue:track-2", "副歌", 2_000, 5_000)
        assertEquals(listOf(clip), SavedTrackClipCodec.decode(SavedTrackClipCodec.encode(listOf(clip))))
        assertTrue(clip.isPlayable(10_000)); assertFalse(clip.isPlayable(4_000)); assertFalse(clip.isPlayable(0))
        assertFalse(clip.copy(endMs = 2_100).isPlayable(10_000))
    }
    @Test fun recordLimitRefusesOverflowInsteadOfEvictingUserClips() {
        val clip = EchoSavedTrackClip("clip", "one", "part", 0, 1_000)
        try { SavedTrackClipCodec.encode(List(501) { clip.copy(id = "$it") }); fail("overflow accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun hiddenMenuActionsSurvivePreferencesAndUnknownIdsAreIgnored() {
        val preferences = mutablePreferencesOf()
        val layout = EchoLibraryLayout(hiddenTrackActions = setOf(EchoTrackMenuAction.Delete, EchoTrackMenuAction.EditTags))
        LibraryLayoutPreferences.write(preferences, layout)
        assertEquals(layout.hiddenTrackActions, LibraryLayoutPreferences.read(preferences).hiddenTrackActions)
        val backup = app.echo.android.model.backup.EchoBackupDocument(settings = EchoAppSettings(libraryLayout = layout).toBackupSettings())
        assertEquals(layout.hiddenTrackActions, EchoBackupCodec.decode(EchoBackupCodec.encode(backup)).settings.libraryLayout!!.hiddenTrackActions)
        assertNull(EchoTrackMenuAction.fromId("future-action"))
    }
}

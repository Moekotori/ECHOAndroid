package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMetadataEditorStateTest {
    private val track = EchoTrack("track", "content://audio/1", "Title", "Artist")

    @Test
    fun invalidNumbersCannotBeSavedAsAccidentalClears() {
        val state = TrackMetadataEditorState().apply { title = "Title"; artist = "Artist" }
        for (invalid in listOf("0", "-1", "1/12", "999999999999", "abc")) {
            state.trackNumber = invalid
            assertFalse(invalid, state.canSave)
        }
        state.trackNumber = " 12 "
        state.discNumber = "2"
        state.year = "2026"
        assertTrue(state.canSave)
        assertEquals(12, state.toUpdate(track).trackNumber)
        state.year = "10000"
        assertFalse(state.canSave)
        state.year = ""
        state.discNumber = "0"
        assertFalse(state.canSave)
        state.discNumber = ""
        state.trackNumber = ""
        assertTrue(state.canSave)
        assertNull(state.toUpdate(track).trackNumber)
    }

    @Test
    fun artworkIsAnExplicitDraftAndDoesNotCarryStaleTrackArtwork() {
        val original = track.copy(artworkUri = "content://cover/old")
        val state = TrackMetadataEditorState()
        assertNull(state.toUpdate(original).artworkUri)
        state.editArtwork("content://cover/new")
        assertEquals("content://cover/new", state.toUpdate(original).artworkUri)
        assertEquals("content://cover/old", original.artworkUri)
        assertNull(TrackMetadataEditorState().toUpdate(original).artworkUri)
    }

    @Test
    fun clearingComposerSendsExplicitEmptyValue() {
        val state = TrackMetadataEditorState()
        state.composer = "Composer"
        state.composer = ""
        assertEquals("", state.toUpdate(track).composer)
    }

    @Test
    fun unrelatedSavePreservesLyricsBeforeAndAfterLoading() {
        val state = TrackMetadataEditorState()
        assertNull(state.toUpdate(track).lyrics)
        state.loadLyrics("original lyrics")
        state.title = "Changed title"
        assertNull(state.toUpdate(track).lyrics)
    }

    @Test
    fun explicitEditsIncludingClearingAreSentToWriter() {
        val state = TrackMetadataEditorState()
        state.loadLyrics("original lyrics")
        state.editLyrics("edited lyrics")
        assertEquals("edited lyrics", state.toUpdate(track).lyrics)
        state.editLyrics("")
        assertEquals("", state.toUpdate(track).lyrics)
    }

    @Test
    fun lateLoadCannotOverwriteAnEdit() {
        val state = TrackMetadataEditorState()
        state.editLyrics("edited lyrics")
        state.loadLyrics("old lyrics")
        assertEquals("edited lyrics", state.lyrics)
        assertEquals("edited lyrics", state.toUpdate(track).lyrics)
    }
}

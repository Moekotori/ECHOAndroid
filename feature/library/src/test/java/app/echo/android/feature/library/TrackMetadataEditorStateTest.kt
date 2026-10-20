package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMetadataEditorStateTest {
    private val track = EchoTrack("track", "content://audio/1", "Title", "Artist")

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

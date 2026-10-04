package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTrackDeletionPolicyTest {
    @Test
    fun originalDeletionRejectsRemoteAndSharedAudioSegments() {
        val local = track()
        assertTrue(LibraryTrackDeletionPolicy.canDeleteOriginal(local))
        assertTrue(LibraryTrackDeletionPolicy.canDeleteOriginal(local.copy(source = LibrarySource.Saf, uri = "content://documents/audio/1")))
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginal(local.copy(source = LibrarySource.WebDav)))
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginal(local.copy(id = CueSheetPolicy.cueTrackId("1", 1))))
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginal(local.copy(uri = CueSheetPolicy.contentUri(local.uri, 1))))
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginal(local.copy(clipStartMs = 1000)))
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginal(local.copy(clipEndMs = 1000)))
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginal(local.copy(uri = "https://example.com/audio.flac")))
    }

    @Test
    fun mixedAndOversizedSelectionsCannotDeleteOriginalFiles() {
        val local = track()
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginals(emptyList()))
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginals(listOf(local, local.copy(source = LibrarySource.EchoLink))))
        assertTrue(LibraryTrackDeletionPolicy.canDeleteOriginals(List(200) { local.copy(id = it.toString()) }))
        assertFalse(LibraryTrackDeletionPolicy.canDeleteOriginals(List(201) { local.copy(id = it.toString()) }))
    }

    private fun track() = EchoTrack("1", "content://media/external/audio/media/1", "Track", "Artist")
}

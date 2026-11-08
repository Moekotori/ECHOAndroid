package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTrackDeletionPolicyTest {
    @Test
    fun legacyPermissionAppliesOnlyToSharedFilesOnAndroid8And9() {
        val media = track()
        val document = media.copy(source = LibrarySource.Saf, uri = "content://documents/audio/1")
        val file = document.copy(uri = "file:///storage/emulated/0/Music/track.mp3")
        listOf(26, 27, 28).forEach { sdk ->
            assertTrue(LibraryTrackDeletionPolicy.requiresLegacyStoragePermission(sdk, listOf(media)))
            assertTrue(LibraryTrackDeletionPolicy.requiresLegacyStoragePermission(sdk, listOf(file)))
            assertFalse(LibraryTrackDeletionPolicy.requiresLegacyStoragePermission(sdk, listOf(document)))
            assertFalse(LibraryTrackDeletionPolicy.requiresLegacyStoragePermission(sdk, emptyList()))
        }
        listOf(29, 30, 31, 33, 37).forEach { sdk ->
            assertFalse(LibraryTrackDeletionPolicy.requiresLegacyStoragePermission(sdk, listOf(media, document, file)))
        }
    }

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

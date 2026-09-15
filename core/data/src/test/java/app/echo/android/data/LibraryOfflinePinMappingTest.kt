package app.echo.android.data

import app.echo.android.model.library.LibraryOfflineFileStatus
import app.echo.android.model.library.LibraryOfflinePinKind
import app.echo.android.model.library.LibraryOfflinePinStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOfflinePinMappingTest {
    @Test
    fun pinStatusFollowsFileRows() {
        val pin = LibraryOfflinePinEntity(
            id = "album:remote||jellyfin||a",
            kind = LibraryOfflinePinKind.Album.id,
            source = "jellyfin",
            title = "Album",
            trackCount = 3,
            createdAtEpochMs = 1L,
        )
        val files = listOf(
            file("t1", LibraryOfflineFileStatus.Ready, 10L),
            file("t2", LibraryOfflineFileStatus.Queued, 0L),
            file("t3", LibraryOfflineFileStatus.Failed, 0L),
        )
        val mapped = pin.toPin(files)
        assertEquals(LibraryOfflinePinStatus.Partial, mapped.status)
        assertEquals(1, mapped.readyCount)
        assertEquals(10L, mapped.bytes)
        assertEquals(3, mapped.trackCount)
    }

    @Test
    fun downloadingBeatsPartialUntilAllReady() {
        val pin = LibraryOfflinePinEntity(
            id = "playlist:p1",
            kind = LibraryOfflinePinKind.Playlist.id,
            source = "subsonic",
            title = "Mix",
            trackCount = 2,
            createdAtEpochMs = 1L,
        )
        val files = listOf(
            file("t1", LibraryOfflineFileStatus.Ready, 4L),
            file("t2", LibraryOfflineFileStatus.Downloading, 0L),
        )
        assertEquals(LibraryOfflinePinStatus.Downloading, pin.toPin(files).status)
    }

    private fun file(trackId: String, status: LibraryOfflineFileStatus, bytes: Long) =
        LibraryOfflineFileEntity(
            trackId = trackId,
            pinId = "pin",
            remoteUri = "https://example.invalid/$trackId",
            localPath = if (status == LibraryOfflineFileStatus.Ready) "/tmp/$trackId" else null,
            bytes = bytes,
            status = status.id,
        )
}

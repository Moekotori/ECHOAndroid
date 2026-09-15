package app.echo.android.model.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOfflinePolicyTest {
    @Test
    fun pinsRemoteSourcesOnly() {
        assertTrue(LibraryOfflinePolicy.canPinSource("subsonic"))
        assertTrue(LibraryOfflinePolicy.canPinSource("jellyfin"))
        assertTrue(LibraryOfflinePolicy.canPinSource("webdav"))
        assertTrue(LibraryOfflinePolicy.canPinSource("echo-link"))
        assertFalse(LibraryOfflinePolicy.canPinSource("mediastore"))
        assertFalse(LibraryOfflinePolicy.canPinSource("saf"))
        assertFalse(LibraryOfflinePolicy.canPinSource("radio"))
        assertFalse(LibraryOfflinePolicy.canPinSource("netease"))
    }

    @Test
    fun pinIdsAreStable() {
        assertEquals("album:remote||jellyfin||a", LibraryOfflinePolicy.albumPinId("remote||jellyfin||a"))
        assertEquals("playlist:p1", LibraryOfflinePolicy.playlistPinId("p1"))
        assertEquals("track:t1", LibraryOfflinePolicy.trackPinId("t1"))
    }

    @Test
    fun remoteAlbumKeysAreParsedForPinning() {
        assertEquals("jellyfin" to "a", LibraryOfflinePolicy.remoteAlbumParts("remote||jellyfin||a"))
        assertTrue(LibraryOfflinePolicy.canPinAlbumKey("remote||subsonic||album"))
        assertTrue(LibraryOfflinePolicy.canPinAlbumKey("remote||echo-link||pc-album"))
        assertFalse(LibraryOfflinePolicy.canPinAlbumKey("local-album"))
        assertFalse(LibraryOfflinePolicy.canPinAlbumKey("remote||mediastore||x"))
        assertFalse(LibraryOfflinePolicy.canPinAlbumKey("remote||jellyfin"))
        assertNull(LibraryOfflinePolicy.remoteAlbumParts("remote||jellyfin||"))
    }

    @Test
    fun offlineFileNamesAreStableHex() {
        val first = LibraryOfflinePolicy.fileNameForTrack("track-1")
        val second = LibraryOfflinePolicy.fileNameForTrack("track-1")
        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertTrue(LibraryOfflinePolicy.fileNameForTrack("track-2") != first)
    }

    @Test
    fun statusCoversReadyPartialAndFailed() {
        assertEquals(LibraryOfflinePinStatus.Ready, LibraryOfflinePolicy.status(4, 4, 0, false))
        assertEquals(LibraryOfflinePinStatus.Downloading, LibraryOfflinePolicy.status(4, 1, 0, true))
        assertEquals(LibraryOfflinePinStatus.Partial, LibraryOfflinePolicy.status(4, 2, 1, false))
        assertEquals(LibraryOfflinePinStatus.Failed, LibraryOfflinePolicy.status(4, 0, 4, false))
        assertEquals(LibraryOfflinePinStatus.Queued, LibraryOfflinePolicy.status(4, 0, 0, false))
    }

    @Test
    fun quotaRejectsOversizeAndOverflow() {
        assertTrue(LibraryOfflinePolicy.quotaAllows(0, 1024))
        assertFalse(LibraryOfflinePolicy.quotaAllows(0, LibraryOfflinePolicy.MaxFileBytes + 1))
        assertFalse(
            LibraryOfflinePolicy.quotaAllows(
                usedBytes = LibraryOfflinePolicy.DefaultQuotaBytes - 10,
                incomingBytes = 20,
            ),
        )
        assertEquals(10L, LibraryOfflinePolicy.remainingQuota(LibraryOfflinePolicy.DefaultQuotaBytes - 10))
    }
}

package app.echo.android.model.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

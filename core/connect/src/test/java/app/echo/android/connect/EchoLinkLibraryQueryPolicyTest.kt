package app.echo.android.connect

import app.echo.android.model.connect.EchoLinkLibraryQueryPolicy
import app.echo.android.model.connect.EchoRemoteTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkLibraryQueryPolicyTest {
    @Test
    fun nonEmptyQueriesAreSentToPc() {
        assertEquals("zw", EchoLinkLibraryQueryPolicy.remoteSearchQuery("zw"))
        assertEquals("ziwei", EchoLinkLibraryQueryPolicy.remoteSearchQuery("ziwei"))
        assertEquals("Radiohead", EchoLinkLibraryQueryPolicy.remoteSearchQuery(" Radiohead "))
        assertEquals("周杰伦", EchoLinkLibraryQueryPolicy.remoteSearchQuery("周杰伦"))
        assertEquals("", EchoLinkLibraryQueryPolicy.remoteSearchQuery("  "))
    }

    @Test
    fun remoteAlbumKeysRoundTrip() {
        assertEquals("echo-link-album:a1", EchoLinkLibraryQueryPolicy.remoteAlbumKey("a1"))
        assertEquals("a1", EchoLinkLibraryQueryPolicy.remoteAlbumId("echo-link-album:a1"))
        assertEquals(null, EchoLinkLibraryQueryPolicy.remoteAlbumId("echo-link:local"))
        assertEquals("", EchoLinkLibraryQueryPolicy.parentFolderPath("Jazz"))
        assertEquals("Music", EchoLinkLibraryQueryPolicy.parentFolderPath("Music/Jazz"))
        assertEquals(listOf("a", "b"), EchoLinkLibraryQueryPolicy.playableLinkedPcTrackIds(
            listOf(
                EchoRemoteTrack("a", "A", "Artist", null, null, 1_000),
                EchoRemoteTrack("  ", "B", "Artist", null, null, 1_000),
                EchoRemoteTrack("b", "C", "Artist", null, null, 1_000),
            ),
        ))
        assertEquals("b", EchoLinkLibraryQueryPolicy.queueReplaceStartId(listOf("a", "b"), "b"))
        assertEquals("a", EchoLinkLibraryQueryPolicy.queueReplaceStartId(listOf("a", "b"), "missing"))
    }

    @Test
    fun incompletePlaylistPreviewIsFetched() {
        assertTrue(EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(knownTrackCount = 1, declaredTrackCount = 12))
        assertFalse(EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(knownTrackCount = 12, declaredTrackCount = 12))
        assertTrue(EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(knownTrackCount = 0, declaredTrackCount = 0))
        assertFalse(EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(knownTrackCount = 3, declaredTrackCount = 0))
    }

    @Test
    fun emptyLatinRemotePageKeepsPreviousTracks() {
        assertTrue(
            EchoLinkLibraryQueryPolicy.shouldKeepPreviousTracksOnEmptyRemotePage(
                query = "radiohead",
                remoteTrackCount = 0,
                previousTrackCount = 12,
            ),
        )
        assertFalse(
            EchoLinkLibraryQueryPolicy.shouldKeepPreviousTracksOnEmptyRemotePage(
                query = "周杰伦",
                remoteTrackCount = 0,
                previousTrackCount = 12,
            ),
        )
        assertFalse(
            EchoLinkLibraryQueryPolicy.shouldKeepPreviousTracksOnEmptyRemotePage(
                query = "radiohead",
                remoteTrackCount = 3,
                previousTrackCount = 12,
            ),
        )
        assertTrue(EchoLinkLibraryQueryPolicy.shouldKeepLoadedTracksForQuery("zw", previousTrackCount = 8))
        assertFalse(EchoLinkLibraryQueryPolicy.shouldKeepLoadedTracksForQuery("", previousTrackCount = 8))
    }

    @Test
    fun phoneQueueDropsUnplayableTracks() {
        val playable = EchoLinkLibraryQueryPolicy.playableLinkedPhoneTracks(
            listOf(
                EchoRemoteTrack("a", "A", "Artist", null, null, 1_000, canPlayOnPhone = true),
                EchoRemoteTrack(null, "B", "Artist", null, null, 1_000, canPlayOnPhone = true),
                EchoRemoteTrack("c", "C", "Artist", null, null, 1_000, canPlayOnPhone = false),
            ),
        )
        assertEquals(listOf("a"), playable.map { it.id })
    }
}

package app.echo.android.connect

import app.echo.android.model.connect.EchoLinkLibraryQueryPolicy
import app.echo.android.model.connect.EchoRemoteTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkLibraryQueryPolicyTest {
    @Test
    fun latinPinyinQueriesStayOnDevice() {
        assertEquals("", EchoLinkLibraryQueryPolicy.remoteSearchQuery("zw"))
        assertEquals("", EchoLinkLibraryQueryPolicy.remoteSearchQuery("ziwei"))
        assertEquals("周杰伦", EchoLinkLibraryQueryPolicy.remoteSearchQuery("周杰伦"))
        assertEquals("", EchoLinkLibraryQueryPolicy.remoteSearchQuery("  "))
    }

    @Test
    fun incompletePlaylistPreviewIsFetched() {
        assertTrue(EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(knownTrackCount = 1, declaredTrackCount = 12))
        assertFalse(EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(knownTrackCount = 12, declaredTrackCount = 12))
        assertTrue(EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(knownTrackCount = 0, declaredTrackCount = 0))
        assertFalse(EchoLinkLibraryQueryPolicy.shouldFetchPlaylistTracks(knownTrackCount = 3, declaredTrackCount = 0))
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

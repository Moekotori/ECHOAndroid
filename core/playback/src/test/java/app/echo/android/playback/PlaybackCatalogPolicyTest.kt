package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCatalogPolicyTest {
    @Test
    fun inAppTrackWithUriIsNotExpandedToAlbum() {
        assertFalse(
            PlaybackCatalogPolicy.shouldExpandCatalogQueue(
                mediaId = "mediastore:42",
                hasPlayUri = true,
            ),
        )
        assertTrue(
            PlaybackCatalogPolicy.shouldExpandCatalogQueue(
                mediaId = "mediastore:42",
                hasPlayUri = false,
            ),
        )
    }

    @Test
    fun browseCollectionsStillExpand() {
        assertTrue(
            PlaybackCatalogPolicy.shouldExpandCatalogQueue(
                mediaId = EchoPlaybackLibraryIds.album("kind-of-blue"),
                hasPlayUri = false,
            ),
        )
        assertTrue(
            PlaybackCatalogPolicy.shouldExpandCatalogQueue(
                mediaId = EchoPlaybackLibraryIds.playlist("p1"),
                hasPlayUri = true,
            ),
        )
        assertTrue(
            PlaybackCatalogPolicy.shouldExpandCatalogQueue(
                mediaId = EchoPlaybackLibraryIds.FAVORITES,
                hasPlayUri = false,
            ),
        )
    }

    @Test
    fun multiItemStartIndexFollowsRequestedMediaId() {
        assertEquals(
            1,
            PlaybackCatalogPolicy.resolvedStartIndex(
                requestedIds = listOf("a", "b", "c"),
                resolvedIds = listOf("a", "b", "c"),
                startIndex = 1,
            ),
        )
        assertEquals(
            2,
            PlaybackCatalogPolicy.resolvedStartIndex(
                requestedIds = listOf("a", "b", "c"),
                resolvedIds = listOf("x", "a", "b", "c"),
                startIndex = 1,
            ),
        )
        assertEquals(
            3,
            PlaybackCatalogPolicy.resolvedStartIndex(
                requestedIds = listOf("album-only"),
                resolvedIds = listOf("t1", "t2", "t3", "album-only"),
                startIndex = 0,
            ),
        )
    }
}

package app.echo.android.playback

import app.echo.android.model.playback.EchoLinkPlaybackUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkPlaybackUriTest {
    @Test
    fun persistUsesStableIdInsteadOfOneShotStreamUrl() {
        val trackId = "pc-track-42"
        val mediaId = EchoLinkPlaybackUri.mediaId(trackId)
        val oneShot = "http://192.168.1.20:26789/echo-link/media/token"
        val persist = EchoLinkPlaybackUri.persistableUri(mediaId, oneShot)

        assertEquals("echo-link://track/pc-track-42", persist)
        assertTrue(EchoLinkPlaybackUri.isOneShotStreamUri(oneShot))
        assertFalse(EchoLinkPlaybackUri.isOneShotStreamUri(persist))
        assertTrue(EchoLinkPlaybackUri.requiresStreamResolve(mediaId, persist))
        assertTrue(EchoLinkPlaybackUri.requiresStreamResolve(mediaId, oneShot))
        assertEquals(trackId, EchoLinkPlaybackUri.trackId(mediaId, oneShot))
        assertFalse(
            EchoLinkPlaybackUri.requiresStreamResolve(
                mediaId = "mediastore:1",
                uri = "content://media/external/audio/media/1",
            ),
        )
    }

    @Test
    fun liveOneShotStreamIsNotResolvedWhilePlayerIsAvailable() {
        val oneShot = "http://192.168.1.20:26789/echo-link/media/token"
        val persist = "echo-link://track/pc-track-42"
        assertFalse(EchoLinkPlaybackUri.playUriNeedsResolve(oneShot, playerUnavailable = false))
        assertTrue(EchoLinkPlaybackUri.playUriNeedsResolve(oneShot, playerUnavailable = true))
        assertTrue(EchoLinkPlaybackUri.playUriNeedsResolve(persist, playerUnavailable = false))
        assertTrue(EchoLinkPlaybackUri.playUriNeedsResolve(persist, playerUnavailable = true))
        assertFalse(
            EchoLinkPlaybackUri.playUriNeedsResolve(
                "content://media/external/audio/media/1",
                playerUnavailable = true,
            ),
        )
    }

    @Test
    fun pcLibrarySourceUsesEchoLinkIdOrPersistUri() {
        assertTrue(
            EchoLinkPlaybackUri.isPcLibrarySource(
                sourceId = "echo-link",
                mediaId = "echo-link:pc-42",
                uri = "http://192.168.1.20:26789/echo-link/media/token",
            ),
        )
        assertTrue(
            EchoLinkPlaybackUri.isPcLibrarySource(
                sourceId = null,
                mediaId = "echo-link:pc-42",
                uri = "echo-link://track/pc-42",
            ),
        )
        assertFalse(
            EchoLinkPlaybackUri.isPcLibrarySource(
                sourceId = "mediastore",
                mediaId = "mediastore:1",
                uri = "content://media/external/audio/media/1",
            ),
        )
    }
}

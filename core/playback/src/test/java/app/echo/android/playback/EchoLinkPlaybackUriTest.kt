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
}

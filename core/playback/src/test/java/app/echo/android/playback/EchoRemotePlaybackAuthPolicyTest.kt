package app.echo.android.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoRemotePlaybackAuthPolicyTest {
    @Test
    fun webDavHttpsWithoutUserInfoRequiresRegisteredCredential() {
        assertTrue(webDavPlaybackUriRequiresCredential("https://dav.example/music/a.flac"))
        assertFalse(webDavPlaybackUriRequiresCredential("https://user:pass@dav.example/music/a.flac"))
        assertFalse(webDavPlaybackUriRequiresCredential("https://navidrome.example/rest/stream.view?id=1"))
        assertFalse(webDavPlaybackUriRequiresCredential("content://media/external/audio/media/1"))
        assertTrue(subsonicPlaybackUriRequiresCredential("https://navidrome.example/rest/stream.view?id=1"))
        assertTrue(subsonicPlaybackUriRequiresCredential("https://navidrome.example/music/rest/getCoverArt.view?id=c1"))
        assertFalse(subsonicPlaybackUriRequiresCredential("https://dav.example/music/a.flac"))
        assertFalse(subsonicPlaybackUriRequiresCredential("content://media/external/audio/media/1"))
    }

    @Test
    fun restorePlayWaitsUntilAMatchingSubsonicBaseUrlIsRegistered() {
        val queue = listOf("https://navidrome.example/rest/stream.view?id=s1")
        assertTrue(queueRequiresSubsonicAuth(queue))
        assertFalse(subsonicAuthReadyForQueue(queue, emptyList()))
        assertFalse(subsonicAuthReadyForQueue(queue, listOf("https://other.example")))
        assertTrue(subsonicAuthReadyForQueue(queue, listOf("https://navidrome.example")))
        assertFalse(
            shouldAllowRestoredPlayWhenReady(
                playWhenReady = true,
                queueRequiresWebDavAuth = false,
                webDavAuthReady = true,
                queueRequiresSubsonicAuth = true,
                subsonicAuthReady = false,
            ),
        )
        assertTrue(
            shouldAllowRestoredPlayWhenReady(
                playWhenReady = true,
                queueRequiresWebDavAuth = false,
                webDavAuthReady = true,
                queueRequiresSubsonicAuth = true,
                subsonicAuthReady = true,
            ),
        )
    }

    @Test
    fun restorePlayWaitsUntilAMatchingWebDavBaseUrlIsRegistered() {
        val queue = listOf("https://dav.example/music/a.flac", "https://dav.example/music/b.flac")
        assertTrue(queueRequiresWebDavAuth(queue))
        assertFalse(webDavAuthReadyForQueue(queue, emptyList()))
        assertFalse(webDavAuthReadyForQueue(queue, listOf("https://other.example/music")))
        assertTrue(webDavAuthReadyForQueue(queue, listOf("https://dav.example/music")))
        assertFalse(
            shouldAllowRestoredPlayWhenReady(
                playWhenReady = true,
                queueRequiresWebDavAuth = queueRequiresWebDavAuth(queue),
                webDavAuthReady = webDavAuthReadyForQueue(queue, emptyList()),
            ),
        )
        assertTrue(
            shouldAllowRestoredPlayWhenReady(
                playWhenReady = true,
                queueRequiresWebDavAuth = queueRequiresWebDavAuth(queue),
                webDavAuthReady = webDavAuthReadyForQueue(queue, listOf("https://dav.example/music")),
            ),
        )
    }
}

package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoRemotePlaybackCachePolicyTest {
    @Test
    fun webDavAccountsUseDifferentCacheNamespaces() {
        val alice = remotePlaybackCacheNamespace(credentialIdentity = "https://dav.example/music:alice")
        val bob = remotePlaybackCacheNamespace(credentialIdentity = "https://dav.example/music:bob")

        assertNotEquals(alice, bob)
    }

    @Test
    fun signedTokensUseDifferentCacheNamespacesWithoutExposingToken() {
        val first = remotePlaybackCacheNamespace(sensitiveQueryValues = listOf("token" to "secret-one"))
        val second = remotePlaybackCacheNamespace(sensitiveQueryValues = listOf("token" to "secret-two"))

        assertNotEquals(first, second)
        assertEquals(64, first.length)
        assertFalse(first.contains("secret"))
    }

    @Test
    fun publicRequestsShareAStableNamespace() {
        assertEquals(remotePlaybackCacheNamespace(), remotePlaybackCacheNamespace())
    }

    @Test
    fun bitPerfectBypassesEchoLinkOneShotCacheOnly() {
        val oneShot = "http://192.168.1.20:26789/echo-link/media/token"
        assertTrue(EchoRemotePlaybackCachePolicy.shouldBypassCache(oneShot, usbBitPerfectEnabled = true))
        assertFalse(EchoRemotePlaybackCachePolicy.shouldBypassCache(oneShot, usbBitPerfectEnabled = false))
        assertFalse(
            EchoRemotePlaybackCachePolicy.shouldBypassCache(
                "https://nas.example/rest/stream.view?id=1",
                usbBitPerfectEnabled = true,
            ),
        )
        assertFalse(
            EchoRemotePlaybackCachePolicy.shouldBypassCache(
                "echo-link://track/pc-42",
                usbBitPerfectEnabled = true,
            ),
        )
    }

    @Test
    fun persistEchoLinkUrisAreCacheableAndShareATrackKey() {
        assertTrue(EchoRemotePlaybackCachePolicy.isCacheablePlaybackUri("echo-link://track/pc-42"))
        assertTrue(EchoRemotePlaybackCachePolicy.isCacheablePlaybackUri("https://nas.example/rest/stream.view?id=1"))
        assertFalse(EchoRemotePlaybackCachePolicy.isCacheablePlaybackUri("content://media/external/audio/media/1"))
        assertEquals(
            "echo-link-track:pc-42",
            EchoRemotePlaybackCachePolicy.resourceKey("echo-link://track/pc-42", explicitKey = null),
        )
        assertEquals(
            EchoRemotePlaybackCachePolicy.resourceKey("echo-link://track/pc-42", null),
            EchoRemotePlaybackCachePolicy.resourceKey("http://192.168.1.20:26789/echo-link/media/token", "echo-link:pc-42"),
        )
    }
}

package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
}

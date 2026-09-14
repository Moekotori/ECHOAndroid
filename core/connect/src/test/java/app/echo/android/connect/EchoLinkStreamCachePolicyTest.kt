package app.echo.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkStreamCachePolicyTest {
    @Test
    fun missingOrZeroExpiryIsNotCached() {
        assertFalse(EchoLinkStreamCachePolicy.shouldCache(null))
        assertFalse(EchoLinkStreamCachePolicy.shouldCache(0L))
        assertFalse(EchoLinkStreamCachePolicy.isFresh(null, nowEpochMs = 1_000L))
        assertFalse(EchoLinkStreamCachePolicy.isFresh(0L, nowEpochMs = 1_000L))
    }

    @Test
    fun freshUntilSkewWindow() {
        val expiresAt = 100_000L
        assertTrue(
            EchoLinkStreamCachePolicy.isFresh(
                expiresAtEpochMs = expiresAt,
                nowEpochMs = expiresAt - EchoLinkStreamCachePolicy.ExpirySkewMs - 1L,
            ),
        )
        assertFalse(
            EchoLinkStreamCachePolicy.isFresh(
                expiresAtEpochMs = expiresAt,
                nowEpochMs = expiresAt - EchoLinkStreamCachePolicy.ExpirySkewMs,
            ),
        )
        assertFalse(
            EchoLinkStreamCachePolicy.isFresh(
                expiresAtEpochMs = expiresAt,
                nowEpochMs = expiresAt + 1L,
            ),
        )
    }

    @Test
    fun cacheKeyIncludesEndpointAndTrack() {
        assertEquals(
            "pc|token\ntrack-1",
            EchoLinkStreamCachePolicy.cacheKey("pc|token", "track-1"),
        )
        assertTrue(
            EchoLinkStreamCachePolicy.cacheKey("pc|a", "t") !=
                EchoLinkStreamCachePolicy.cacheKey("pc|b", "t"),
        )
    }
}

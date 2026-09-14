package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkStreamRefreshPolicyTest {
    @Test
    fun firstAttemptAlwaysRuns() {
        assertTrue(EchoLinkStreamRefreshPolicy.shouldAttempt(0, 0L, 0L))
    }

    @Test
    fun retriesAfterCooldownUntilTheCap() {
        assertFalse(
            EchoLinkStreamRefreshPolicy.shouldAttempt(
                previousAttempts = 1,
                lastAttemptElapsedMs = 1_000L,
                nowElapsedMs = 1_400L,
            ),
        )
        assertTrue(
            EchoLinkStreamRefreshPolicy.shouldAttempt(
                previousAttempts = 1,
                lastAttemptElapsedMs = 1_000L,
                nowElapsedMs = 2_600L,
            ),
        )
        assertFalse(
            EchoLinkStreamRefreshPolicy.shouldAttempt(
                previousAttempts = EchoLinkStreamRefreshPolicy.MaxAttempts,
                lastAttemptElapsedMs = 0L,
                nowElapsedMs = 60_000L,
            ),
        )
    }

    @Test
    fun prefetchIsOnlyTheNextEchoLinkItem() {
        assertEquals(8_000L, EchoLinkStreamRefreshPolicy.ResolveTimeoutMs)
        assertTrue(
            EchoLinkStreamRefreshPolicy.shouldPrefetchNext(
                nextMediaId = "echo-link:b",
                nextUri = "echo-link://track/b",
                currentMediaId = "echo-link:a",
            ),
        )
        assertFalse(
            EchoLinkStreamRefreshPolicy.shouldPrefetchNext(
                nextMediaId = "echo-link:a",
                nextUri = "echo-link://track/a",
                currentMediaId = "echo-link:a",
            ),
        )
        assertFalse(
            EchoLinkStreamRefreshPolicy.shouldPrefetchNext(
                nextMediaId = "mediastore:1",
                nextUri = "content://media/external/audio/media/1",
                currentMediaId = "echo-link:a",
            ),
        )
        assertFalse(
            EchoLinkStreamRefreshPolicy.shouldPrefetchNext(
                nextMediaId = null,
                nextUri = "echo-link://track/b",
                currentMediaId = "echo-link:a",
            ),
        )
    }
}

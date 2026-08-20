package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkRequestPolicyTest {
    @Test
    fun overlappingResolvesKeepOnlyTheLatestGeneration() {
        assertFalse(EchoLinkRequestPolicy.shouldApplyResolvedPlay(requestGeneration = 1L, latestGeneration = 2L))
        assertTrue(EchoLinkRequestPolicy.shouldApplyResolvedPlay(requestGeneration = 2L, latestGeneration = 2L))
        assertFalse(EchoLinkRequestPolicy.shouldApplyResolvedPlay(requestGeneration = 0L, latestGeneration = 0L))
    }

    @Test
    fun endpointIdentityIncludesToken() {
        val first = EchoRemoteEndpoint(
            id = "pc:26789",
            name = "PC",
            host = "192.168.1.8",
            port = 26789,
            token = "aaaaaaaaaaaaaaaa",
        )
        val rotated = first.copy(token = "bbbbbbbbbbbbbbbb")
        assertFalse(EchoLinkRequestPolicy.isSameEndpoint(first, rotated))
        assertTrue(EchoLinkRequestPolicy.isSameEndpoint(rotated, rotated.copy(id = "other")))
    }
}

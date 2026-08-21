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

    @Test
    fun onlyResolvedBearerEndpointsCanBePersisted() {
        val pairing = EchoRemoteEndpoint(
            id = "pc:26789",
            name = "PC",
            host = "192.168.1.8",
            port = 26789,
            token = "one-time-secret",
            protocolVersion = app.echo.android.model.connect.EchoProtocolVersion(2, 0),
            pairingId = "pair-1",
            pairingSecret = "one-time-secret",
        )
        assertFalse(EchoLinkRequestPolicy.shouldPersistEndpoint(pairing))
        assertTrue(
            EchoLinkRequestPolicy.shouldPersistEndpoint(
                pairing.copy(token = "access-token", pairingId = null, pairingSecret = null),
            ),
        )
    }

    @Test
    fun pairingStopsAfterTwoAttemptsAndClearsOneTimeSecret() {
        assertFalse(EchoLinkRequestPolicy.shouldFailPairingAfterAttempts(1))
        assertTrue(EchoLinkRequestPolicy.shouldFailPairingAfterAttempts(2))
        assertTrue(
            EchoLinkRequestPolicy.shouldClearPersistedPairingSecret(
                connectionFailed = true,
                needsV2PairExchange = true,
            ),
        )
        assertFalse(
            EchoLinkRequestPolicy.shouldClearPersistedPairingSecret(
                connectionFailed = true,
                needsV2PairExchange = false,
            ),
        )
    }
}

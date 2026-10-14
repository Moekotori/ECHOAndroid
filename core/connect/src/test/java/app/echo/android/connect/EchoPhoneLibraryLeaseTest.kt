package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EchoPhoneLibraryLeaseTest {
    private val target = EchoRemoteEndpoint("pc", "PC", "192.168.1.20", 26789, "paired-token")

    @Test
    fun reconnectKeepsMediaUrlsButDisconnectAuthorizationLossAndPcSwitchRevokeThem() {
        assertTrue(shouldKeepEchoPhoneLibrarySharing(target, target, EchoRemoteConnectionState.Reconnecting))
        assertTrue(shouldKeepEchoPhoneLibrarySharing(target.copy(name = "renamed"), target, EchoRemoteConnectionState.Connected))
        for (state in listOf(EchoRemoteConnectionState.Disconnected, EchoRemoteConnectionState.Error)) {
            assertFalse(shouldKeepEchoPhoneLibrarySharing(target, target, state))
        }
        for (changed in listOf(null, target.copy(host = "192.168.1.21"), target.copy(token = "new-token"))) {
            assertFalse(shouldKeepEchoPhoneLibrarySharing(changed, target, EchoRemoteConnectionState.Reconnecting))
        }
    }

    @Test
    fun temporaryNetworkAndServerFailuresRenewTheSameLease() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        renewEchoPhoneLibraryLease(register = {
            calls++
            if (calls == 1) throw SocketTimeoutException()
            if (calls == 2) throw EchoLinkHttpException("temporary", 503)
        }, waitBeforeRetry = { waits += it })
        assertEquals(3, calls)
        assertEquals(listOf(5_000L, 10_000L), waits)
    }

    @Test
    fun persistentNetworkFailureStopsAfterFourAttempts() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        try {
            renewEchoPhoneLibraryLease(register = { calls++; throw SocketTimeoutException() },
                waitBeforeRetry = { waits += it })
            fail("must surface exhausted renewal")
        } catch (_: SocketTimeoutException) {
            assertEquals(4, calls)
            assertEquals(listOf(5_000L, 10_000L, 20_000L), waits)
        }
    }

    @Test
    fun authorizationAndUnsupportedResponsesAreNeverRetried() = runBlocking {
        for (code in listOf(401, 403, 404, 405, 501)) {
            var calls = 0
            try {
                renewEchoPhoneLibraryLease(register = { calls++; throw EchoLinkHttpException("rejected", code) },
                    waitBeforeRetry = { fail("must not retry $code") })
                fail("must surface $code")
            } catch (error: EchoLinkHttpException) {
                assertEquals(code, error.statusCode)
                assertEquals(1, calls)
            }
        }
    }

    @Test
    fun stoppingSharingCancelsRenewalWithoutRetry() = runBlocking {
        try {
            renewEchoPhoneLibraryLease(register = { throw CancellationException("stopped") },
                waitBeforeRetry = { fail("must not retry cancellation") })
            fail("must propagate cancellation")
        } catch (_: CancellationException) { }
    }

    @Test
    fun establishedSharingSurvivesExhaustedRenewalRoundAndRecoversWithoutNewSession() = runBlocking {
        var calls = 0
        var renewed = 0
        val waits = mutableListOf<Long>()
        try {
            maintainEchoPhoneLibraryLease(register = {
                calls++
                if (calls in 2..5) throw SocketTimeoutException()
            }, onRenewed = {
                renewed++
                if (renewed == 2) throw CancellationException("user stopped")
            }, wait = { waits += it })
            fail("must propagate stop")
        } catch (_: CancellationException) {
            assertEquals(6, calls)
            assertEquals(2, renewed)
            assertEquals(listOf(60_000L, 5_000L, 10_000L, 20_000L, 30_000L), waits)
        }
    }

    @Test
    fun establishedSharingStillEndsImmediatelyWhenAuthorizationIsRevoked() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        try {
            maintainEchoPhoneLibraryLease(register = {
                calls++
                if (calls > 1) throw EchoLinkHttpException("revoked", 403)
            }, onRenewed = {}, wait = { waits += it })
            fail("must surface authorization rejection")
        } catch (error: EchoLinkHttpException) {
            assertEquals(403, error.statusCode)
            assertEquals(2, calls)
            assertEquals(listOf(60_000L), waits)
        }
    }
}

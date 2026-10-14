package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class EchoPhoneLibraryBackgroundTest {
    private val endpoint = EchoRemoteEndpoint("pc", "PC", "192.168.1.20", 26789, "paired-token", supportsV2Events = true)

    private suspend fun awaitState(check: () -> Boolean) = withTimeout(1_000L) {
        while (!check()) delay(1)
    }

    @Test
    fun activeShareKeepsBackgroundChecksWithoutEventsOrLibraryLoadsAndReleasesThemOnStop() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, backgroundStatusPollIntervalMs = 5)
        try {
            client.connect(endpoint, false)
            awaitState { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            client.setBackgroundConnectionRequired(true)
            client.setForeground(false)
            val subscriptions = transport.eventSubscriptions
            val calls = transport.statusCalls
            awaitState { transport.statusCalls > calls }
            assertEquals(subscriptions, transport.eventSubscriptions)
            assertEquals(0, transport.libraryTrackCalls)
            assertEquals(1, transport.maxConcurrentStatusCalls)
            client.setBackgroundConnectionRequired(false)
            val stopped = transport.statusCalls
            delay(20)
            assertEquals(stopped, transport.statusCalls)
        } finally { client.disconnect() }
    }

    @Test
    fun backgroundShareRecoversTransientNetworkFailureButStopsForRevokedAuthorization() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, backgroundStatusPollIntervalMs = 5)
        try {
            client.setForeground(false)
            client.setBackgroundConnectionRequired(true)
            client.connect(endpoint, false)
            awaitState { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            transport.statusError = IOException("temporary")
            awaitState { client.status.value.connectionState == EchoRemoteConnectionState.Reconnecting }
            transport.statusError = null
            awaitState { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            transport.statusError = EchoLinkHttpException("revoked", 401)
            awaitState { client.status.value.connectionState == EchoRemoteConnectionState.Error }
            val rejected = transport.statusCalls
            delay(20)
            assertEquals(rejected, transport.statusCalls)
            assertEquals(0, transport.eventSubscriptions)
        } finally { client.disconnect() }
    }
}

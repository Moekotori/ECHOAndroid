package app.echo.android

import app.echo.android.model.connect.EchoLinkPhonePresenceTrack
import app.echo.android.model.connect.EchoRemoteEndpoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoLinkPhonePresenceTest {
    @Test
    fun switchingPcClearsPreviousPresenceAfterTheInFlightWrite() = runBlocking {
        val first = EchoRemoteEndpoint("first", "First", "192.168.1.20", 26789, "first-token")
        val second = EchoRemoteEndpoint("second", "Second", "192.168.1.21", 26789, "second-token")
        val track = EchoLinkPhonePresenceTrack("Phone track", "Artist")
        val desired = MutableStateFlow<PhonePresencePublication?>(PhonePresencePublication(first, 1L, track))
        val writes = Channel<Pair<EchoRemoteEndpoint, EchoLinkPhonePresenceTrack?>>(Channel.UNLIMITED)
        val finishFirstWrite = CompletableDeferred<Unit>()
        val sender = launch {
            publishEchoLinkPhonePresence(desired) { endpoint, currentTrack ->
                writes.send(endpoint to currentTrack)
                if (endpoint == first && currentTrack != null) finishFirstWrite.await()
            }
        }
        try {
            withTimeout(2_000L) {
                assertEquals(first to track, writes.receive())
                desired.value = PhonePresencePublication(second, 2L, track)
                finishFirstWrite.complete(Unit)
                assertEquals(first to null, writes.receive())
                assertEquals(second to track, writes.receive())
                desired.value = null
                assertEquals(second to null, writes.receive())
            }
        } finally {
            sender.cancelAndJoin()
        }
    }
}

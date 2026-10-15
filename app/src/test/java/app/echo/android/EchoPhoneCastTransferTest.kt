package app.echo.android

import app.echo.android.connect.EchoLinkCastBodyFactory
import app.echo.android.connect.EchoLinkCastPlan
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.connect.EchoLinkCastPreparedQueue
import app.echo.android.connect.EchoLinkCastPublication
import app.echo.android.connect.EchoLinkCastServer
import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueContext
import app.echo.android.model.playback.PlaybackQueueState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class EchoPhoneCastTransferTest {
    private val sourceTrack = EchoLinkCastSourceTrack("song", "content://media/1", "Song", "Artist")
    private val plan = EchoLinkCastPlan.LocalHttp(listOf(sourceTrack), 0)

    private fun source(): EchoPhoneCastSource {
        val track = EchoTrackRef("song", "content://media/1", "Song", "Artist", queueContext = PlaybackQueueContext("one"))
        val second = track.copy(queueContext = PlaybackQueueContext("two"))
        return EchoPhoneCastSource(PlaybackQueueState(listOf(track, second), 0),
            EchoPlaybackStatus(track = track, isPlaying = true), 1000)
    }

    @Test
    fun readsLiveProgressAfterPreparationAndStateSurvivesAnotherSubscriber() = runBlocking {
        val host = FakeHost()
        var current = source()
        var paused = false
        val transfer = EchoPhoneCastTransfer(host, this, Dispatchers.Unconfined)
        transfer.start({ current }, { plan }, { paused = true })
        host.preparing.await()
        assertTrue(transfer.state.first().isBusy)
        assertEquals("http://127.0.0.1:1", transfer.state.first().targetAddress)
        current = current.copy(positionMs = 9000)
        host.preparationGate.complete(Unit)
        withTimeout(2000) { transfer.state.first { !it.isBusy } }
        assertEquals(9000L, host.sentPosition)
        assertEquals(listOf("send", "stop-old-renderer", "commit", "activate"), host.events)
        assertTrue(paused)
        assertNull(transfer.state.value.issue)
    }

    @Test
    fun switchingToAnotherOccurrenceOfTheSameSongCancelsBeforeSending() = runBlocking {
        val host = FakeHost()
        var current = source()
        val transfer = EchoPhoneCastTransfer(host, this, Dispatchers.Unconfined)
        transfer.start({ current }, { plan }, { fail("Must not pause changed playback") })
        host.preparing.await()
        current = current.copy(queue = current.queue.copy(currentIndex = 1),
            playback = current.playback.copy(track = current.queue.items[1]))
        host.preparationGate.complete(Unit)
        withTimeout(2000) { transfer.state.first { !it.isBusy } }
        assertEquals(listOf("rollback"), host.events)
        assertEquals(EchoPhoneCastIssue.SourceChanged, transfer.state.value.issue)
    }

    @Test
    fun failedCommandRollsBackBeforePublishingIdleFailureState() = runBlocking {
        val host = FakeHost(failSend = true)
        val current = source()
        val transfer = EchoPhoneCastTransfer(host, this, Dispatchers.Unconfined)
        transfer.start({ current }, { plan }, { fail("Must not pause after failure") })
        host.preparationGate.complete(Unit)
        withTimeout(2000) { transfer.state.first { !it.isBusy } }
        assertEquals(listOf("send", "rollback"), host.events)
        assertEquals(EchoPhoneCastIssue.Failed, transfer.state.value.issue)
    }

    @Test
    fun unsafeQueueIsRejectedBeforePublishingOrMutatingAnything() = runBlocking {
        val host = FakeHost(atomic = false)
        val current = source()
        val transfer = EchoPhoneCastTransfer(host, this, Dispatchers.Unconfined)
        transfer.start({ current }, { plan.copy(tracks = listOf(sourceTrack, sourceTrack)) }, {})
        withTimeout(2000) { transfer.state.first { !it.isBusy } }
        assertFalse(host.preparing.isCompleted)
        assertTrue(host.events.isEmpty())
        assertEquals(EchoPhoneCastIssue.AtomicPhoneQueueRequired, transfer.state.value.issue)
    }

    @Test
    fun cancellationRollsBackAnInFlightCandidateAndClearsBusyState() = runBlocking {
        val host = FakeHost(holdSend = true)
        val current = source()
        val transfer = EchoPhoneCastTransfer(host, this, Dispatchers.Unconfined)
        transfer.start({ current }, { plan }, { fail("Must not pause after cancellation") })
        host.preparationGate.complete(Unit)
        host.sending.await()
        transfer.cancel()
        withTimeout(2000) { transfer.state.first { !it.isBusy } }
        assertEquals(listOf("send", "rollback"), host.events)
        assertNull(transfer.state.value.issue)
    }

    private class FakeHost(
        private val atomic: Boolean = true,
        private val failSend: Boolean = false,
        private val holdSend: Boolean = false,
    ) : EchoPhoneCastHost {
        override val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 1, "token")
        override val supportsAtomicPhoneQueue get() = atomic
        val preparing = CompletableDeferred<Unit>()
        val preparationGate = CompletableDeferred<Unit>()
        val sending = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var sentPosition: Long? = null
        private val server = EchoLinkCastServer(EchoLinkCastBodyFactory { _, _ -> null })

        override suspend fun prepare(tracks: List<EchoLinkCastSourceTrack>, target: EchoRemoteEndpoint): EchoLinkCastPreparedQueue {
            preparing.complete(Unit)
            preparationGate.await()
            return server.prepare(tracks.map { EchoLinkCastPublication(EchoLinkCastPolicy.newToken(), it.id, it.uri,
                title = it.title, artist = it.artist, peerHost = target.host) }, target.host, target.port)
        }
        override suspend fun send(plan: EchoLinkCastPlan, prepared: EchoLinkCastPreparedQueue?, positionMs: Long) {
            events += "send"
            sentPosition = positionMs
            sending.complete(Unit)
            if (failSend) error("Rejected by PC")
            if (holdSend) CompletableDeferred<Unit>().await()
        }
        override suspend fun commit(prepared: EchoLinkCastPreparedQueue?, target: EchoRemoteEndpoint) {
            events += "stop-old-renderer"
            assertTrue(server.commit(checkNotNull(prepared)))
            events += "commit"
        }
        override suspend fun rollback(prepared: EchoLinkCastPreparedQueue) { server.rollback(prepared); events += "rollback" }
        override fun activate(target: EchoRemoteEndpoint) { events += "activate" }
    }
}

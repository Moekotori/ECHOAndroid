package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemotePlaybackOrder
import app.echo.android.model.connect.EchoRemotePlaybackQueue
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemoteTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkSnapshotMergeTest {
    private val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 26789, "token")
    @Test
    fun v2IdentityAndCapabilitiesMergeWithoutErasingQueueRows() {
        val track = EchoRemoteTrack("same", "Song", "Artist", null, null, 1000, queueId = "occurrence-1")
        val previous = EchoRemotePlaybackSnapshot(queue = EchoRemotePlaybackQueue(items = listOf(track), revision = 1, currentQueueId = "occurrence-1"))
        val message = parseEchoLinkEventData(
            """{"snapshot":{"state":"playing","queueRevision":2,"currentQueueId":"occurrence-2","supportsAtomicPhoneQueue":true}}""", endpoint,
        )!!
        val merged = mergeEchoLinkSnapshot(previous, message)
        assertSame(previous.queue.items, merged.queue.items)
        assertEquals(2L, merged.queue.revision)
        assertEquals("occurrence-2", merged.queue.currentQueueId)
        assertTrue(merged.supportsAtomicPhoneQueue)
        assertTrue(merged.queueIdentityAvailable)
    }

    @Test
    fun partialEventsKeepArtworkVolumeLockAndQueueButApplyTheNewOutputAndMode() {
        val track = EchoRemoteTrack(id = "a", title = "A", artist = "Artist", album = null,
            artworkUrl = "http://pc/cover", durationMs = 240000)
        val previous = EchoRemotePlaybackSnapshot(
            track = track, volumeControlEnabled = false, volumeLockedReason = "fixed_volume",
            outputMode = "asio", playbackOrder = EchoRemotePlaybackOrder.Sequential,
            queue = EchoRemotePlaybackQueue("a", listOf(track)),
        )
        val event = parseEchoLinkEventData(
            """{"snapshot":{"track":{"id":"a","title":"A","artist":"Artist"},"output":{"mode":"shared"},"playbackOrder":"shuffle","positionMs":42000}}""",
            endpoint,
        )!!
        val merged = mergeEchoLinkSnapshot(previous, event)
        assertEquals(track.artworkUrl, merged.track?.artworkUrl)
        assertEquals(false, merged.volumeControlEnabled)
        assertEquals("fixed_volume", merged.volumeLockedReason)
        assertEquals("shared", merged.outputMode)
        assertEquals(EchoRemotePlaybackOrder.Shuffle, merged.playbackOrder)
        assertEquals(42000L, merged.positionMs)
        assertSame(previous.queue.items, merged.queue.items)
        val cleared = mergeEchoLinkSnapshot(merged, parseEchoLinkEventData(
            """{"snapshot":{"track":{"id":"a","title":"A","artist":"Artist","artworkUrl":null},"queue":{"items":[]},"volumeControlEnabled":true}}""", endpoint,
        )!!)
        assertNull(cleared.track?.artworkUrl)
        assertTrue(cleared.volumeControlEnabled)
        assertNull(cleared.volumeLockedReason)
        assertTrue(cleared.queue.items.isEmpty())
    }
}

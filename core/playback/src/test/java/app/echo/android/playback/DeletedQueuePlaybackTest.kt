package app.echo.android.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

@UnstableApi
class DeletedQueuePlaybackTest {
    @Test
    fun removingPausedCurrentTrackPreparesRemainingTrackWithoutAutoplay() {
        val fake = QueuePlayer(mutableListOf("A", "B"), current = 1, wanted = false)
        applyDeletedQueueRanges(fake.player, listOf(QueueRemovalRange(1, 2)))
        assertEquals(listOf("A"), fake.items)
        assertTrue(fake.prepared)
        assertFalse(fake.wanted)
        assertFalse(fake.stopped)
    }

    @Test
    fun removingPlayingCurrentTrackKeepsPlayIntentOnNextTrack() {
        val fake = QueuePlayer(mutableListOf("A", "B", "C"), current = 1, wanted = true)
        applyDeletedQueueRanges(fake.player, listOf(QueueRemovalRange(1, 2)))
        assertEquals("C", fake.items[fake.current])
        assertTrue(fake.prepared)
        assertTrue(fake.wanted)
    }

    @Test
    fun unrelatedRemovalDoesNotRestartCurrentAudioAndEmptyQueueStops() {
        val fake = QueuePlayer(mutableListOf("A", "B", "C"), current = 1, wanted = true)
        applyDeletedQueueRanges(fake.player, listOf(QueueRemovalRange(0, 1), QueueRemovalRange(2, 3)))
        assertEquals(listOf("B"), fake.items)
        assertEquals(27_000L, fake.position)
        assertFalse(fake.prepared)
        assertTrue(fake.wanted)
        applyDeletedQueueRanges(fake.player, listOf(QueueRemovalRange(0, 1)))
        assertTrue(fake.items.isEmpty())
        assertTrue(fake.stopped)
        assertFalse(fake.wanted)
    }

    private class QueuePlayer(val items: MutableList<String>, var current: Int, var wanted: Boolean) {
        var state = Player.STATE_READY
        var position = 27_000L
        var prepared = false
        var stopped = false
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getMediaItemCount" -> items.size
                "getCurrentMediaItemIndex" -> current
                "getPlayWhenReady" -> wanted
                "getPlaybackState" -> state
                "getPlayerError" -> null
                "removeMediaItems" -> {
                    val from = args!![0] as Int
                    val to = args[1] as Int
                    val old = items.getOrNull(current)
                    items.subList(from, to).clear()
                    if (old in items) current = items.indexOf(old)
                    else {
                        current = from.coerceAtMost(items.lastIndex)
                        position = 0
                        state = if (from >= items.size) Player.STATE_ENDED else Player.STATE_IDLE
                    }
                    null
                }
                "seekToDefaultPosition" -> { position = 0; state = Player.STATE_IDLE; null }
                "prepare" -> { prepared = true; state = Player.STATE_READY; null }
                "setPlayWhenReady" -> { wanted = args!![0] as Boolean; null }
                "pause" -> { wanted = false; null }
                "stop" -> { stopped = true; state = Player.STATE_IDLE; null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }
}

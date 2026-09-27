package app.echo.android.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in, short Media3 checks. Not part of default CI. All players are muted. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class NextUpQueueControllerTest {
    private fun item(id: String) = MediaItem.Builder().setMediaId(id)
        .setUri("asset:///fade-clock.wav").build().asQueueEntry(source = "Album A")

    private fun withPlayer(block: (ExoPlayer, NextUpQueueController) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val player = ExoPlayer.Builder(instrumentation.context).build()
            player.volume = 0f
            try {
                val queue = NextUpQueueController(player)
                player.setMediaItems(listOf(item("A1"), item("A2"), item("A3")))
                block(player, queue)
            } finally { player.release() }
        }
    }

    @Test fun requestsPlayInOrderThenResumeAlbumAndNeverRepeatConsumedItems() = withPlayer { player, queue ->
        queue.add(item("B"), false)
        queue.add(item("C"), false)
        player.repeatMode = Player.REPEAT_MODE_ALL
        assertEquals(listOf("A1", "B", "C", "A2", "A3"), player.ids())
        player.seekToNextMediaItem(); assertEquals("B", player.currentMediaItem?.mediaId)
        player.seekToNextMediaItem(); assertEquals("C", player.currentMediaItem?.mediaId)
        player.seekToNextMediaItem(); assertEquals("A2", player.currentMediaItem?.mediaId)
        assertEquals(listOf("A1", "A2", "A3"), player.ids())
        player.seekToNextMediaItem(); player.seekToNextMediaItem()
        assertEquals("A1", player.currentMediaItem?.mediaId)
    }

    @Test fun shuffleToggleAndRestorationKeepRequestsAheadOfOriginalSuccessor() = withPlayer { player, queue ->
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(0, 2, 1), 0L))
        player.shuffleModeEnabled = true
        queue.add(item("B"), false); queue.add(item("C"), false)
        player.shuffleModeEnabled = false; player.shuffleModeEnabled = true
        player.seekToNextMediaItem(); assertEquals("B", player.currentMediaItem?.mediaId)
        val snapshot = requireNotNull(player.toPlaybackSessionSnapshot())
        player.clearMediaItems()
        player.applyPlaybackSessionSnapshot(snapshot, play = false, preparePlayer = false)
        assertEquals("B", player.currentMediaItem?.mediaId)
        player.seekToNextMediaItem(); assertEquals("C", player.currentMediaItem?.mediaId)
        player.seekToNextMediaItem(); assertEquals("A3", player.currentMediaItem?.mediaId)
    }

    @Test fun sameSongOccurrencesCanBeMovedRemovedAndClearedIndependently() = withPlayer { player, queue ->
        queue.add(item("A2"), false); queue.add(item("A2"), false); queue.add(item("C"), false)
        val pending = player.toPlaybackQueueState().nextUpIndices.map { requireNotNull(player.getMediaItemAt(it).queueContext()).entryId }
        queue.edit("move", pending[2], pending[0])
        assertEquals("C", player.getMediaItemAt(player.nextMediaItemIndex).mediaId)
        queue.edit("remove", pending[0], null)
        assertEquals(2, player.toPlaybackQueueState().nextUpIndices.size)
        player.seekToNextMediaItem()
        queue.clearPending()
        assertEquals("C", player.currentMediaItem?.mediaId)
        assertEquals(listOf("A1", "C", "A2", "A3"), player.ids())
        player.seekToNextMediaItem()
        assertEquals("A2", player.currentMediaItem?.mediaId)
    }

    @Test fun singleRepeatStillAllowsManualNextAndQueueReplacementDropsRequests() = withPlayer { player, queue ->
        queue.add(item("B"), false); queue.add(item("C"), true)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.seekToNextMediaItem(); assertEquals("C", player.currentMediaItem?.mediaId)
        player.setMediaItems(listOf(item("Other")))
        assertTrue(player.toPlaybackQueueState().nextUpIndices.isEmpty())
        assertEquals(listOf("Other"), player.ids())
    }

    @Test fun reorderingOriginalQueueInShuffleChangesActualNextTrack() = withPlayer { player, queue ->
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(0, 2, 1), 0L))
        player.shuffleModeEnabled = true
        val second = requireNotNull(player.getMediaItemAt(1).queueContext()).entryId
        val third = requireNotNull(player.getMediaItemAt(2).queueContext()).entryId
        queue.edit("move", second, third)
        assertEquals("A2", player.getMediaItemAt(player.nextMediaItemIndex).mediaId)
    }

    @Test fun automaticPlaybackConsumesRequestsAndResumesWithoutUiCommands() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        val heard = java.util.Collections.synchronizedList(mutableListOf<String>())
        var player: ExoPlayer? = null
        var error: PlaybackException? = null
        try {
            instrumentation.runOnMainSync {
                val live = ExoPlayer.Builder(instrumentation.context).build()
                player = live
                live.volume = 0f
                val queue = NextUpQueueController(live)
                live.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.mediaId?.let(heard::add)
                        if (mediaItem?.mediaId == "A2") { live.pause(); done.countDown() }
                    }
                    override fun onPlayerError(e: PlaybackException) { error = e; done.countDown() }
                })
                fun short(id: String) = item(id).buildUpon().setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setEndPositionMs(100).build(),
                ).build()
                live.setMediaItems(listOf(short("A1"), short("A2")))
                queue.add(short("B"), false); queue.add(short("C"), false)
                live.prepare(); live.play()
            }
            assertTrue("Automatic playback did not reach the original queue", done.await(8, TimeUnit.SECONDS))
            assertNull(error)
            assertEquals(listOf("A1", "B", "C", "A2"), heard.toList())
            instrumentation.runOnMainSync { assertEquals(listOf("A1", "A2"), player!!.ids()) }
        } finally { instrumentation.runOnMainSync { player?.release() } }
    }

    private fun Player.ids() = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }
}

package app.echo.android.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder

/** Service-owned scheduling; Media3 still owns playback and gapless transitions. */
@UnstableApi
internal class NextUpQueueController(private val player: ExoPlayer) : Player.Listener {
    private var changing = false

    init { player.addListener(this) }

    fun add(item: MediaItem, first: Boolean) = change {
        val current = player.currentMediaItemIndex
        val pending = manualIndices().filter { it != current }
        val at = NextUpQueuePolicy.insertionIndex(current, player.mediaItemCount, pending, first)
        val source = player.currentMediaItem?.queueContext()?.source
        player.addMediaItem(at, item.asQueueEntry(nextUp = true, source = source))
        if (player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    fun clearPending() = change {
        val current = player.currentMediaItemIndex
        manualIndices().filter { it != current }.asReversed().forEach(player::removeMediaItem)
    }

    fun edit(action: String, entry: String, target: String?) = change {
        val from = indexOf(entry) ?: return@change
        when (action) {
            "remove" -> if (from != player.currentMediaItemIndex) player.removeMediaItem(from)
            "play" -> {
                // Consume the old manual occurrence after seeking (nested callbacks are guarded).
                val old = player.currentMediaItem?.queueContext()?.takeIf { it.nextUp }
                player.seekTo(from, 0L)
                if (old != null && old.entryId != entry) indexOf(old.entryId)?.let(player::removeMediaItem)
                if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
                player.play()
            }
            "move" -> {
                val to = target?.let(::indexOf) ?: return@change
                val manual = player.getMediaItemAt(from).queueContext()?.nextUp == true
                if (from == player.currentMediaItemIndex || to == player.currentMediaItemIndex ||
                    manual != (player.getMediaItemAt(to).queueContext()?.nextUp == true)) return@change
                if (player.shuffleModeEnabled && !manual) {
                    val order = player.queueShuffleOrder().toMutableList()
                    val at = order.indexOf(to)
                    order.remove(from)
                    order.add(at, from)
                    player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), 0L))
                } else player.moveMediaItem(from, to)
            }
        }
    }

    fun restoreOrder(order: List<Int>) = change {
        if (NextUpQueuePolicy.validOrder(order, player.mediaItemCount)) {
            player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), 0L))
        }
    }

    private fun indexOf(entry: String): Int? = (0 until player.mediaItemCount).firstOrNull {
        player.getMediaItemAt(it).queueContext()?.entryId == entry
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val old = oldPosition.mediaItem?.queueContext() ?: return
        if (!old.nextUp || old.entryId == newPosition.mediaItem?.queueContext()?.entryId) return
        change {
            val index = (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).queueContext()?.entryId == old.entryId
            }
            if (index != null && index != player.currentMediaItemIndex) player.removeMediaItem(index)
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = reconcile()
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = reconcile()

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && player.repeatMode == Player.REPEAT_MODE_ALL &&
            player.mediaItemCount == 1 && mediaItem?.queueContext()?.nextUp == true) {
            change { player.clearMediaItems() }
        }
    }

    private inline fun change(action: () -> Unit) {
        if (changing) return
        changing = true
        try { action() } finally { changing = false }
        reconcile()
    }

    private fun manualIndices(): List<Int> = (0 until player.mediaItemCount).filter {
        player.getMediaItemAt(it).queueContext()?.nextUp == true
    }

    private fun reconcile() {
        if (changing || player.mediaItemCount == 0) return
        changing = true
        try {
            // A seek into the base queue carries pending requests along without restarting audio.
            val pending = manualIndices().filter { it != player.currentMediaItemIndex }
            if (pending.withIndex().any { (offset, index) -> index != player.currentMediaItemIndex + 1 + offset }) {
                val ids = pending.mapNotNull { player.getMediaItemAt(it).queueContext()?.entryId }
                ids.forEachIndexed { offset, id ->
                    val from = indexOf(id) ?: return@forEachIndexed
                    val current = player.currentMediaItemIndex
                    val to = current + 1 + offset - if (from < current) 1 else 0
                    if (from != to) player.moveMediaItem(from, to)
                }
            }
            val manual = manualIndices()
            if (manual.isEmpty()) return
            // The base item before the manual block is the resume anchor, also while B/C plays.
            val anchor = manual.first() - 1
            val existing = player.queueShuffleOrder()
            val desired = NextUpQueuePolicy.shuffledOrder(existing, manual, anchor)
            if (desired != existing) {
                player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(desired.toIntArray(), 0L))
            }
        } finally { changing = false }
    }
}

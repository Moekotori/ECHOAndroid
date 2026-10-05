package app.echo.android.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import app.echo.android.model.playback.EchoShuffleMode
import app.echo.android.model.playback.EchoShuffleOptions

/** Service-owned scheduling; Media3 still owns playback and gapless transitions. */
@UnstableApi
internal class NextUpQueueController(private val player: ExoPlayer) : Player.Listener {
    private var changing = false
    private var shuffleOptions = EchoPlaybackRuntimeOptionsStore.options.value.shuffleOptions
    private val shuffleMode get() = shuffleOptions.mode
    private val pureRandom = PureRandomQueuePolicy()
    private var reshuffle = false
    private var spreadUpcoming = false

    init { player.addListener(this) }

    fun setShuffleOptions(options: EchoShuffleOptions) {
        val next = options.normalized
        if (next == shuffleOptions) return
        if (next.mode != shuffleMode) pureRandom.reset()
        reshuffle = next.mode != shuffleMode ||
            next.mode == EchoShuffleMode.PseudoRandom && next.spreadArtists != shuffleOptions.spreadArtists
        shuffleOptions = next
        reconcile()
    }

    fun playAlbum(items: List<MediaItem>, startIndex: Int, shuffle: Boolean) = change {
        if (items.isEmpty()) return@change
        val retained = AlbumQueuePolicy.retainedIndices(
            existingIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            nextUpIndices = manualIndices().toSet(),
            albumIds = items.map { it.mediaId },
            currentIndex = player.currentMediaItemIndex,
        ).map(player::getMediaItemAt)
        val start = startIndex.coerceIn(items.indices)
        player.shuffleModeEnabled = shuffle
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.setMediaItems(retained + items, retained.size + start, 0L)
        // Keep older albums in the previous section even when the new album is shuffled.
        val albumOrder = if (shuffle) PseudoRandomQueuePolicy.order(
            items.indices.toList(), start, items.map { it.mediaMetadata.artist?.toString() },
            shuffleOptions.spreadArtists && shuffleMode == EchoShuffleMode.PseudoRandom,
        )
            else items.indices.toList()
        val order = retained.indices.toList() + albumOrder.map { retained.size + it }
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), 0L))
        reshuffle = false
        spreadUpcoming = false
        player.prepare()
        player.play()
    }

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

    fun removeDeletedFiles(uris: Set<String>) = change {
        val ranges = DeletedQueueFilesPolicy.removalRanges(
            player.mediaItemCount,
            { player.getMediaItemAt(it).localConfiguration?.uri?.toString() },
            uris,
        )
        // Reverse ranges keep indices valid and suppress per-item Next Up reconciliation.
        applyDeletedQueueRanges(player, ranges)
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
                    rememberNext(order)
                    player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), 0L))
                } else player.moveMediaItem(from, to)
            }
        }
    }

    fun restoreOrder(order: List<Int>) = change {
        if (NextUpQueuePolicy.validOrder(order, player.mediaItemCount)) {
            rememberNext(order)
            player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), 0L))
        }
    }

    private fun rememberNext(order: List<Int>) {
        if (shuffleMode != EchoShuffleMode.PureRandom) return
        val next = order.drop(order.indexOf(player.currentMediaItemIndex) + 1).firstOrNull {
            player.getMediaItemAt(it).queueContext()?.nextUp != true
        }
        pureRandom.preferNext(next?.let {
            val item = player.getMediaItemAt(it)
            item.queueContext()?.entryId ?: "${item.mediaId}:$it"
        })
    }

    private fun indexOf(entry: String): Int? = (0 until player.mediaItemCount).firstOrNull {
        player.getMediaItemAt(it).queueContext()?.entryId == entry
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && player.shuffleModeEnabled &&
            player.repeatMode == Player.REPEAT_MODE_ALL && shuffleMode == EchoShuffleMode.PseudoRandom) {
            val order = player.queueShuffleOrder()
            if (oldPosition.mediaItemIndex == order.lastOrNull() && newPosition.mediaItemIndex == order.firstOrNull()) {
                reshuffle = true
            }
        }
        val old = oldPosition.mediaItem?.queueContext() ?: return
        if (!old.nextUp || old.entryId == newPosition.mediaItem?.queueContext()?.entryId) return
        change {
            val index = (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).queueContext()?.entryId == old.entryId
            }
            if (index != null && index != player.currentMediaItemIndex) player.removeMediaItem(index)
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (!changing && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) spreadUpcoming = true
        reconcile()
    }
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        pureRandom.reset()
        if (shuffleModeEnabled && !changing) spreadUpcoming = true
        reconcile()
    }
    override fun onRepeatModeChanged(repeatMode: Int) = reconcile()

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && player.repeatMode == Player.REPEAT_MODE_ALL &&
            player.mediaItemCount == 1 && mediaItem?.queueContext()?.nextUp == true) {
            change { player.clearMediaItems() }
        }
        reconcile()
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
        if (changing) return
        if (player.mediaItemCount == 0) {
            pureRandom.reset()
            return
        }
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
            if (manual.isEmpty() && (!player.shuffleModeEnabled ||
                    shuffleMode == EchoShuffleMode.PseudoRandom && !reshuffle && !spreadUpcoming)) return
            // The base item before the manual block is the resume anchor, also while B/C plays.
            val current = player.currentMediaItemIndex
            val anchor = manual.firstOrNull()?.minus(1) ?: current
            val existing = player.queueShuffleOrder()
            var desired = NextUpQueuePolicy.shuffledOrder(existing, manual, anchor)
            if (player.shuffleModeEnabled && shuffleMode == EchoShuffleMode.PureRandom) {
                val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
                desired = pureRandom.order(
                    desired,
                    items.mapIndexed { index, item -> item.queueContext()?.entryId ?: "${item.mediaId}:$index" },
                    items.map { it.mediaId }, current, manual.toSet(), player.repeatMode == Player.REPEAT_MODE_ALL,
                    shuffleOptions.recentTrackCount,
                )
            } else if (player.shuffleModeEnabled && reshuffle && current in existing) {
                // A strategy change or new round keeps the current song and playback position.
                val manualSet = manual.toHashSet()
                desired = PseudoRandomQueuePolicy.order(
                    existing.filter { it !in manualSet || it == current }, current,
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaMetadata.artist?.toString() },
                    shuffleOptions.spreadArtists,
                ) +
                    manual.filter { it != current }
                desired = NextUpQueuePolicy.shuffledOrder(desired, manual, current)
            } else if (player.shuffleModeEnabled && shuffleOptions.spreadArtists && spreadUpcoming && current in desired) {
                // Only spread the unplayed suffix; keep restored history and manual requests intact.
                val at = desired.indexOf(current)
                val upcoming = desired.drop(at + 1).filter { it !in manual }
                val artists = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaMetadata.artist?.toString() }
                desired = desired.take(at + 1) + manual.filter { it != current } +
                    PseudoRandomQueuePolicy.spread(upcoming, current, artists)
            }
            reshuffle = false
            spreadUpcoming = false
            if (desired != existing) {
                player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(desired.toIntArray(), 0L))
            }
        } finally { changing = false }
    }
}

package app.echo.android.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.echo.android.model.playback.EchoShuffleMode
import app.echo.android.model.playback.EchoShuffleOptions
import app.echo.android.model.playback.EchoPlayNextBehavior
import app.echo.android.model.playback.PlaybackShuffleResume
import app.echo.android.model.radio.EchoRadioStation
import java.util.UUID
import kotlinx.coroutines.*

/** Service-owned scheduling; Media3 still owns playback and gapless transitions. */
@UnstableApi
internal class NextUpQueueController(private val player: ExoPlayer, private val scope: CoroutineScope = EchoPlaybackProcessRuntime.scope) : Player.Listener {
    private var albumPlan: Job? = null
    private var albumRevision = 0L
    private var changing = false
    private var shuffleOptions = EchoPlaybackRuntimeOptionsStore.options.value.shuffleOptions
    private val shuffleMode get() = shuffleOptions.mode
    private val pureRandom = PureRandomQueuePolicy()
    private val desktopShuffle = DesktopShufflePolicy()
    private val libraryShuffle = LibraryShufflePlanner(scope, EchoPlaybackProcessRuntime::catalog)
    private val history = ShufflePlaybackHistory()
    var requestedRepeatMode = player.repeatMode
        private set
    private var reshuffle = false
    private var spreadUpcoming = false

    init { player.addListener(this) }

    fun setRepeatMode(mode: Int) = change { requestedRepeatMode = mode }

    private fun effectiveRepeatMode(): Int =
        if (player.shuffleModeEnabled && shuffleMode.desktopRecentCount != null && requestedRepeatMode == Player.REPEAT_MODE_ALL)
            Player.REPEAT_MODE_OFF else requestedRepeatMode

    fun beforeManualNext() {
        val next = player.nextMediaItemIndex
        if (next != C.INDEX_UNSET) prepareAutomaticAdvance(player.currentMediaItemIndex, next)
        else if (player.shuffleModeEnabled && shuffleMode == EchoShuffleMode.LibraryWide) {
            libraryShuffle.cancel()
            reconcile()
        }
    }

    fun seekToHistoryPrevious(restartCurrent: Boolean): Boolean {
        if (!player.shuffleModeEnabled || shuffleMode != EchoShuffleMode.LibraryWide) return false
        if (restartCurrent && player.currentPosition > player.maxSeekToPreviousPosition && player.isCurrentMediaItemSeekable) {
            player.seekTo(0L)
            return true
        }
        val current = player.currentMediaItem?.queueContext()?.entryId
        val indices = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).queueContext()?.entryId?.let { it to index }
        }.toMap()
        val previous = history.entries().lastOrNull { it != current && it in indices }
        if (previous != null) {
            history.rewindTo(previous, manual = false)
            player.seekToDefaultPosition(indices.getValue(previous))
        } else if (restartCurrent && player.isCurrentMediaItemSeekable) player.seekTo(0L)
        return true
    }

    fun setShuffleOptions(options: EchoShuffleOptions) {
        val next = options.normalized
        if (next == shuffleOptions) return
        if (next.copy(playNextBehavior = shuffleOptions.playNextBehavior) == shuffleOptions) {
            shuffleOptions = next
            return
        }
        val previousMode = shuffleMode
        if (next.mode != previousMode) {
            pureRandom.reset()
            desktopShuffle.preferNext(null)
        }
        reshuffle = reshuffle || next.mode != shuffleMode ||
            next.mode == EchoShuffleMode.PseudoRandom && next.spreadArtists != shuffleOptions.spreadArtists
        shuffleOptions = next
        if (previousMode == EchoShuffleMode.LibraryWide && next.mode != previousMode) change { clearLibraryShuffle() }
        else reconcile()
    }

    fun replaceQueue(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) = change {
        libraryShuffle.cancel()
        desktopShuffle.preferNext(null)
        history.restore(null, false, emptyList())
        player.setMediaItems(items, startIndex, startPositionMs)
        reshuffle = player.shuffleModeEnabled && items.isNotEmpty()
        spreadUpcoming = false
    }

    fun restoreSession(snapshot: EchoPlaybackSessionSnapshot, play: Boolean, preparePlayer: Boolean) = change {
        keepRestoredOrder()
        requestedRepeatMode = snapshot.repeatMode.toPlayerRepeatMode()
        player.applyPlaybackSessionSnapshot(snapshot, play, preparePlayer, applyRepeatMode = false)
        restoreShuffleState(snapshot.shuffleOrder, snapshot.shuffleResume)
    }

    fun playAlbum(items: List<MediaItem>, startIndex: Int, shuffle: Boolean) = change {
        if (items.isEmpty()) return@change
        clearLibraryShuffle()
        desktopShuffle.preferNext(null)
        val retained = AlbumQueuePolicy.retainedIndices(
            existingIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            nextUpIndices = manualIndices().toSet(),
            albumIds = items.map { it.mediaId },
            currentIndex = player.currentMediaItemIndex,
        ).map(player::getMediaItemAt)
        val start = startIndex.coerceIn(items.indices)
        player.shuffleModeEnabled = shuffle
        requestedRepeatMode = Player.REPEAT_MODE_OFF
        player.repeatMode = Player.REPEAT_MODE_OFF
        val group = UUID.randomUUID().toString()
        val scopedItems = items.map { item ->
            val entry = if (item.queueContext() == null) item.asQueueEntry() else item
            entry.withQueueContext(requireNotNull(entry.queueContext()).copy(shuffleGroup = group))
        }
        player.setMediaItems(retained + scopedItems, retained.size + start, 0L)
        // Keep older albums in the previous section even when the new album is shuffled.
        val albumOrder = if (shuffle && shuffleMode != EchoShuffleMode.Albums) PseudoRandomQueuePolicy.order(
            items.indices.toList(), start, items.map { it.mediaMetadata.artist?.toString() },
            shuffleOptions.spreadArtists && shuffleMode == EchoShuffleMode.PseudoRandom,
        )
            else items.indices.toList()
        val order = retained.indices.toList() + albumOrder.map { retained.size + it }
        player.setQueueShuffleOrder(order)
        reshuffle = false
        spreadUpcoming = false
        if (shuffle && shuffleMode == EchoShuffleMode.Albums) spreadUpcoming = true
        player.prepare()
        player.play()
    }

    fun add(item: MediaItem, first: Boolean) = change {
        val current = player.currentMediaItemIndex
        val pending = manualIndices().filter { it != current }
        val at = NextUpQueuePolicy.insertionIndex(current, player.mediaItemCount, pending, first)
        val source = player.currentMediaItem?.queueContext()?.source
        if (first && shuffleOptions.playNextBehavior == EchoPlayNextBehavior.MoveUpcoming) {
            val order = if (player.shuffleModeEnabled) player.queueShuffleOrder() else (0 until player.mediaItemCount).toList()
            val found = upcomingQueueMatch(order, current,
                (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }, item.mediaId)
            if (found != null) {
                val original = player.getMediaItemAt(found)
                val context = original.queueContext()
                val moved = if (context != null) original.withQueueContext(context.copy(nextUp = true, automaticShuffle = false))
                    else original.asQueueEntry(nextUp = true, source = source)
                player.replaceMediaItem(found, moved)
                val destination = at - if (found < at) 1 else 0
                if (found != destination) player.moveMediaItem(found, destination)
                return@change
            }
        }
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

    fun removeHiddenTracks(ids: Set<String>) = change {
        libraryShuffle.cancel()
        if (player.currentMediaItem?.mediaId in ids) EchoPlaybackProcessRuntime.onSleepTimerUserCommand(
            app.echo.android.model.playback.EchoSleepTimerCancellationReason.QueueChanged)
        applyDeletedQueueRanges(player, HiddenQueuePolicy.ranges(player.mediaItemCount,
            { player.getMediaItemAt(it).mediaId }, ids))
    }

    fun removeDeletedFiles(uris: Set<String>) = change {
        libraryShuffle.cancel()
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
            "remove" -> if (from != player.currentMediaItemIndex) {
                if (player.getMediaItemAt(from).queueContext()?.automaticShuffle == true) libraryShuffle.cancel()
                player.removeMediaItem(from)
            }
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
                    player.setQueueShuffleOrder(order)
                } else player.moveMediaItem(from, to)
            }
        }
    }

    fun restoreOrder(order: List<Int>, resume: PlaybackShuffleResume? = null) = change {
        if (NextUpQueuePolicy.validOrder(order, player.mediaItemCount)) {
            keepRestoredOrder()
            restoreShuffleState(order, resume)
            player.setQueueShuffleOrder(order)
        }
    }

    private fun restoreShuffleState(order: List<Int>, resume: PlaybackShuffleResume?) {
        val context = player.currentMediaItem?.queueContext()
        val saved = resume?.takeIf { it.entryId == context?.entryId }
        history.restore(context?.entryId, context?.nextUp == true, saved?.previousEntryIds.orEmpty(),
            played = player.isPlaying || player.currentPosition > 0L)
        if (!NextUpQueuePolicy.validOrder(order, player.mediaItemCount)) return
        if (shuffleMode == EchoShuffleMode.PureRandom) {
            val hasSavedNext = order.drop(order.indexOf(player.currentMediaItemIndex) + 1).any {
                player.getMediaItemAt(it).queueContext()?.nextUp != true
            }
            val remaining = saved?.pureRemaining ?: 0.takeIf { !hasSavedNext && requestedRepeatMode != Player.REPEAT_MODE_ALL }
            pureRandom.restore(remaining, saved?.recentTrackIds.orEmpty())
        }
        rememberNext(order)
    }

    private fun keepRestoredOrder() {
        albumPlan?.cancel()
        albumPlan = null
        reshuffle = false
        spreadUpcoming = false
    }

    private fun rememberNext(order: List<Int>) {
        if (shuffleMode != EchoShuffleMode.PureRandom && shuffleMode.desktopRecentCount == null) return
        player.currentMediaItem?.let { observeShuffleTrack(it, player.currentMediaItemIndex) }
        val next = order.drop(order.indexOf(player.currentMediaItemIndex) + 1).firstOrNull {
            player.getMediaItemAt(it).queueContext()?.nextUp != true
        }
        val key = next?.let { shuffleKey(player.getMediaItemAt(it), it) }
        if (shuffleMode == EchoShuffleMode.PureRandom) pureRandom.preferNext(key)
        else desktopShuffle.preferNext(key)
    }

    private fun indexOf(entry: String): Int? = (0 until player.mediaItemCount).firstOrNull {
        player.getMediaItemAt(it).queueContext()?.entryId == entry
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            prepareAutomaticAdvance(oldPosition.mediaItemIndex, newPosition.mediaItemIndex)
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
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && shuffleMode.desktopRecentCount != null) return
        if (!changing && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) spreadUpcoming = true
        reconcile()
    }

    /** Called before AutoMix's seek so the normal transition reconciliation starts the next round. */
    fun prepareAutomaticAdvance(from: Int, to: Int) {
        if (player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_ALL &&
            shuffleMode != EchoShuffleMode.PureRandom && shuffleMode.desktopRecentCount == null &&
            NextUpQueuePolicy.startsShuffleRound(player.queueShuffleOrder(), from, to)) reshuffle = true
    }
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        pureRandom.reset()
        if (!changing) desktopShuffle.preferNext(null)
        if (!shuffleModeEnabled && !changing && shuffleMode == EchoShuffleMode.LibraryWide) {
            change { clearLibraryShuffle() }
            return
        }
        if (shuffleModeEnabled && !changing) reshuffle = true
        reconcile()
    }
    override fun onRepeatModeChanged(repeatMode: Int) {
        if (!changing && repeatMode != effectiveRepeatMode()) requestedRepeatMode = repeatMode
        reconcile()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!changing && isPlaying) {
            val context = player.currentMediaItem?.queueContext()
            history.observe(context?.entryId, context?.nextUp == true, playing = true)
            publishShuffleResume()
        }
    }

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
        changing = true
        try { reconcileQueue() } finally {
            changing = false
            publishShuffleResume()
        }
    }

    private fun reconcileQueue() {
        val repeat = effectiveRepeatMode()
        if (player.repeatMode != repeat) player.repeatMode = repeat
        val planningAlbums = albumPlan?.isActive == true
        albumRevision++
        albumPlan?.cancel()
        albumPlan = null
        if (planningAlbums && player.shuffleModeEnabled && shuffleMode == EchoShuffleMode.Albums) spreadUpcoming = true
        if (player.mediaItemCount == 0) {
            pureRandom.reset()
            libraryShuffle.cancel()
            history.restore(null, false, emptyList())
            return
        }
        run {
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
            val current = player.currentMediaItemIndex
            val active = player.currentMediaItem ?: return
            val context = active.queueContext()
            history.observe(context?.entryId, context?.nextUp == true, player.isPlaying)
            observeShuffleTrack(active, current)
            val desktopCount = shuffleMode.desktopRecentCount
            if (manual.isEmpty() && (!player.shuffleModeEnabled ||
                    desktopCount == null && shuffleMode != EchoShuffleMode.PureRandom && !reshuffle && !spreadUpcoming)) return
            // The base item before the manual block is the resume anchor, also while B/C plays.
            val anchor = manual.firstOrNull()?.minus(1) ?: current
            val existing = player.queueShuffleOrder()
            var desired = NextUpQueuePolicy.shuffledOrder(existing, manual, anchor)
            if (player.shuffleModeEnabled && shuffleMode == EchoShuffleMode.Albums && (reshuffle || spreadUpcoming)) {
                val entries = (0 until player.mediaItemCount).map { index ->
                    val metadata = player.getMediaItemAt(index).mediaMetadata
                    AlbumShuffleEntry(index, metadata.albumTitle?.toString(), metadata.artist?.toString(), metadata.albumArtist?.toString(),
                        metadata.extras?.getString(EchoPlaybackSourceExtra), metadata.discNumber, metadata.trackNumber)
                }
                val revision = albumRevision
                val manualSet = manual.toHashSet()
                val base = desired.filter { it !in manualSet }.let { order ->
                    if (reshuffle && anchor in order) listOf(anchor) + order.filterNot { it == anchor } else order
                }
                val current = player.currentMediaItemIndex
                spreadUpcoming = false
                albumPlan = scope.launch {
                    val order = withContext(Dispatchers.Default) {
                        NextUpQueuePolicy.shuffledOrder(AlbumShuffleQueuePolicy.order(base, anchor, entries) + manual, manual, anchor)
                    }
                    if (revision == albumRevision && player.currentMediaItemIndex == current &&
                        player.shuffleModeEnabled && shuffleMode == EchoShuffleMode.Albums && player.queueShuffleOrder() == existing) {
                        reshuffle = false
                        changing = true
                        try { player.setQueueShuffleOrder(order) }
                        finally { changing = false }
                    }
                }
                return
            }
            if (player.shuffleModeEnabled && desktopCount != null) {
                val manualSet = manual.toSet()
                val libraryWide = shuffleMode == EchoShuffleMode.LibraryWide
                val next = if (libraryWide) {
                    planLibraryShuffle(active, current, existing, desktopCount)
                } else {
                    val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
                    val group = items.getOrNull(anchor)?.queueContext()?.shuffleGroup
                    val candidates = existing.filter { it !in manualSet &&
                        (group == null || items[it].queueContext()?.shuffleGroup == group) }
                    desktopShuffle.next(candidates, items.mapIndexed { index, item -> shuffleKey(item, index) },
                        items.map { it.mediaId }, current, desktopCount) { index ->
                        val tags = items[index].mediaMetadata
                        ShuffleTrackTags(tags.artist?.toString(), tags.albumTitle?.toString(), tags.albumArtist?.toString())
                    }
                }
                val order = if (reshuffle && !libraryWide) listOf(current) + existing.filterNot { it == current } else existing
                desired = DesktopShufflePolicy.order(order, current, manualSet, next, keepUpcoming = !libraryWide)
            } else if (player.shuffleModeEnabled && shuffleMode == EchoShuffleMode.PureRandom) {
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
            } else if (player.shuffleModeEnabled && shuffleMode == EchoShuffleMode.PseudoRandom && shuffleOptions.spreadArtists && spreadUpcoming && current in desired) {
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
                player.setQueueShuffleOrder(desired)
            }
        }
    }

    private fun publishShuffleResume() {
        val entry = player.currentMediaItem?.queueContext()?.entryId
        val pure = player.shuffleModeEnabled && shuffleMode == EchoShuffleMode.PureRandom
        EchoPlaybackProcessRuntime.publishShuffleResume(entry?.let {
            PlaybackShuffleResume(it, pureRandom.remainingDraws().takeIf { pure },
                if (pure) pureRandom.recentIds() else emptyList(), history.entries())
        })
    }

    private fun observeShuffleTrack(item: MediaItem, index: Int) =
        desktopShuffle.observe(shuffleKey(item, index), item.mediaId, item.queueContext()?.nextUp == true)

    private fun shuffleKey(item: MediaItem, index: Int): String = item.queueContext()?.entryId ?: "${item.mediaId}:$index"

    private fun planLibraryShuffle(active: MediaItem, current: Int, existing: List<Int>, count: Int): Int? {
        val next = existing.drop(existing.indexOf(current) + 1).firstOrNull { index ->
            val item = player.getMediaItemAt(index)
            item.queueContext()?.automaticShuffle == true && item.mediaId != active.mediaId
        }
        if (next != null) return next
        if (EchoRadioStation.isRadio(active.mediaId) || active.mediaMetadata.extras?.getString(EchoPlaybackSourceExtra) == "podcast") {
            libraryShuffle.cancel()
            return null
        }
        val key = shuffleKey(active, current)
        libraryShuffle.request(key, active.mediaId, desktopShuffle.recent(count)) { track ->
            var resumeAfterEnd = false
            change {
                val now = player.currentMediaItem ?: return@change
                if (shuffleMode != EchoShuffleMode.LibraryWide || !player.shuffleModeEnabled ||
                    shuffleKey(now, player.currentMediaItemIndex) != key) return@change
                trimLibraryShuffle()
                val activeIndex = player.currentMediaItemIndex
                val nextIndex = NextUpQueuePolicy.insertionIndex(activeIndex, player.mediaItemCount,
                    manualIndices().filter { it != activeIndex }, first = false)
                player.addMediaItem(nextIndex, track.toMediaItem().asQueueEntry(automaticShuffle = true))
                player.setQueueShuffleOrder(DesktopShufflePolicy.order(player.queueShuffleOrder(), player.currentMediaItemIndex,
                    manualIndices().toSet(), nextIndex))
                resumeAfterEnd = player.playWhenReady && player.playbackState == Player.STATE_ENDED &&
                    player.repeatMode != Player.REPEAT_MODE_ONE
            }
            if (resumeAfterEnd) player.seekToNextMediaItem()
        }
        return null
    }

    private fun automaticIndices(): List<Int> = (0 until player.mediaItemCount).filter {
        it != player.currentMediaItemIndex && player.getMediaItemAt(it).queueContext()?.automaticShuffle == true
    }

    private fun trimLibraryShuffle() {
        val old = automaticIndices()
        old.take((old.size - 30).coerceAtLeast(0)).asReversed().forEach(player::removeMediaItem)
    }

    private fun clearLibraryShuffle() {
        libraryShuffle.cancel()
        automaticIndices().asReversed().forEach(player::removeMediaItem)
    }
}

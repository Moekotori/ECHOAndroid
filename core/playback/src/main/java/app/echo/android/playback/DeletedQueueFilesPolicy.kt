package app.echo.android.playback

import app.echo.android.model.library.CueSheetPolicy
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

internal data class QueueRemovalRange(val start: Int, val endExclusive: Int)

/** Plan against the current service timeline; no UI indices or track IDs are trusted. */
internal object DeletedQueueFilesPolicy {
    const val MaxSources = 200

    fun validSources(uris: Collection<String>): Boolean = uris.isNotEmpty() &&
        uris.size <= MaxSources && uris.all {
            it.length <= 8192 && it.substringBefore(':') in setOf("content", "file")
        }

    fun removalRanges(
        itemCount: Int,
        uriAt: (Int) -> String?,
        deletedUris: Set<String>,
    ): List<QueueRemovalRange> {
        require(validSources(deletedUris))
        val ranges = ArrayList<QueueRemovalRange>()
        var start = -1
        for (index in 0 until itemCount) {
            val remove = uriAt(index)?.let(CueSheetPolicy::playbackUri) in deletedUris
            if (remove && start < 0) start = index
            if (!remove && start >= 0) {
                ranges.add(QueueRemovalRange(start, index))
                start = -1
            }
        }
        if (start >= 0) ranges.add(QueueRemovalRange(start, itemCount))
        return ranges
    }
}

/** Keep the play/pause intent and leave unaffected audio clocks alone. Called on the player thread. */
@UnstableApi
internal fun applyDeletedQueueRanges(player: Player, ranges: List<QueueRemovalRange>) {
    if (ranges.isEmpty()) return
    val currentRemoved = ranges.any { player.currentMediaItemIndex in it.start until it.endExclusive }
    val playWhenReady = player.playWhenReady
    for (range in ranges.asReversed()) player.removeMediaItems(range.start, range.endExclusive)
    if (player.mediaItemCount == 0) {
        player.pause()
        player.stop()
    } else if (currentRemoved) {
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED || player.playerError != null) {
            player.prepare()
        }
        player.playWhenReady = playWhenReady
    }
}

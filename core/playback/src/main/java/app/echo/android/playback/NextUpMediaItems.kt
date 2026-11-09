package app.echo.android.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import app.echo.android.model.playback.PlaybackQueueContext
import app.echo.android.model.playback.PlaybackShuffleResume
import java.util.UUID
import kotlin.random.Random

private const val EntryKey = "echo.queue.entry"
private const val ManualKey = "echo.queue.nextUp"
private const val SourceKey = "echo.queue.source"
private const val AutomaticShuffleKey = "echo.queue.automaticShuffle"
private const val ShuffleGroupKey = "echo.queue.shuffleGroup"

fun MediaItem.queueContext(): PlaybackQueueContext? {
    val extras = mediaMetadata.extras ?: return null
    val entryId = extras.getString(EntryKey)?.takeIf { it.isNotBlank() } ?: return null
    return PlaybackQueueContext(entryId, extras.getBoolean(ManualKey), extras.getString(SourceKey),
        extras.getBoolean(AutomaticShuffleKey), extras.getString(ShuffleGroupKey))
}

fun MediaItem.withQueueContext(context: PlaybackQueueContext): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
        putString(EntryKey, context.entryId)
        putBoolean(ManualKey, context.nextUp)
        putString(SourceKey, context.source)
        putBoolean(AutomaticShuffleKey, context.automaticShuffle)
        putString(ShuffleGroupKey, context.shuffleGroup)
    }
    return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
}

fun MediaItem.asQueueEntry(nextUp: Boolean = false, source: String? = null, automaticShuffle: Boolean = false): MediaItem =
    withQueueContext(PlaybackQueueContext(UUID.randomUUID().toString(), nextUp, source, automaticShuffle))

fun PlaybackShuffleResume.toShuffleResumeBundle(): Bundle = Bundle().apply {
    putString("entryId", entryId)
    pureRemaining?.let { putInt("remaining", it) }
    putStringArrayList("recent", ArrayList(recentTrackIds))
    putStringArrayList("previous", ArrayList(previousEntryIds))
}

internal fun Bundle.readShuffleResume(): PlaybackShuffleResume? {
    val entry = getString("entryId") ?: return null
    return PlaybackShuffleResume(entry, getInt("remaining", -1).takeIf { it >= 0 },
        getStringArrayList("recent").orEmpty().takeLast(PlaybackShuffleResume.HistoryLimit),
        getStringArrayList("previous").orEmpty().takeLast(PlaybackShuffleResume.HistoryLimit))
}

/** Read the real shuffled traversal even when shuffle is temporarily disabled. */
fun Player.queueShuffleOrder(): List<Int> {
    val timeline = currentTimeline
    val result = ArrayList<Int>(timeline.windowCount)
    var index = timeline.getFirstWindowIndex(true)
    while (index != C.INDEX_UNSET && result.size < timeline.windowCount) {
        result += index
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
    }
    return result
}

@UnstableApi
internal fun ExoPlayer.setQueueShuffleOrder(order: List<Int>) {
    // The seed drives future Media3 insertions and replacements, even with an explicit order.
    shuffleOrder = ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), Random.nextLong())
}

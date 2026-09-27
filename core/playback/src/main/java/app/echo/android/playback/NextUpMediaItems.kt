package app.echo.android.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import app.echo.android.model.playback.PlaybackQueueContext
import java.util.UUID

private const val EntryKey = "echo.queue.entry"
private const val ManualKey = "echo.queue.nextUp"
private const val SourceKey = "echo.queue.source"

fun MediaItem.queueContext(): PlaybackQueueContext? {
    val extras = mediaMetadata.extras ?: return null
    val entryId = extras.getString(EntryKey)?.takeIf { it.isNotBlank() } ?: return null
    return PlaybackQueueContext(entryId, extras.getBoolean(ManualKey), extras.getString(SourceKey))
}

fun MediaItem.withQueueContext(context: PlaybackQueueContext): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
        putString(EntryKey, context.entryId)
        putBoolean(ManualKey, context.nextUp)
        putString(SourceKey, context.source)
    }
    return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
}

fun MediaItem.asQueueEntry(nextUp: Boolean = false, source: String? = null): MediaItem =
    withQueueContext(PlaybackQueueContext(UUID.randomUUID().toString(), nextUp, source))

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

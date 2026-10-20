package app.echo.android

import app.echo.android.model.playback.PlaybackQueueState

internal class PlaybackPersistenceQueue(
    val revision: Long,
    val queue: PlaybackQueueState,
    val shuffleOrder: List<Int>,
)

/** Main-thread owned. Immutable player timelines invalidate all queue-derived persistence data. */
internal class PlaybackPersistenceQueueCache {
    private var owner: Any? = null
    private var timeline: Any? = null
    private var count = -1
    private var revision = 0L
    private var cached: PlaybackPersistenceQueue? = null

    fun snapshot(
        owner: Any,
        timeline: Any,
        count: Int,
        readQueue: () -> PlaybackQueueState,
        readShuffleOrder: () -> List<Int>,
    ): PlaybackPersistenceQueue {
        cached?.takeIf { this.owner === owner && this.timeline === timeline && this.count == count }
            ?.let { return it }
        val next = PlaybackPersistenceQueue(++revision, readQueue(), readShuffleOrder())
        this.owner = owner
        this.timeline = timeline
        this.count = count
        cached = next
        return next
    }

    fun invalidate() {
        owner = null
        timeline = null
        cached = null
    }
}

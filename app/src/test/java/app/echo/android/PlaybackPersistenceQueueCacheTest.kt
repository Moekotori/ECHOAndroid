package app.echo.android

import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueContext
import app.echo.android.model.playback.PlaybackQueueState
import org.junit.Assert.*
import org.junit.Test

class PlaybackPersistenceQueueCacheTest {
    @Test fun positionSavesReuseQueueWithoutReadingItAgain() {
        val cache = PlaybackPersistenceQueueCache()
        val owner = Any()
        val timeline = Any()
        var reads = 0
        fun snapshot() = cache.snapshot(owner, timeline, 2,
            readQueue = { reads++; queue() }, readShuffleOrder = { listOf(1, 0) })
        val first = snapshot()
        repeat(20) { assertSame(first, snapshot()) }
        assertEquals(1, reads)
        assertEquals(listOf("entry-a", "entry-b"), first.queue.items.map { it.queueContext?.entryId })
        assertEquals(listOf(1, 0), first.shuffleOrder)
    }

    @Test fun timelineReplacementControllerReplacementAndInvalidationRefreshTheSnapshot() {
        val cache = PlaybackPersistenceQueueCache()
        val owner = Any()
        val timeline = Any()
        fun snapshot(who: Any = owner, whenChanged: Any = timeline, count: Int = 2) =
            cache.snapshot(who, whenChanged, count, { queue() }, { listOf(1, 0) })
        val first = snapshot()
        val changedTimeline = snapshot(whenChanged = Any())
        assertTrue(changedTimeline.revision > first.revision)
        val changedOwner = snapshot(who = Any())
        assertTrue(changedOwner.revision > changedTimeline.revision)
        val changedCount = snapshot(count = 3)
        assertTrue(changedCount.revision > changedOwner.revision)
        cache.invalidate()
        assertTrue(snapshot(count = 3).revision > changedCount.revision)
    }

    private fun queue() = PlaybackQueueState(items = listOf("entry-a", "entry-b").map {
        EchoTrackRef("same-song", "file:///song", "song", "artist", queueContext = PlaybackQueueContext(it))
    })
}

package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeletedQueueFilesPolicyTest {
    @Test
    fun removesEveryOccurrenceAndCueSegmentWhileKeepingOtherSourcesInOrder() {
        val deleted = "content://media/external/audio/media/1"
        val keep = "content://media/external/audio/media/2"
        val queue = mutableListOf(deleted, "$deleted#echo-cue=1", keep, deleted, "https://pc/audio/1", null)
        val ranges = DeletedQueueFilesPolicy.removalRanges(queue.size, { queue[it] }, setOf(deleted))
        assertEquals(listOf(QueueRemovalRange(0, 2), QueueRemovalRange(3, 4)), ranges)
        ranges.asReversed().forEach { queue.subList(it.start, it.endExclusive).clear() }
        assertEquals(listOf(keep, "https://pc/audio/1", null), queue)
    }

    @Test
    fun emptyQueueAndUnrelatedDeletionProduceNoEdits() {
        val deleted = "file:///music/removed.flac"
        assertEquals(emptyList<QueueRemovalRange>(), DeletedQueueFilesPolicy.removalRanges(0, { error("Empty queue must not be read") }, setOf(deleted)))
        assertEquals(emptyList<QueueRemovalRange>(), DeletedQueueFilesPolicy.removalRanges(1, { "file:///music/kept.flac" }, setOf(deleted)))
    }

    @Test
    fun completeDeletionIsOneRangeAndTargetCountIsBounded() {
        val deleted = "file:///music/removed.flac"
        assertEquals(listOf(QueueRemovalRange(0, 5)), DeletedQueueFilesPolicy.removalRanges(5, { deleted }, setOf(deleted)))
        assertFalse(DeletedQueueFilesPolicy.validSources(emptyList()))
        assertFalse(DeletedQueueFilesPolicy.validSources(listOf("https://pc/audio/1")))
        assertFalse(DeletedQueueFilesPolicy.validSources(List(201) { deleted }))
    }
}

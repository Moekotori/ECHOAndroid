package app.echo.android.feature.player

import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueContext
import app.echo.android.model.playback.PlaybackQueueState
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSectionsTest {
    @Test
    fun endOfQueueKeepsEarlierOccurrencesAvailableWithoutRepeatingCurrent() {
        val tracks = List(3) { track(it) }
        val sections = queueSections(PlaybackQueueState(tracks, currentIndex = 2))
        assertEquals(emptyList<Int>(), sections.nextUp)
        assertEquals(emptyList<Int>(), sections.continuation)
        assertEquals(listOf(0, 1), sections.previous)
    }

    @Test
    fun shuffledQueueSeparatesInsertionsAndPreservesContinuationOrder() {
        val tracks = List(6) { track(it, nextUp = it == 3 || it == 5) }
        val queue = PlaybackQueueState(tracks, currentIndex = 2, playOrder = listOf(4, 2, 0, 3, 1, 5))
        assertEquals(QueueSections(listOf(3, 5), listOf(0, 1), listOf(4)), queueSections(queue))
        assertEquals(QueueSections(listOf(3, 5), listOf(0, 1, 4), emptyList()), queueSections(queue.copy(repeatAll = true)))
    }

    private fun track(index: Int, nextUp: Boolean = false) = EchoTrackRef(
        id = "same-track", uri = "content://music/1", title = "Song", artist = "Artist",
        queueContext = PlaybackQueueContext(entryId = "occurrence-$index", nextUp = nextUp),
    )
}

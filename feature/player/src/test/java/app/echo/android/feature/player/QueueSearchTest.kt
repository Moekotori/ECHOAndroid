package app.echo.android.feature.player

import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test

class QueueSearchTest {
    private fun track(entry: String?) = EchoTrackRef("same", "content://audio/1", "Song", "Artist",
        queueContext = entry?.let { PlaybackQueueContext(entryId = it) })

    @Test fun duplicateSongsKeepTheirOriginalOccurrenceAndPlaybackPosition() {
        val first = track("first"); val second = track("second")
        val hits = searchQueue(PlaybackQueueState(listOf(first, second), playOrder = listOf(1, 0)), "ARTIST")
        assertEquals(listOf("second", "first"), hits.map { it.entryId })
        assertEquals(listOf(1, 2), hits.map { it.position })
        assertEquals(0, queueSearchTarget(hits.first(), PlaybackQueueState(listOf(second, first))))
        assertNull(queueSearchTarget(hits.first(), PlaybackQueueState(listOf(first))))
    }

    @Test fun legacyResultIsInvalidatedWhenQueueChangesAndResultsAreBounded() {
        val hit = searchQueue(PlaybackQueueState(listOf(track(null), track(null))), "Song").first()
        assertNull(queueSearchTarget(hit, PlaybackQueueState(listOf(track(null)))))
        assertEquals(200, searchQueue(PlaybackQueueState(List(250) { track("$it") }), "song").size)
        assertTrue(searchQueue(PlaybackQueueState(), " ").isEmpty())
    }
}

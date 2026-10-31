package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackQueueContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SavedPlaybackCueRecoveryTest {
    private val first = EchoTrackRef("saf:album#cue:1", "content://audio/album", "First", "Artist", durationMs = 60_000L)
    private val second = first.copy(id = "saf:album#cue:2", title = "Second")
    private fun session(queue: List<EchoTrackRef>) = EchoSavedPlaybackSession(queue, 0, 1_000L, false)
    private fun row(track: EchoTrackRef, start: Long, end: Long) =
        EchoTrack(track.id, track.uri, "Library title", "Library artist", durationMs = 0L,
            clipStartMs = start, clipEndMs = end).toLibraryTrackEntity()

    @Test
    fun legacyQueueRecoversRangesAfterResumeWithoutChangingOrderOrOccurrences() = runBlocking {
        val stored = session(listOf(first, second, second.copy(queueContext = PlaybackQueueContext("next", true))))
            .copy(shuffleOrder = listOf(2, 0, 1))
        val legacyJson = JSONObject(stored.toPreferenceValue()).apply {
            val queue = getJSONArray("queue")
            for (index in 0 until queue.length()) {
                queue.getJSONObject(index).apply { remove("clipStartMs"); remove("clipEndMs") }
            }
        }
        val legacy = requireNotNull(parsePlaybackSession(legacyJson.toString()))
        val latest = legacy.copy(currentIndex = 1, positionMs = 25_000L, playWhenReady = true,
            repeatMode = EchoRepeatMode.All, playbackSpeed = 1.25f, shuffleEnabled = true)
        val resume = parsePlaybackResume(latest.toResumePreferenceValue(legacy.playbackQueueIdentity()))
        val requested = ArrayList<List<String>>()

        val recovered = legacy.restorePlaybackSession(resume) { ids ->
            requested += ids
            listOf(row(second, 60_000L, 120_000L), row(first, 0L, 60_000L))
        }

        assertEquals(listOf(listOf(first.id, second.id)), requested)
        assertEquals(latest.copy(queue = listOf(
            first.copy(clipEndMs = 60_000L),
            second.copy(clipStartMs = 60_000L, clipEndMs = 120_000L),
            stored.queue[2].copy(clipStartMs = 60_000L, clipEndMs = 120_000L),
        )), recovered)
        assertEquals(recovered, parsePlaybackSession(recovered.toPreferenceValue()))
        assertNotEquals(legacy.playbackQueueIdentity(), recovered.playbackQueueIdentity())
    }

    @Test
    fun existingRangesAndOrdinaryTracksNeedNoLibraryRead() = runBlocking {
        val stored = session(listOf(first.copy(id = "whole"), second.copy(clipStartMs = 60_000L),
            first.copy(clipEndMs = 60_000L)))
        assertEquals(stored, stored.restorePlaybackSession(null) { error("No missing CUE ranges") })
    }

    @Test
    fun openEndedTailIsRecoveredButMissingAndInvalidLibraryRangesAreNotGuessed() = runBlocking {
        val missing = first.copy(id = "saf:missing#cue:1")
        val invalid = first.copy(id = "saf:invalid#cue:1")
        val stored = session(listOf(first, second, missing, invalid))
        val recovered = stored.restorePlaybackSession(null) {
            listOf(row(first, 0L, 60_000L), row(second, 60_000L, 0L), row(invalid, 60_000L, 30_000L))
        }
        assertEquals(listOf(
            first.copy(clipEndMs = 60_000L), second.copy(clipStartMs = 60_000L), missing, invalid,
        ), recovered.queue)
    }

    @Test
    fun lookupsAreBoundedAndCancellationIsPropagated() = runBlocking {
        val tracks = (1..257).map { first.copy(id = "saf:album#cue:$it") }
        val batches = ArrayList<Int>()
        val recovered = session(tracks).restorePlaybackSession(null) { ids ->
            batches += ids.size
            ids.map { row(first.copy(id = it), 0L, 60_000L) }
        }
        assertEquals(listOf(256, 1), batches)
        assertTrue(recovered.queue.all { it.clipEndMs == 60_000L })

        val cancelled = CancellationException("Restore cancelled")
        try {
            session(listOf(first)).restorePlaybackSession(null) { throw cancelled }
            fail("Cancellation must propagate")
        } catch (error: CancellationException) {
            assertSame(cancelled, error)
        }
    }
}

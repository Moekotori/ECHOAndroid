package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackVersionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackTrackVersionResolverTest {
    private val anchor = EchoTrack("anchor", "file:///music/song.flac", "Song", "Artist", "Album", durationMs = 180_000)

    @Test fun queueWithoutAlternativesDoesNotProbeFiles() {
        var probes = 0
        val queue = (0 until 500).map { anchor.copy(id = "track-$it", title = "Song $it") }
        val resolved = queue.map { track ->
            resolvePlaybackTrackVersion(track, listOf(track), null, emptyMap(), false, false) {
                probes++
                false
            }
        }
        assertEquals(queue, resolved)
        assertEquals(0, probes)
    }

    @Test fun alternativesKeepTheExistingPreferenceAndUnavailableFallbackBehavior() {
        val alternative = anchor.copy(id = "alternative", uri = "file:///music/other.flac")
        val candidates = listOf(anchor, alternative)
        for (preferred in listOf(null, anchor.id, alternative.id)) {
            for (playable in listOf(emptySet(), setOf(anchor.id), setOf(alternative.id), setOf(anchor.id, alternative.id))) {
                assertEquals(
                    EchoTrackVersionPolicy.choose(anchor, candidates, preferred, playable),
                    resolvePlaybackTrackVersion(anchor, candidates, preferred, emptyMap(), false, false) { it.id in playable },
                )
            }
        }
    }

    @Test fun indexedSingletonStillChecksAvailabilityForInvalidation() {
        val checked = mutableListOf<String>()
        val selected = resolvePlaybackTrackVersion(anchor, listOf(anchor), null, emptyMap(), true, true) {
            checked += it.id
            false
        }
        assertSame(anchor, selected)
        assertEquals(listOf(anchor.id), checked)
    }
}

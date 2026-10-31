package app.echo.android.model.library

import org.junit.Assert.*
import org.junit.Test

class EchoDuplicateVersionPolicyTest {
    private val base = EchoTrack("a", "content://audio/a", "Song", "Artist", "Album", durationMs = 180000,
        mimeType = "audio/mpeg", fileName = "Song.mp3")

    @Test fun strictGroupsPreserveEditionsSpeedsAndAvoidTransitiveDurations() {
        val tracks = listOf(base, base.copy(id = "b", durationMs = 181500), base.copy(id = "c", durationMs = 183000))
        assertEquals(listOf(listOf("a", "b")), EchoDuplicateVersionPolicy.clusters(tracks).map { it.map(EchoTrack::id) })
        assertFalse(EchoDuplicateVersionPolicy.matches(base, base.copy(title = "Song (Live)")))
        assertFalse(EchoDuplicateVersionPolicy.matches(base, base.copy(album = "Album (Remastered)")))
        assertFalse(EchoDuplicateVersionPolicy.matches(base, base.copy(fileName = "Song 1.05x.mp3")))
        assertFalse(EchoDuplicateVersionPolicy.matches(base.copy(trackNumber = 1), base.copy(trackNumber = 2)))
        assertFalse(EchoDuplicateVersionPolicy.matches(base, base.copy(clipStartMs = 1000)))
        assertTrue(EchoDuplicateVersionPolicy.clusters(listOf(base.copy(album = null), base.copy(id = "b", album = null))).isEmpty())
        listOf("Song Live.flac", "Song Remix.flac", "Song 现场版.flac", "Song 2x.flac").forEach {
            assertFalse(it, EchoDuplicateVersionPolicy.matches(base, base.copy(fileName = it)))
        }
        assertFalse(EchoDuplicateVersionPolicy.matches(base.copy(fileName = "Song 2011 Remaster.flac"), base.copy(fileName = "Song 2020 Remastered.flac")))
        assertFalse(EchoDuplicateVersionPolicy.matches(base.copy(year = 2011), base.copy(year = 2020)))
        listOf("未知艺术家", "不明なアーティスト", "unknown artist").forEach { artist ->
            assertTrue(EchoDuplicateVersionPolicy.clusters(listOf(base.copy(artist = artist), base.copy(id = "b", artist = artist))).isEmpty())
        }
    }

    @Test fun measuredCodecAndResolutionRankWithoutTreatingRenamedFilesAsLossless() {
        val lossless = base.copy(id = "flac", mimeType = "audio/flac", fileName = "Song.flac")
        val quality = EchoTrackAudioQuality(44100, 24, 1200000, "audio/flac")
        assertTrue(quality.score(lossless) > EchoTrackAudioQuality(48000, 16, 320000, "audio/mpeg").score(base))
        assertTrue(quality.score(lossless) > EchoTrackAudioQuality(96000, 16, 1200000, "audio/flac").score(lossless))
        assertTrue(EchoTrackAudioQuality(codecMime = "audio/alac").score(base.copy(fileName = "Song.m4a")) >
            EchoTrackAudioQuality(codecMime = "audio/mp4a-latm").score(base.copy(fileName = "Song.flac")))
    }

    @Test fun manualPreferenceAndAvailabilityStillTakePriorityOverAutomaticQuality() {
        val best = base.copy(id = "best", mimeType = "audio/flac", fileName = "Song.flac")
        val tracks = listOf(base, best)
        val quality = mapOf(best.id to EchoTrackAudioQuality(96000, 24, 2000000))
        assertEquals(best.id, EchoTrackVersionPolicy.choose(base, tracks, null, setOf(base.id, best.id), quality, true).id)
        assertEquals(base.id, EchoTrackVersionPolicy.choose(base, tracks, base.id, setOf(base.id, best.id), quality, true).id)
        assertEquals(base.id, EchoTrackVersionPolicy.choose(base, tracks, best.id, setOf(base.id), quality, true).id)
        val speed = best.copy(id = "speed", fileName = "Song 1.01x.flac")
        assertEquals(base.id, EchoTrackVersionPolicy.choose(base, listOf(base, speed), null, setOf(base.id, speed.id), quality, true).id)
        assertEquals(base.id, EchoTrackVersionPolicy.choose(base, tracks, null, setOf(base.id, best.id),
            mapOf(base.id to EchoTrackAudioQuality(44100, 16, 320000, "audio/mpeg")), true).id)
        val changed = best.copy(fileName = "Song Live.flac")
        assertEquals(base.id, EchoTrackVersionPolicy.choose(base, listOf(base, changed), changed.id,
            setOf(base.id, changed.id), quality, true).id)
    }
}

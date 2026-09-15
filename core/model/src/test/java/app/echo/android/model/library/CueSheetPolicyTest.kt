package app.echo.android.model.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CueSheetPolicyTest {
    @Test
    fun framesConvertToMilliseconds() {
        assertEquals(0L, CueSheetPolicy.framesToMs(0, 0, 0))
        assertEquals(1000L, CueSheetPolicy.framesToMs(0, 1, 0))
        assertEquals(2000L, CueSheetPolicy.framesToMs(0, 1, 75))
        assertEquals(3 * 60 * 1000L + 12_000L, CueSheetPolicy.framesToMs(3, 12, 0))
    }

    @Test
    fun endTimesUseTheNextIndexThenFileDuration() {
        val tracks = listOf(
            CueSheetTrack(1, "A", startMs = 0L),
            CueSheetTrack(2, "B", startMs = 60_000L),
        )
        val ended = CueSheetPolicy.withEndTimes(tracks, fileDurationMs = 180_000L)
        assertEquals(60_000L, ended[0].endMs)
        assertEquals(180_000L, ended[1].endMs)
    }

    @Test
    fun idsAndPlaybackUrisStayStable() {
        val id = CueSheetPolicy.cueTrackId("saf:doc", 2)
        assertEquals("saf:doc#cue:2", id)
        assertTrue(CueSheetPolicy.isCueTrackId(id))
        assertEquals("saf:doc", CueSheetPolicy.baseTrackId(id))
        val uri = CueSheetPolicy.contentUri("content://tree/doc/file.flac", 2)
        assertEquals("content://tree/doc/file.flac#echo-cue=2", uri)
        assertEquals("content://tree/doc/file.flac", CueSheetPolicy.playbackUri(uri))
    }

    @Test
    fun matchesFileNameOrSingleSameBasename() {
        assertEquals("Album.flac", CueSheetPolicy.matchAudioName("Album.flac", listOf("Album.flac", "other.mp3")))
        assertEquals("Album.flac", CueSheetPolicy.matchAudioName("Album.wav", listOf("Album.flac")))
        assertNull(CueSheetPolicy.matchAudioName("other.flac", listOf("Album.flac", "B.flac")))
    }

    @Test
    fun tracksForAudioUsesPerFileNamesThenTheSheetFile() {
        val sheet = CueSheet(
            fileName = "Album.flac",
            tracks = listOf(
                CueSheetTrack(1, "A", startMs = 0L, fileName = "t1.wav"),
                CueSheetTrack(2, "B", startMs = 0L, fileName = "t2.wav"),
            ),
        )
        assertEquals("A", CueSheetPolicy.tracksForAudio(sheet, "t1.wav").single().title)
        assertEquals("B", CueSheetPolicy.tracksForAudio(sheet, "t2.wav").single().title)
        assertTrue(CueSheetPolicy.tracksForAudio(sheet, "Album.flac").isEmpty())
        val image = CueSheet(
            fileName = "Album.wav",
            tracks = listOf(
                CueSheetTrack(1, "A", startMs = 0L),
                CueSheetTrack(2, "B", startMs = 60_000L),
            ),
        )
        assertEquals(2, CueSheetPolicy.tracksForAudio(image, "Album.flac").size)
    }
}

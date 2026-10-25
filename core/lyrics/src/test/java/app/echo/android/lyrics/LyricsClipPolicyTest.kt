package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.*
import org.junit.Test

class LyricsClipPolicyTest {
    @Test fun trackSpecificImportsCannotOverwriteSharedSourceLyrics() {
        assertFalse(LyricsClipPolicy.canMirrorToWholeFile("track#cue:2", 60_000, 120_000))
        assertFalse(LyricsClipPolicy.canMirrorToWholeFile("track#cue:1", 0, 0))
        assertFalse(LyricsClipPolicy.canMirrorToWholeFile("saved-clip", 1_000, 2_000))
        assertTrue(LyricsClipPolicy.canMirrorToWholeFile("whole-track", 0, 0))
    }
    @Test fun wholeFileLyricsAreRebasedAndExcludeAdjacentTracks() {
        val source = EchoLyrics(lines = listOf(
            EchoLyricLine(20_000, text = "Previous without an end"),
            EchoLyricLine(58_000, 62_000, "Overlapping"),
            EchoLyricLine(65_000, 80_000, "Current"),
            EchoLyricLine(120_000, 130_000, "Next"),
        ))
        val clipped = LyricsClipPolicy.embedded(source, 60_000, 120_000)!!
        assertEquals(listOf("Overlapping", "Current"), clipped.lines.map { it.text })
        assertEquals(listOf(0L, 5_000L), clipped.lines.map { it.startMs })
        assertEquals(listOf(2_000L, 20_000L), clipped.lines.map { it.endMs })
        assertEquals(65_000L, source.lines[2].startMs)
    }

    @Test fun wordTimingsStayInsideClipWithoutLosingTextOrTranslations() {
        val source = EchoLyrics(lines = listOf(EchoLyricLine(58_000, 70_000, "ABC", "译文", words = listOf(
            EchoLyricWord(58_000, 59_000, "A"), EchoLyricWord(59_000, 61_000, "B"), EchoLyricWord(61_000, 69_000, "C"),
        ))))
        val line = LyricsClipPolicy.embedded(source, 60_000, 65_000)!!.lines.single()
        assertEquals("ABC", line.words.joinToString("") { it.text })
        assertEquals("译文", line.translation)
        assertEquals(listOf(0L, 0L, 1_000L), line.words.map { it.startMs })
        assertEquals(listOf(0L, 1_000L, 5_000L), line.words.map { it.endMs })
        assertEquals(5_000L, line.endMs)
    }

    @Test fun onlyWholeFileSidecarsAreRebased() {
        val lyrics = EchoLyricsParser.parse("[00:01.00]Track", "Movement.lrc")
        assertSame(lyrics, LyricsClipPolicy.sidecar(lyrics, "Album.flac", 60_000, 120_000))
        val album = EchoLyricsParser.parse("[01:01.00]Track", "ALBUM.LRC")
        assertEquals(1_000L, LyricsClipPolicy.sidecar(album, "Album.flac", 60_000, 120_000)!!.lines.single().startMs)
        assertSame(lyrics, LyricsClipPolicy.embedded(lyrics, 0, 0))
    }

    @Test fun unassignableOrEmptySourcesAllowFallbackAndUserOffsetIsAppliedOnce() {
        val plain = EchoLyricsParser.parse("Whole album text", "album.txt")
        assertNull(LyricsClipPolicy.embedded(plain, 60_000, 120_000))
        val perTrack = plain.copy(sourceLabel = "Movement.txt")
        assertSame(perTrack, LyricsClipPolicy.sidecar(perTrack, "album.flac", 60_000, 120_000))
        assertNull(LyricsContentPolicy.readable(EchoLyrics(lines = listOf(EchoLyricLine(0, text = "")))))
        assertNull(LyricsClipPolicy.embedded(EchoLyricsParser.parse("[00:01.00]Previous"), 60_000, 120_000))
        val shifted = LyricsClipPolicy.embedded(EchoLyricsParser.parse("[01:01.00]Current\n[offset:250]"), 60_000, 120_000)!!.withUserOffset(100)
        assertEquals(1_350L, shifted.lines.single().startMs)
        assertNull(LyricsClipPolicy.embedded(shifted, 5_000, 4_000))
    }

    @Test fun clipSpecificSidecarsWinWithoutChangingNormalFilePriority() {
        val files = listOf("album.lrc", "Movement.LRC")
        assertEquals(listOf("Movement.LRC", "album.lrc"), LyricsSidecarNames.matching(LyricsSidecarNames.candidates("album.flac", "Movement", preferTitle = true), files))
        assertEquals(listOf("album.lrc", "Movement.LRC"), LyricsSidecarNames.matching(LyricsSidecarNames.candidates("album.flac", "Movement"), files))
    }
}

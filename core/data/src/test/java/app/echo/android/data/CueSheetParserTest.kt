package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueSheetParserTest {
    @Test
    fun parsesAlbumPerformerAndIndex01Times() {
        val cue = CueSheetParser.parseText(
            """
            REM GENRE Jazz
            REM DATE 1999
            PERFORMER "Artist"
            TITLE "Blue Album"
            FILE "Blue Album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Intro"
                PERFORMER "Artist"
                INDEX 00 00:00:00
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Song Two"
                INDEX 01 03:12:00
            """.trimIndent(),
        )!!
        assertEquals("Blue Album.flac", cue.fileName)
        assertEquals("Blue Album", cue.album)
        assertEquals("Artist", cue.performer)
        assertEquals(1999, cue.year)
        assertEquals("Jazz", cue.genre)
        assertEquals(2, cue.tracks.size)
        assertEquals("Intro", cue.tracks[0].title)
        assertEquals(0L, cue.tracks[0].startMs)
        assertEquals("Song Two", cue.tracks[1].title)
        assertEquals(CueSheetPolicy.framesToMs(3, 12, 0), cue.tracks[1].startMs)
    }

    @Test
    fun rejectsASingleTrackWithNoOffsets() {
        assertNull(
            CueSheetParser.parseText(
                """
                FILE "one.wav" WAVE
                  TRACK 01 AUDIO
                    TITLE "Only"
                    INDEX 01 00:00:00
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun decodesLatin1WhenUtf8WouldReplace() {
        val latin = (
            "TITLE \"Cafe\"\nFILE \"a.flac\" WAVE\n  TRACK 01 AUDIO\n    TITLE \"A\"\n    INDEX 01 00:00:00\n  TRACK 02 AUDIO\n    TITLE \"B\"\n    INDEX 01 01:00:00\n"
        ).toByteArray(Charsets.ISO_8859_1).let { bytes ->
            bytes[7] = 0xC9.toByte()
            bytes
        }
        val cue = CueSheetParser.parse(latin)!!
        assertEquals(2, cue.tracks.size)
    }
}

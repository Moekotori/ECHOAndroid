package app.echo.android.lyrics

import org.junit.Assert.*
import org.junit.Test

/** A Bridge response must produce timed display lines even when no raw LRC exists. */
class EchoBridgeLyricsTest {
    @Test fun readsBridgeTimingOffsetWordsTranslationsAndBackgroundVocals() {
        val lyrics = EchoLyricsParser.parse("""{"kind":"synced","offsetMs":-120,"lines":[
            {"timeMs":1250.5,"endMs":2500,"text":"First","translation":"译文","romanization":"First",
             "agentName":"Singer","words":[{"startMs":1250.5,"endMs":2500,"text":"First"}],
             "backgroundVocals":[{"timeMs":1500,"endMs":2000,"text":"Echo"}]},
            {"timeMs":3000,"text":"Second"}]}""", "ECHO Bridge")
        assertTrue(lyrics.isSynced); assertEquals(-120L, lyrics.offsetMs)
        assertEquals(listOf(1251L, 1500L, 3000L), lyrics.lines.map { it.startMs })
        val first = lyrics.lines.first()
        assertEquals(2500L, first.endMs); assertEquals("译文", first.translation)
        assertEquals("First", first.romanization); assertEquals("Singer", first.speaker)
        assertEquals(1251L, first.words.single().startMs); assertEquals(2500L, first.words.single().endMs)
        assertTrue(lyrics.lines[1].isBackground)
    }

    @Test fun plainBridgeLinesRemainUntimedAndExistingLrcStillParses() {
        val plain = EchoLyricsParser.parse("""{"kind":"plain","lines":[{"timeMs":-1,"text":"Plain"}]}""", "ECHO Bridge")
        assertFalse(plain.isSynced); assertEquals("Plain", plain.lines.single().text)
        assertEquals(1250L, EchoLyricsParser.parse("[00:01.250]Legacy", "ECHO Bridge").lines.single().startMs)
    }
}

package app.echo.android.lyrics

import app.echo.android.model.lyrics.*
import org.junit.Assert.*
import org.junit.Test

class LyricsRegressionTest {
    @Test fun offsetCanBeResetAfterCrossingZero() {
        val original = EchoLrcParser.parse("[00:00.10]<00:00.10>Hello")
        assertEquals(0L, original.withUserOffset(-250).lines.single().startMs)
        val reset = original.withUserOffset(0)
        assertEquals(100L, reset.lines.single().startMs)
        assertEquals(100L, reset.lines.single().words.single().startMs)
    }
    @Test fun trailingOffsetAndEmptyInstrumentalLineArePreserved() {
        val lyrics = EchoLrcParser.parse("[00:01.00]Hello\n[00:02.00]\n[offset:250]")
        assertEquals(listOf(1250L, 2250L), lyrics.lines.map { it.startMs })
        assertEquals(2250L, lyrics.lines.first().endMs)
        assertTrue(lyrics.lines.last().text.isEmpty())
    }
    @Test fun repeatedEnhancedLinesMoveWordsToo() {
        val lyrics = EchoLrcParser.parse("[00:01.00][00:05.00]<00:01.00>A<00:02.00>B")
        assertEquals(listOf(5000L, 6000L), lyrics.lines[1].words.map { it.startMs })
    }
    @Test fun krcAlwaysUsesRelativeWordTimes() {
        val lyrics = EchoLyricsParser.parse("[1000,4000]<0,1500,0>A<1500,1500,0>B", "a.krc")
        assertEquals(listOf(1000L, 2500L), lyrics.lines.single().words.map { it.startMs })
    }
    @Test fun qrcKeepsTextBeforeTimestampsAndLiteralParentheses() {
        val lyrics = EchoLyricsParser.parse("[1000,4000]Hello (you)(1000,1500) world!(2500,1500)", "a.qrc")
        assertEquals("Hello (you) world!", lyrics.lines.single().text)
        assertEquals(listOf(1000L, 2500L), lyrics.lines.single().words.map { it.startMs })
    }
    @Test fun ttmlRetainsPunctuationSpacesTranslationAndBackingVocals() {
        val lyrics = EchoLyricsParser.parse("""<tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body>
          <p begin="1s" end="5s" ttm:agent="v1"><span begin="1s" end="2s">Hello</span> <span begin="2s" end="3s">world</span>!<span ttm:role="x-translation">你好</span><span ttm:role="x-roman">ni hao</span><span ttm:role="x-bg" begin="2s" end="4s"><span begin="2s" end="4s">Oh</span></span></p>
        </body></tt>""", "a.ttml")
        val line = lyrics.lines.first()
        assertEquals("Hello world!", line.text)
        assertEquals(line.text, line.words.joinToString("") { it.text })
        assertEquals("你好", line.translation)
        assertEquals("ni hao", line.romanization)
        assertEquals("v1", line.speaker)
        assertEquals(true, lyrics.lines[1].isBackground)
        assertEquals("Oh", lyrics.lines[1].text)
    }
    @Test fun ttmlDoesNotInsertSpacesBetweenChineseWords() {
        val lyrics = EchoLyricsParser.parse("""<tt><body><p begin="1s" end="3s"><span begin="1s">你</span><span begin="2s">好</span>！</p></body></tt>""")
        assertEquals("你好！", lyrics.lines.single().text)
        assertEquals(2000L, lyrics.lines.single().words.first().endMs)
    }
    @Test fun storageRoundTripKeepsAllTimingAndVocalFields() {
        val lyrics = EchoLyrics(listOf(EchoLyricLine(1000, 5000, "Hello", "你好", "ni hao",
            listOf(EchoLyricWord(1000, 2000, "Hello")), "v2", true)), mapOf("provider" to "test"), "test", EchoLyricsFormat.Ttml)
        assertEquals(lyrics, EchoLyricsJson.decode(EchoLyricsJson.encode(lyrics)))
    }
    @Test(expected = IllegalArgumentException::class) fun ttmlRejectsExternalEntities() {
        EchoLyricsParser.parse("""<!DOCTYPE tt [<!ENTITY a SYSTEM "file:///test">]><tt><p begin="1s">&a;</p></tt>""")
    }
    @Test fun readsCompressedKrcFiles() {
        val text = "[1000,2000]<0,1000,0>Hello"
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(output).use { it.write(text.toByteArray()) }
        val payload = output.toByteArray()
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val bytes = "krc1".toByteArray() + ByteArray(payload.size) { (payload[it].toInt() xor key[it % key.size]).toByte() }
        assertEquals(text, EchoLyricsTextDecoder.decode(bytes))
    }
}

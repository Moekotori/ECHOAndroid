package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricsFormat
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLrcParserTest {
    @Test
    fun parsesMultipleTimestampsAndTranslationLines() {
        val lyrics = EchoLrcParser.parse(
            """
            [ti:LiFE Garden]
            [00:01.00][00:02.00]Primary line
            [00:01.00]翻译行
            """.trimIndent(),
            sourceLabel = "test.lrc",
        )

        assertEquals("LiFE Garden", lyrics.metadata["ti"])
        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines[0].startMs)
        assertEquals("Primary line", lyrics.lines[0].text)
        assertEquals("翻译行", lyrics.lines[0].translation)
        assertEquals(2_000L, lyrics.lines[0].endMs)
    }

    @Test
    fun parsesEnhancedWordTiming() {
        val lyrics = EchoLrcParser.parse("[00:03.50]<00:03.50>LiFE <00:03.90>Garden")

        assertEquals(1, lyrics.lines.size)
        assertEquals("LiFE Garden", lyrics.lines[0].text)
        assertEquals(2, lyrics.lines[0].words.size)
        assertEquals(3_500L, lyrics.lines[0].words[0].startMs)
        assertEquals(3_900L, lyrics.lines[0].words[0].endMs)
    }

    @Test
    fun appliesOffset() {
        val lyrics = EchoLrcParser.parse(
            """
            [offset:250]
            [00:01.00]late
            """.trimIndent(),
        )

        assertTrue(lyrics.isSynced)
        assertEquals(1_250L, lyrics.lines.single().startMs)
    }

    @Test
    fun unifiedParserReadsTtmlWords() {
        val lyrics = EchoLyricsParser.parse(
            """
            <tt>
              <body>
                <div>
                  <p begin="00:00:01.000" end="00:00:03.000">
                    <span begin="00:00:01.000" end="00:00:01.500">LiFE</span>
                    <span begin="00:00:01.500" end="00:00:02.000">Garden</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent(),
            sourceLabel = "track.ttml",
        )

        assertEquals(EchoLyricsFormat.Ttml, lyrics.format)
        assertEquals(1_000L, lyrics.lines.single().startMs)
        assertEquals(3_000L, lyrics.lines.single().endMs)
        assertEquals("LiFE Garden", lyrics.lines.single().text)
        assertEquals(2, lyrics.lines.single().words.size)
    }

    @Test
    fun unifiedParserReadsSrt() {
        val lyrics = EchoLyricsParser.parse(
            """
            1
            00:00:01,500 --> 00:00:03,000
            First line

            2
            00:00:04,000 --> 00:00:05,000
            Second line
            """.trimIndent(),
            sourceLabel = "track.srt",
        )

        assertEquals(EchoLyricsFormat.Srt, lyrics.format)
        assertEquals(1_500L, lyrics.lines.first().startMs)
        assertEquals("Second line", lyrics.lines[1].text)
    }

    @Test
    fun unifiedParserReadsWebVtt() {
        val lyrics = EchoLyricsParser.parse(
            """
            WEBVTT

            cue-1
            00:01.000 --> 00:03.500
            <c>First VTT line</c>
            """.trimIndent(),
            sourceLabel = "track.vtt",
        )

        assertEquals(EchoLyricsFormat.Vtt, lyrics.format)
        assertEquals(1_000L, lyrics.lines.single().startMs)
        assertEquals(3_500L, lyrics.lines.single().endMs)
        assertEquals("First VTT line", lyrics.lines.single().text)
    }

    @Test
    fun unifiedParserReadsAssDialogue() {
        val lyrics = EchoLyricsParser.parse(
            """
            [Events]
            Format: Layer, Start, End, Style, Text
            Dialogue: 0,0:00:01.20,0:00:04.30,Default,{\\k20}First\NSecond
            """.trimIndent(),
            sourceLabel = "track.ass",
        )

        assertEquals(EchoLyricsFormat.Ass, lyrics.format)
        assertEquals(1_200L, lyrics.lines.single().startMs)
        assertEquals(4_300L, lyrics.lines.single().endMs)
        assertEquals("First\nSecond", lyrics.lines.single().text)
    }

    @Test
    fun unifiedParserReadsYrcDurationWords() {
        val lyrics = EchoLyricsParser.parse(
            """
            [ar:Yooh]
            [1500,2200](0,500,0)LiFE (500,700,0)Garden
            """.trimIndent(),
            sourceLabel = "track.yrc",
        )

        assertEquals(EchoLyricsFormat.Yrc, lyrics.format)
        assertEquals("Yooh", lyrics.metadata["ar"])
        assertEquals(1_500L, lyrics.lines.single().startMs)
        assertEquals(3_700L, lyrics.lines.single().endMs)
        assertEquals("LiFE Garden", lyrics.lines.single().text)
        assertEquals(1_500L, lyrics.lines.single().words[0].startMs)
        assertEquals(2_000L, lyrics.lines.single().words[0].endMs)
    }

    @Test
    fun unifiedParserReadsPlainQrcDurationLines() {
        val lyrics = EchoLyricsParser.parse(
            "[1000,2000](1000,900)第一句(1900,800)第二句",
            sourceLabel = "track.qrc",
        )

        assertEquals(EchoLyricsFormat.Qrc, lyrics.format)
        assertEquals("第一句第二句", lyrics.lines.single().text)
        assertEquals(1_900L, lyrics.lines.single().words[1].startMs)
    }

    @Test
    fun unifiedParserReadsPlainKrcAngleDurationWords() {
        val lyrics = EchoLyricsParser.parse(
            "[1000,2200]<0,500,0>第一句<500,700,0>第二句",
            sourceLabel = "track.krc",
        )

        assertEquals(EchoLyricsFormat.Krc, lyrics.format)
        assertEquals("第一句第二句", lyrics.lines.single().text)
        assertEquals(1_000L, lyrics.lines.single().words[0].startMs)
        assertEquals(1_500L, lyrics.lines.single().words[0].endMs)
        assertEquals(1_500L, lyrics.lines.single().words[1].startMs)
    }

    @Test
    fun unifiedParserKeepsPlainTextLyrics() {
        val lyrics = EchoLyricsParser.parse(
            """
            Unsynced first
            Unsynced second
            """.trimIndent(),
            sourceLabel = "track.txt",
        )

        assertEquals(EchoLyricsFormat.PlainText, lyrics.format)
        assertEquals(false, lyrics.isSynced)
        assertEquals(2, lyrics.lines.size)
        assertEquals(-1L, lyrics.lines.first().startMs)
    }

    @Test
    fun textDecoderReadsGb18030Lyrics() {
        val text = "[00:01.00]第一句歌词"
        val decoded = EchoLyricsTextDecoder.decode(text.toByteArray(Charset.forName("GB18030")))

        assertEquals(text, decoded)
    }

    @Test
    fun textDecoderReadsUtf16LeBomLyrics() {
        val text = "[00:01.00]第一句歌词"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(Charsets.UTF_16LE)
        val decoded = EchoLyricsTextDecoder.decode(bytes)

        assertEquals(text, decoded)
    }
}

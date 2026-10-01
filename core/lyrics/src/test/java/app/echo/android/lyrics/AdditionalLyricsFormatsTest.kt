package app.echo.android.lyrics

import app.echo.android.model.lyrics.*
import org.junit.Assert.*
import org.junit.Test

class AdditionalLyricsFormatsTest {
    @Test fun readsSbvMultilineCuesAndEntities() {
        val lyrics = EchoLyricsParser.parse("0:00:01.250,0:00:03.500\nHello &amp; world\n第二行\n\n0:00:04.000,0:00:05.000\nNext", "track.sbv")
        assertEquals(EchoLyricsFormat.Sbv, lyrics.format)
        assertEquals(1250L, lyrics.lines.first().startMs)
        assertEquals(3500L, lyrics.lines.first().endMs)
        assertEquals("Hello & world\n第二行", lyrics.lines.first().text)
        assertEquals(2, lyrics.lines.size)
    }

    @Test fun samiKeepsLanguageAndEndsAtEmptyCaptionMarkers() {
        val lyrics = EchoLyricsParser.parse("""<SAMI><BODY>
            <SYNC Start=1000><P Class=ENCC>Hello<br>world<P Class=ZHCC>&#x4F60;&#22909;
            <SYNC Start="3000"><P Class=ENCC>&nbsp;<P Class=ZHCC>只有翻译
            <SYNC Start=4000><P Class=ENCC>Next<P Class=ZHCC>下一句
            <SYNC Start=5000><P Class=ENCC>&nbsp;</BODY></SAMI>""", "track.smi")
        assertEquals(EchoLyricsFormat.Sami, lyrics.format)
        assertEquals(listOf(1000L, 4000L), lyrics.lines.map { it.startMs })
        assertEquals(listOf(3000L, 5000L), lyrics.lines.map { it.endMs })
        assertEquals("Hello\nworld", lyrics.lines.first().text)
        assertEquals("你好", lyrics.lines.first().translation)
    }

    @Test fun assKeepsKaraokeDurationsStyleChangesAndSpeaker() {
        val lyrics = EchoLyricsParser.parse("""[Events]
            Format: Layer, Start, End, Style, Name, Text
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,Singer,{\k50}你{\b1}{\kf100}好{\kt200\ko50}！
            """.trimIndent(), "track.ass")
        val line = lyrics.lines.single()
        assertEquals("你好！", line.text)
        assertEquals("Singer", line.speaker)
        assertEquals(listOf(1000L, 1500L, 3000L), line.words.map { it.startMs })
        assertEquals(listOf(1500L, 2500L, 3500L), line.words.map { it.endMs })
    }

    @Test fun vttPreservesInlineTimeAndIgnoresCommentAndStyleBlocks() {
        val lyrics = EchoLyricsParser.parse("""WEBVTT

            NOTE comment
            00:00.000 --> 00:01.000
            Never a lyric

            STYLE
            ::cue { color: white }

            cue-1
            00:01.000 --> 00:04.000 align:start
            <v Singer>Hello <00:02.000><c.green>world</c>!</v>
            """.trimIndent(), "track.webvtt")
        val line = lyrics.lines.single()
        assertEquals("Hello world!", line.text)
        assertEquals("Singer", line.speaker)
        assertEquals(listOf(1000L, 2000L), line.words.map { it.startMs })
        assertEquals(listOf(2000L, 4000L), line.words.map { it.endMs })
        assertEquals(line.text, line.words.joinToString("") { it.text })
    }

    @Test fun jsonKeepsWordTimesAndTranslationAndSortsLineTimes() {
        val lyrics = EchoLyricsParser.parse("""{"lines":[
            {"startTimeMs":"3000","words":"Next"},
            {"startMs":1000,"text":"你好","translation":"Hello","romanization":"ni hao",
             "words":[{"startMs":1000,"endMs":1500,"text":"你"},{"startMs":1500,"endMs":2500,"text":"好"}]}]}""", "track.json")
        assertEquals(EchoLyricsFormat.Json, lyrics.format)
        val line = lyrics.lines.first()
        assertEquals(3000L, line.endMs)
        assertEquals("Hello", line.translation)
        assertEquals("ni hao", line.romanization)
        assertEquals(listOf(1000L, 1500L), line.words.map { it.startMs })
        assertEquals(lyrics, EchoLyricsJson.decode(EchoLyricsJson.encode(lyrics)))
        assertEquals(1000L, EchoLyricsParser.parse("""[{"startMs":1000,"text":"Hello"}]""", "array.json").lines.single().startMs)
    }

    @Test fun jsonProviderEnvelopesPreferWordTimingAndKeepAuxiliaryLyrics() {
        val lyrics = EchoLyricsParser.parse("""{"lrc":{"lyric":"[00:01.00]你好"},
            "yrc":{"lyric":"[1000,2000](1000,1000,0)你(2000,1000,0)好"},
            "tlyric":{"lyric":"[00:01.00]Hello"},"romalrc":{"lyric":"[00:01.00]ni hao"}}""", "provider.json")
        assertEquals(EchoLyricsFormat.Yrc, lyrics.format)
        assertEquals(2, lyrics.lines.single().words.size)
        assertEquals("Hello", lyrics.lines.single().translation)
        assertEquals("ni hao", lyrics.lines.single().romanization)
        assertEquals("provider.json", lyrics.sourceLabel)
        assertEquals("Hello", EchoLyricsParser.parse("""{"syncedLyrics":"[00:01.00]Hello"}""", "lrclib.json").lines.single().text)
    }

    @Test fun xmlAndDfxpAliasesAndQrcXmlUseTheActualContent() {
        val ttml = "<tt><body><p begin=\"1s\" end=\"3s\">Hello</p></body></tt>"
        assertEquals(EchoLyricsFormat.Ttml, EchoLyricsParser.parse(ttml, "track.xml").format)
        assertEquals(EchoLyricsFormat.Ttml, EchoLyricsParser.parse(ttml, "track.dfxp").format)
        val qrc = EchoLyricsParser.parse("""<QrcInfos><LyricInfo LyricContent="[1000,2000]你(1000,1000)好(2000,1000)"/></QrcInfos>""", "track.xml")
        assertEquals(EchoLyricsFormat.Qrc, qrc.format)
        assertEquals("你好", qrc.lines.single().text)
        assertEquals(2, qrc.lines.single().words.size)
        assertTrue(EchoLyricsParser.fileExtensions.containsAll(listOf(".smi", ".sami", ".sbv", ".json", ".dfxp", ".xml")))
    }

    @Test fun echoJsonExportPreservesOriginalFormatAndInvalidDocumentsAreRejected() {
        val original = EchoLyricsParser.parse("[00:01.00]Hello", "original.lrc")
        val imported = EchoLyricsParser.parse(EchoLyricsJson.encode(original), "export.json")
        assertEquals(original.copy(sourceLabel = "export.json"), imported)
        assertTrue(runCatching { EchoLyricsParser.parse("{}", "track.json") }.isFailure)
        assertTrue(runCatching { EchoLyricsParser.parse("<settings>not lyrics</settings>", "track.xml") }.isFailure)
    }

    @Test fun invalidInlineWordTimesKeepReadableSubtitleText() {
        val line = EchoLyricsParser.parse("WEBVTT\n\n00:01.000 --> 00:04.000\nA<00:03.000>B<00:02.000>C", "bad.vtt").lines.single()
        assertEquals("ABC", line.text)
        assertTrue(line.words.isEmpty())
        val ass = EchoLyricsParser.parse("[Events]\nFormat: Start, End, Text\nDialogue: 0:00:01.00,0:00:02.00,{\\k500}Hello", "short.ass").lines.single()
        assertEquals(2000L, ass.words.single().endMs)
    }
}

package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricsFormat
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class PcLyricsFormatCompatibilityTest {
    @Test fun awlrcContainerKeepsWordTimingTranslationAndRomanization() {
        fun encode(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
        val text = "[awlrc:awlrc:${encode("[00:01.250]<0,500,0>Hello <500,1000,0>world")}," +
            "tlrc:${encode("[00:01.250]你好世界")},rlrc:${encode("[00:01.250]hello world")}]"
        val lyrics = EchoLyricsParser.parse(text, "song.lrc")
        val line = lyrics.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals("你好世界", line.translation)
        assertEquals("hello world", line.romanization)
        assertEquals(listOf(1250L, 1750L), line.words.map { it.startMs })
        assertEquals(2750L, line.endMs)
    }

    @Test fun trcPreservesDurationsOffsetAndSidecarDiscovery() {
        val lyrics = EchoLyricsParser.parse("[offset:250]\n[00:01.25]<500>Hello <1000>world", "song.trc")
        val line = lyrics.lines.single()
        assertEquals(EchoLyricsFormat.Trc, lyrics.format)
        assertEquals("Hello world", line.text)
        assertEquals(1000L, line.startMs)
        assertEquals(listOf(1000L, 1500L), line.words.map { it.startMs })
        assertEquals(2500L, line.endMs)
        assertTrue(EchoLyricsParser.fileExtensions.containsAll(listOf(".trc", ".snc")))
        assertEquals(lyrics, EchoLyricsJson.decode(EchoLyricsJson.encode(lyrics)))
        assertTrue(EchoLyricsParser.parse("[00:99.00]<500>Bad", "bad.trc").lines.isEmpty())
    }

    @Test fun originalSncBytesAndContinuationLinesRemainReadable() {
        val bytes = byteArrayOf(0xa2.toByte(), 0xe2.toByte()) + "00000125".toByteArray() +
            byteArrayOf(0xa2.toByte(), 0xd0.toByte()) + "Hello\nworld\n⑩00006000⑿Bad".toByteArray(charset("GB18030"))
        val lyrics = EchoLyricsParser.parse(EchoLyricsTextDecoder.decode(bytes)!!, "song.snc")
        assertEquals(EchoLyricsFormat.Snc, lyrics.format)
        assertEquals("Hello world", lyrics.lines.single().text)
        assertEquals(1250L, lyrics.lines.single().startMs)
    }

    @Test fun lrcxAttachmentsDoNotBecomeVisibleLinesAndKeepCharacterIndexedTiming() {
        val text = "[00:01.25]Hello world\n[00:01.25][tt]<0,0><500,6><1500,11>\n" +
            "[00:01.25][tr:zh-Hans]你好世界\n[00:01.25][ro]<hello,0,5>"
        val lyrics = EchoLyricsParser.parse(text, "song.lrcx")
        val line = lyrics.lines.single()
        assertEquals(EchoLyricsFormat.Lrcx, lyrics.format)
        assertEquals("Hello world", line.text)
        assertEquals("你好世界", line.translation)
        assertEquals("hello world", line.romanization)
        assertEquals(listOf(1250L, 1750L), line.words.map { it.startMs })
        assertEquals(2750L, line.words.last().endMs)
        assertEquals(lyrics.copy(sourceLabel = null), EchoLyricsParser.parse(text))
        val unicode = EchoLyricsParser.parse("[00:01.00]🎵好\n[00:01.00][tt]<0,0><500,1><1000,2>", "unicode.lrcx").lines.single()
        assertEquals(listOf("🎵", "好"), unicode.words.map { it.text })
        val malformed = EchoLyricsParser.parse("[00:01.00]Hello\n[00:01.00][tt]<100,3><0,1>", "bad.lrcx").lines.single()
        assertEquals("Hello", malformed.text)
        assertTrue(malformed.words.isEmpty())
    }

    @Test fun krcLanguageTracksSurviveCompressionAndUnlabelledParsing() {
        val language = """{"content":[{"type":1,"lyricContent":[["你好世界"]]},{"type":0,"lyricContent":[["hello ","world"]]}]}"""
        val text = "[language:${Base64.getEncoder().encodeToString(language.toByteArray())}]\n[1250,1500]<0,500,0>Hello <500,1000,0>world"
        val compressed = java.io.ByteArrayOutputStream().also { output ->
            java.util.zip.DeflaterOutputStream(output).use { it.write(text.toByteArray()) }
        }.toByteArray()
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val bytes = "krc1".toByteArray() + ByteArray(compressed.size) { (compressed[it].toInt() xor key[it % 16]).toByte() }
        val lyrics = EchoLyricsParser.parse(EchoLyricsTextDecoder.decode(bytes)!!)
        assertEquals(EchoLyricsFormat.Krc, lyrics.format)
        assertEquals("你好世界", lyrics.lines.single().translation)
        assertEquals("hello world", lyrics.lines.single().romanization)
        assertEquals(listOf(1250L, 1750L), lyrics.lines.single().words.map { it.startMs })
        assertEquals("Hello", EchoLyricsParser.parse("[language:not base64]\n[1000,1000]<0,1000,0>Hello", "bad.krc").lines.single().text)
    }

    @Test fun qrcUsesAbsoluteOrRelativeTimingAndSingleQuotedXml() {
        for (body in listOf("你(0,500)好(500,500)", "你(5000,500)好(5500,500)")) {
            val lyrics = EchoLyricsParser.parse("<QrcInfos><LyricInfo LyricContent='[5000,1000]$body'/></QrcInfos>", "song.xml")
            assertEquals("你好", lyrics.lines.single().text)
            assertEquals(listOf(5000L, 5500L), lyrics.lines.single().words.map { it.startMs })
        }
        for (clock in listOf("00:00:04,000 --> 00:00:03,000", "00:99:01,000 --> 00:99:03,000"))
            assertTrue(EchoLyricsParser.parse("1\n$clock\nBad", "bad.srt").lines.isEmpty())
        val ass = EchoLyricsParser.parse("[Events]\nFormat: Start, End, Text\nDialogue: 0:00:01.00,0:00:03.00,{\\p1}m 0 0 l 50 50{\\p0}{\\k100}Hello", "song.ass")
        assertEquals("Hello", ass.lines.single().text)
        assertEquals("Hello", ass.lines.single().words.single().text)
    }
}

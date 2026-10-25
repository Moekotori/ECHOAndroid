package app.echo.android.lyrics

import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class LyricsCharsetRegressionTest {
    @Test fun explicitEncodingResolvesAmbiguousShortTextAndStripsBom() {
        val bytes = "안녕".toByteArray(Charset.forName("EUC-KR"))
        val selected = EchoLyricsTextDecoder.decodeResult(bytes, "EUC-KR")!!
        assertEquals("안녕", selected.text)
        assertEquals("EUC-KR", selected.charset)
        assertFalse(selected.lossy)
        assertEquals("Hello", EchoLyricsTextDecoder.decodeResult(byteArrayOf(-17, -69, -65) + "Hello".toByteArray(), "UTF-8")?.text)
        assertNull(EchoLyricsTextDecoder.decodeResult(byteArrayOf(0xe9.toByte()), "UTF-8"))
        assertNull(EchoLyricsTextDecoder.decodeResult(bytes, "unknown-charset"))
    }
    @Test fun legacyLyricsDoNotBecomeOtherLanguages() {
        val samples = listOf(
            "GB18030" to "青花瓷 爱音乐 今天我们一起听歌",
            "GB18030" to "六兆年と一夜物語 君の声が聞こえる",
            "GB18030" to "𠮷野家的音乐 🎵",
            "Big5" to "繁體中文 愛音樂 今天我們一起聽歌",
            "Shift_JIS" to "夜に駆ける 君の声が聞こえる カタオモイ",
            "EUC-KR" to "사랑 노래 오늘 우리 함께 하늘 바다",
            "windows-1252" to "Don’t Stop — Café déjà vu",
            "windows-1252" to "Björk",
            "windows-1252" to "Hélène",
        )
        val mismatches = samples.mapNotNull { (encoding, body) ->
            val text = "[00:01.00]$body\n[00:04.00]$body"
            val decoded = EchoLyricsTextDecoder.decodeResult(text.toByteArray(Charset.forName(encoding)))
            if (decoded?.text == text) null else "$encoding became ${decoded?.charset}: ${decoded?.text}"
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test fun shortBomlessUtf16IsNotAcceptedAsUtf8WithNullCharacters() {
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            for (text in listOf("A", "AB", "[00:01.00]你好 🎵")) {
                assertEquals("$charset / $text", text, EchoLyricsTextDecoder.decode(text.toByteArray(charset)))
            }
        }
    }

    @Test fun damagedDeclaredUnicodeIsNotReinterpretedAsChinese() {
        assertNull(EchoLyricsTextDecoder.decodeResult(byteArrayOf(-17, -69, -65, -23)))
        assertNull(EchoLyricsTextDecoder.decodeResult(byteArrayOf(-1, -2, 65)))
        assertNull(EchoLyricsTextDecoder.decodeResult(byteArrayOf(-17, -69, -65) + (" ".repeat(5000) + "\u0000").toByteArray()))
    }
}

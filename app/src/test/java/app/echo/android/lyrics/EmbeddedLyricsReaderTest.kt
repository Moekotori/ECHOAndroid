package app.echo.android.lyrics

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedLyricsReaderTest {
    @Test
    fun legacyId3LyricsKeepChineseJapaneseKoreanAndAccents() {
        val samples = listOf(
            "GB18030" to "青花瓷 今天我们一起听歌",
            "Big5" to "繁體中文 今天我們一起聽歌",
            "Shift_JIS" to "夜に駆ける 君の声が聞こえる",
            "EUC-KR" to "사랑 노래 오늘 우리 함께 하늘 바다",
            "windows-1252" to "Don’t Stop — Café déjà vu",
        )
        for ((encoding, body) in samples) {
            val text = "[00:01.00]$body"
            val payload = byteArrayOf(0) + "eng".toByteArray() + byteArrayOf(0) + text.toByteArray(java.nio.charset.Charset.forName(encoding))
            val frame = "USLT".toByteArray() + syncSafe(payload.size) + byteArrayOf(0, 0) + payload
            val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncSafe(frame.size) + frame
            assertEquals(encoding, text, EmbeddedLyricsReader.read(tag.inputStream())?.text)
        }
    }
    @Test
    fun malformedDeclaredUnicodeIsNotDisplayedAsReplacementCharacters() {
        fun tag(id: String, payload: ByteArray): ByteArray {
            val frame = id.toByteArray() + syncSafe(payload.size) + byteArrayOf(0, 0) + payload
            return "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncSafe(frame.size) + frame
        }
        val broken = "[00:01.00]".toByteArray() + byteArrayOf(0xe9.toByte())
        val uslt = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + broken
        assertEquals(null, EmbeddedLyricsReader.read(tag("USLT", uslt).inputStream()))
        val sylt = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(2, 1, 0) +
            byteArrayOf(0xe9.toByte(), 0) + ByteBuffer.allocate(4).putInt(1000).array()
        assertEquals(null, EmbeddedLyricsReader.read(tag("SYLT", sylt).inputStream()))
    }
    @Test
    fun timestampOnlyEmbeddedLyricsDoNotBlockOtherSources() {
        assertEquals(null, EmbeddedLyricsReader.parse(EmbeddedLyricsText("[ti:Song]\n[00:01.00]", "Embedded tags")))
        assertEquals("Body", EmbeddedLyricsReader.parse(EmbeddedLyricsText("[00:01.00]Body", "Embedded tags"))?.lines?.single()?.text)
    }
    private val text = "[00:01.00]保存后的歌词"

    @Test
    fun readsMp3LyricsWrittenInBothId3Versions() {
        for (version in listOf(3, 4)) {
            assertEquals(text, EmbeddedLyricsReader.read((id3(version) + ByteArray(32)).inputStream())?.text)
        }
    }

    @Test
    fun readsHeaderWhenProviderReturnsShortReads() {
        val bytes = id3(4)
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(2, len))
        }
        assertEquals(text, EmbeddedLyricsReader.read(input)?.text)
    }

    @Test
    fun sharesLegacyAndExtendedId3SupportWithTheEditor() {
        val payload = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + text.toByteArray()
        val oldFrame = "ULT".toByteArray() + byteArrayOf(0, 0, payload.size.toByte()) + payload
        val oldTag = "ID3".toByteArray() + byteArrayOf(2, 0, 0) + syncSafe(oldFrame.size) + oldFrame
        assertEquals(text, EmbeddedLyricsReader.read(oldTag.inputStream())?.text)

        val ordinary = id3(3)
        val extension = ByteBuffer.allocate(4).putInt(6).array() + ByteArray(6)
        val body = extension + ordinary.copyOfRange(10, ordinary.size)
        val extended = "ID3".toByteArray() + byteArrayOf(3, 0, 64) + syncSafe(body.size) + body
        assertEquals(text, EmbeddedLyricsReader.read(extended.inputStream())?.text)
    }

    @Test
    fun unifiedReaderRetainsSyncedLyricsAndUsesAsciiTimestamps() {
        val payload = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(2, 1, 0) +
            "同步歌词".toByteArray() + byteArrayOf(0) + ByteBuffer.allocate(4).putInt(1000).array()
        val frame = "SYLT".toByteArray() + syncSafe(payload.size) + byteArrayOf(0, 0) + payload
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncSafe(frame.size) + frame
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            assertEquals("[00:01.00]同步歌词", EmbeddedLyricsReader.read(tag.inputStream())?.text)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun keepsWavEmbeddedId3Readable() {
        val tag = id3(3)
        val payload = "WAVEid3 ".toByteArray() + littleEndian(tag.size) + tag +
            if (tag.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
        val wav = "RIFF".toByteArray() + littleEndian(payload.size) + payload
        assertEquals(text, EmbeddedLyricsReader.read(wav.inputStream())?.text)
    }

    @Test
    fun readsM4aLyricsThroughLibraryTagReader() {
        fun atom(type: String, payload: ByteArray): ByteArray =
            ByteBuffer.allocate(4).putInt(payload.size + 8).array() + type.toByteArray(Charsets.ISO_8859_1) + payload
        val data = atom("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + text.toByteArray())
        val item = atom("©lyr", data)
        val metadata = atom("meta", ByteArray(4) + atom("ilst", item))
        val file = atom("ftyp", "M4A ".toByteArray() + ByteArray(4)) + atom("moov", atom("udta", metadata))
        assertEquals(text, EmbeddedLyricsReader.read(file.inputStream())?.text)
    }

    @Test
    fun readsFlacAndOggCommentLyrics() {
        val comment = "LYRICS=$text".toByteArray()
        val comments = littleEndian(0) + littleEndian(1) + littleEndian(comment.size) + comment
        val flac = "fLaC".toByteArray() + byteArrayOf(0x84.toByte(), 0, 0, comments.size.toByte()) + comments
        assertEquals(text, EmbeddedLyricsReader.read(flac.inputStream())?.text)

        fun page(packet: ByteArray, sequence: Int): ByteArray {
            val header = ByteArray(27)
            "OggS".toByteArray().copyInto(header)
            header[18] = sequence.toByte()
            header[26] = 1
            return header + byteArrayOf(packet.size.toByte()) + packet
        }
        val ogg = page("OpusHead".toByteArray(), 0) + page("OpusTags".toByteArray() + comments, 1)
        assertEquals(text, EmbeddedLyricsReader.read(ogg.inputStream())?.text)
    }

    private fun id3(version: Int): ByteArray {
        val payload = if (version == 4) {
            byteArrayOf(3) + "xxx".toByteArray() + byteArrayOf(0) + text.toByteArray()
        } else {
            byteArrayOf(1) + "xxx".toByteArray() +
                byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0, 0xFF.toByte(), 0xFE.toByte()) +
                text.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        }
        val size = if (version == 4) syncSafe(payload.size) else ByteBuffer.allocate(4).putInt(payload.size).array()
        val frame = "USLT".toByteArray() + size + byteArrayOf(0, 0) + payload
        return "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) + syncSafe(frame.size) + frame
    }

    private fun syncSafe(value: Int): ByteArray =
        byteArrayOf((value shr 21 and 127).toByte(), (value shr 14 and 127).toByte(),
            (value shr 7 and 127).toByte(), (value and 127).toByte())

    private fun littleEndian(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}

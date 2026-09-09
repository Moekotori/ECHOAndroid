package app.echo.android.data

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WavTagReaderTest {
    @Test
    fun readsUtf8InfoTags() {
        val wav = wavWithInfo(
            "INAM" to "青花瓷".toByteArray(StandardCharsets.UTF_8),
            "IART" to "周杰伦".toByteArray(StandardCharsets.UTF_8),
            "IPRD" to "我很忙".toByteArray(StandardCharsets.UTF_8),
            "ITRK" to "3".toByteArray(StandardCharsets.US_ASCII),
            "ICRD" to "2007".toByteArray(StandardCharsets.US_ASCII),
        )

        val tags = readLocalAudioTags(ByteArrayInputStream(wav))!!

        assertEquals("青花瓷", tags.title)
        assertEquals("周杰伦", tags.artist)
        assertEquals("我很忙", tags.album)
        assertEquals(3, tags.trackNumber)
        assertEquals(2007, tags.year)
    }

    @Test
    fun latinInfoIsNotDecodedAsGbk() {
        val wav = wavWithInfo(
            "INAM" to "Hélène".toByteArray(StandardCharsets.ISO_8859_1),
            "IART" to "Roch Voisine".toByteArray(StandardCharsets.US_ASCII),
        )

        val tags = readLocalAudioTags(ByteArrayInputStream(wav))!!

        assertEquals("Hélène", tags.title)
        assertEquals("Roch Voisine", tags.artist)
    }

    @Test
    fun readsGbkInfoTags() {
        val gbk = Charset.forName("GBK")
        val wav = wavWithInfo(
            "INAM" to "青花瓷".toByteArray(gbk),
            "IART" to "周杰伦".toByteArray(gbk),
        )

        val tags = readLocalAudioTags(ByteArrayInputStream(wav))!!

        assertEquals("青花瓷", tags.title)
        assertEquals("周杰伦", tags.artist)
    }

    @Test
    fun skipsPcmToReadInfoAfterData() {
        val wav = wavWithInfo(
            mapOf("INAM" to "After Data".toByteArray(StandardCharsets.UTF_8)),
            dataSize = 64,
            infoAfterData = true,
        )

        val tags = readLocalAudioTags(ByteArrayInputStream(wav))!!

        assertEquals("After Data", tags.title)
    }

    @Test
    fun readsId3ChunkUtf8() {
        val wav = wavWithId3Chunk(
            title = "等你下课",
            artist = "周杰伦",
            encoding = 3,
            charset = StandardCharsets.UTF_8,
        )

        val tags = readLocalAudioTags(ByteArrayInputStream(wav))!!

        assertEquals("等你下课", tags.title)
        assertEquals("周杰伦", tags.artist)
    }

    @Test
    fun readsId3EncodingZeroAsGbk() {
        val gbk = Charset.forName("GBK")
        val wav = wavWithId3Chunk(
            title = "等你下课",
            artist = "周杰伦",
            encoding = 0,
            charset = gbk,
        )

        val tags = readLocalAudioTags(ByteArrayInputStream(wav))!!

        assertEquals("等你下课", tags.title)
        assertEquals("周杰伦", tags.artist)
    }

    @Test
    fun prependedId3WinsOverInfo() {
        val info = wavWithInfo("INAM" to "INFO Title".toByteArray(StandardCharsets.UTF_8))
        val id3 = id3Tag(
            title = "ID3 Title",
            artist = "ID3 Artist",
            encoding = 3,
            charset = StandardCharsets.UTF_8,
        )

        val tags = readLocalAudioTags(ByteArrayInputStream(id3 + info))!!

        assertEquals("ID3 Title", tags.title)
        assertEquals("ID3 Artist", tags.artist)
    }

    @Test
    fun wavWithoutTagsReturnsNull() {
        assertNull(readLocalAudioTags(ByteArrayInputStream(silentWav(16))))
    }

    @Test
    fun overlayKeepsFilenameWhenTagsMissing() {
        val track = LibraryTrackEntity(
            id = "saf:1",
            contentUri = "content://doc/1",
            title = "folder-name",
            artist = "Unknown artist",
            album = null,
            albumArtist = null,
            artworkUri = null,
            durationMs = 1000L,
            trackNumber = null,
            discNumber = null,
            year = null,
            mimeType = "audio/wav",
            sizeBytes = 1024L,
            dateModifiedSeconds = 1L,
        )

        assertEquals("folder-name", track.withAudioTags(null).title)
    }

    @Test
    fun overlayPrefersDecodedTags() {
        val track = LibraryTrackEntity(
            id = "mediastore:1",
            contentUri = "content://media/1",
            title = "ÖÐÎÄ",
            artist = "Unknown artist",
            album = null,
            albumArtist = null,
            artworkUri = null,
            durationMs = 1000L,
            trackNumber = null,
            discNumber = null,
            year = null,
            mimeType = "audio/wav",
            sizeBytes = 1024L,
            dateModifiedSeconds = 1L,
        )
        val tagged = track.withAudioTags(
            AudioTagFields("青花瓷", "周杰伦", "我很忙", null, 1, null, 2007),
        )

        assertEquals("青花瓷", tagged.title)
        assertEquals("周杰伦", tagged.artist)
        assertEquals("我很忙", tagged.album)
        assertEquals(1, tagged.trackNumber)
        assertEquals(2007, tagged.year)
    }

    @Test
    fun detectsWavMimeAndFilename() {
        assertEquals(true, LibraryWavTagPolicy.isWavContainer("audio/x-wav", null))
        assertEquals(true, LibraryWavTagPolicy.isWavContainer("audio/mpeg", "song.wav"))
        assertEquals(false, LibraryWavTagPolicy.isWavContainer("audio/mpeg", "song.mp3"))
    }

    private fun wavWithInfo(vararg fields: Pair<String, ByteArray>): ByteArray =
        wavWithInfo(fields.toMap(), dataSize = 16, infoAfterData = false)

    private fun wavWithInfo(
        fields: Map<String, ByteArray>,
        dataSize: Int,
        infoAfterData: Boolean = false,
    ): ByteArray {
        val info = infoList(fields)
        val fmt = fmtChunk()
        val data = chunk("data", ByteArray(dataSize))
        val body = if (infoAfterData) fmt + data + info else fmt + info + data
        return riff(body)
    }

    private fun wavWithId3Chunk(
        title: String,
        artist: String,
        encoding: Int,
        charset: Charset,
    ): ByteArray {
        val id3 = chunk("id3 ", id3Tag(title, artist, encoding, charset))
        return riff(fmtChunk() + id3 + chunk("data", ByteArray(16)))
    }

    private fun silentWav(dataSize: Int): ByteArray = riff(fmtChunk() + chunk("data", ByteArray(dataSize)))

    private fun riff(body: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(12 + body.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(4 + body.size)
        buffer.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
        buffer.put(body)
        return buffer.array()
    }

    private fun fmtChunk(): ByteArray {
        val fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        fmt.putShort(1)
        fmt.putShort(1)
        fmt.putInt(8000)
        fmt.putInt(16000)
        fmt.putShort(2)
        fmt.putShort(16)
        return chunk("fmt ", fmt.array())
    }

    private fun infoList(fields: Map<String, ByteArray>): ByteArray {
        val payload = ArrayList<Byte>()
        "INFO".toByteArray(StandardCharsets.US_ASCII).forEach { payload += it }
        fields.forEach { (id, value) ->
            val terminated = if (value.lastOrNull() == 0.toByte()) value else value + 0
            payload += chunk(id, terminated).toList()
        }
        return chunk("LIST", payload.toByteArray())
    }

    private fun chunk(id: String, payload: ByteArray): ByteArray {
        val paddedSize = payload.size + (payload.size and 1)
        val buffer = ByteBuffer.allocate(8 + paddedSize).order(ByteOrder.LITTLE_ENDIAN)
        val name = id.padEnd(4, ' ').take(4).toByteArray(StandardCharsets.US_ASCII)
        buffer.put(name)
        buffer.putInt(payload.size)
        buffer.put(payload)
        if (payload.size % 2 == 1) buffer.put(0)
        return buffer.array()
    }

    private fun id3Tag(
        title: String,
        artist: String,
        encoding: Int,
        charset: Charset,
    ): ByteArray {
        fun frame(id: String, text: String): ByteArray {
            val encoded = if (encoding == 0 || encoding == 3) {
                byteArrayOf(encoding.toByte()) + text.toByteArray(charset) + 0
            } else {
                byteArrayOf(encoding.toByte()) + text.toByteArray(charset) + byteArrayOf(0, 0)
            }
            val buffer = ByteBuffer.allocate(10 + encoded.size)
            buffer.put(id.toByteArray(StandardCharsets.US_ASCII))
            buffer.putInt(encoded.size)
            buffer.putShort(0)
            buffer.put(encoded)
            return buffer.array()
        }
        val body = frame("TIT2", title) + frame("TPE1", artist)
        val header = ByteBuffer.allocate(10)
        header.put("ID3".toByteArray(StandardCharsets.US_ASCII))
        header.put(3)
        header.put(0)
        header.put(0)
        val size = body.size
        header.put(
            byteArrayOf(
                ((size shr 21) and 0x7F).toByte(),
                ((size shr 14) and 0x7F).toByte(),
                ((size shr 7) and 0x7F).toByte(),
                (size and 0x7F).toByte(),
            ),
        )
        return header.array() + body
    }
}

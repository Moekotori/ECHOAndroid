package app.echo.android.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileTagRewriterTest {
    @Test
    fun mp3WithoutId3PrependsUtf8Tags() {
        val source = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00, 1, 2, 3, 4)
        val rewritten = rewrite(source, SAMPLE_FIELDS, "audio/mpeg")
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("夜曲", fields?.title)
        assertEquals("周杰伦", fields?.artist)
        assertEquals("十一月的萧邦", fields?.album)
        assertEquals("Jay Chou", fields?.albumArtist)
        assertEquals(7, fields?.trackNumber)
        assertEquals(1, fields?.discNumber)
        assertEquals(2005, fields?.year)
        assertTrue(rewritten.copyOfRange(rewritten.size - source.size, rewritten.size).contentEquals(source))
    }

    @Test
    fun id3v24RoundTripReplacesTitleAndKeepsArtworkFrame() {
        val artwork = Id3FrameBytes("APIC", byteArrayOf(0, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 3, 0))
        val original = id3File(
            version = 4,
            frames = listOf(
                textFrameV24("TIT2", "Old Title"),
                textFrameV24("TPE1", "Old Artist"),
                artwork,
            ),
            audio = FAKE_MP3,
        )
        val rewritten = rewrite(original, SAMPLE_FIELDS, "audio/mpeg")
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("夜曲", fields?.title)
        assertEquals("周杰伦", fields?.artist)
        assertTrue("APIC frame should be preserved", rewritten.asString().contains("APIC"))
        assertTrue(rewritten.copyOfRange(rewritten.size - FAKE_MP3.size, rewritten.size).contentEquals(FAKE_MP3))
    }

    @Test
    fun clearingAlbumRemovesTalb() {
        val original = id3File(
            version = 4,
            frames = listOf(
                textFrameV24("TIT2", "Keep"),
                textFrameV24("TPE1", "Band"),
                textFrameV24("TALB", "Should Go"),
            ),
            audio = FAKE_MP3,
        )
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(album = null),
            "audio/mpeg",
        )
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertNull(fields?.album)
        assertTrue(!rewritten.asString().contains("Should Go"))
    }

    @Test
    fun flacReplacesTitleAndKeepsReplayGain() {
        val original = flacFile(
            comments = listOf(
                "TITLE=Old",
                "ARTIST=Who",
                "REPLAYGAIN_TRACK_GAIN=-6.00 dB",
            ),
            audio = byteArrayOf(9, 8, 7, 6),
        )
        val rewritten = rewrite(original, SAMPLE_FIELDS, "audio/flac")
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("夜曲", fields?.title)
        assertEquals("周杰伦", fields?.artist)
        assertEquals("十一月的萧邦", fields?.album)
        assertTrue(rewritten.asString().contains("REPLAYGAIN_TRACK_GAIN=-6.00 dB"))
        assertTrue(rewritten.copyOfRange(rewritten.size - 4, rewritten.size).contentEquals(byteArrayOf(9, 8, 7, 6)))
    }

    @Test
    fun writesLyricsIntoId3AndKeepsArtwork() {
        val artwork = Id3FrameBytes("APIC", byteArrayOf(0, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 3, 0, 1, 2, 3))
        val original = id3File(
            version = 4,
            frames = listOf(
                textFrameV24("TIT2", "Old"),
                textFrameV24("TPE1", "Band"),
                artwork,
            ),
            audio = FAKE_MP3,
        )
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(lyrics = "[00:01.00]夜曲"),
            "audio/mpeg",
        )
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("[00:01.00]夜曲", fields?.lyrics)
        assertTrue(rewritten.asString().contains("APIC"))
    }

    @Test
    fun flacWritesLyricsAndArtwork() {
        val original = flacFile(
            comments = listOf("TITLE=Old", "ARTIST=Who", "REPLAYGAIN_TRACK_GAIN=-6.00 dB"),
            audio = byteArrayOf(9, 8, 7, 6),
        )
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(lyrics = "歌词", artworkBytes = TINY_PNG, artworkMime = "image/png"),
            "audio/flac",
        )
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("歌词", fields?.lyrics)
        assertTrue(rewritten.asString().contains("REPLAYGAIN_TRACK_GAIN=-6.00 dB"))
        assertTrue(rewritten.toList().windowed(TINY_PNG.size).any { it == TINY_PNG.toList() })
    }

    @Test
    fun wavWritesInfoAndLyrics() {
        val original = silentWav()
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(lyrics = "WAV lyrics"),
            "audio/wav",
        )
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("夜曲", fields?.title)
        assertEquals("周杰伦", fields?.artist)
        assertEquals("十一月的萧邦", fields?.album)
        assertEquals(7, fields?.trackNumber)
        assertEquals(2005, fields?.year)
        assertEquals("WAV lyrics", fields?.lyrics)
        assertTrue(rewritten.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF")
    }

    @Test
    fun mp4IsUnsupported() {
        val ftyp = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 1, 2, 3, 4)
        val status = AudioFileTagRewriter.rewrite(
            ByteArrayInputStream(ftyp),
            ByteArrayOutputStream(),
            SAMPLE_FIELDS,
            "audio/mp4",
        )
        assertEquals(AudioTagRewriteStatus.UnsupportedFormat, status)
    }

    private fun rewrite(source: ByteArray, fields: AudioTagFields, mimeType: String): ByteArray {
        val output = ByteArrayOutputStream()
        val status = AudioFileTagRewriter.rewrite(ByteArrayInputStream(source), output, fields, mimeType)
        assertEquals(AudioTagRewriteStatus.Written, status)
        val bytes = output.toByteArray()
        assertNotNull(AudioFileTagRewriter.readFields(ByteArrayInputStream(bytes)))
        return bytes
    }

    private fun id3File(version: Int, frames: List<ByteArray>, audio: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        frames.forEach(body::write)
        val bodyBytes = body.toByteArray()
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            version.toByte(), 0, 0,
        ) + synchsafe(bodyBytes.size)
        return header + bodyBytes + audio
    }

    private fun textFrameV24(id: String, value: String): ByteArray {
        val payload = byteArrayOf(3) + value.toByteArray(Charsets.UTF_8)
        return id.toByteArray(Charsets.ISO_8859_1) + synchsafe(payload.size) + byteArrayOf(0, 0) + payload
    }

    private fun Id3FrameBytes(id: String, payload: ByteArray): ByteArray =
        id.toByteArray(Charsets.ISO_8859_1) + synchsafe(payload.size) + byteArrayOf(0, 0) + payload

    private fun flacFile(comments: List<String>, audio: ByteArray): ByteArray {
        val vendor = "test".toByteArray(Charsets.UTF_8)
        val commentBytes = comments.map { it.toByteArray(Charsets.UTF_8) }
        val vorbis = ByteArrayOutputStream()
        vorbis.write(leInt(vendor.size))
        vorbis.write(vendor)
        vorbis.write(leInt(commentBytes.size))
        commentBytes.forEach { bytes ->
            vorbis.write(leInt(bytes.size))
            vorbis.write(bytes)
        }
        val vorbisPayload = vorbis.toByteArray()
        val streamInfo = ByteArray(34)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
        out.write(flacBlock(type = 0, last = false, payload = streamInfo))
        out.write(flacBlock(type = 4, last = true, payload = vorbisPayload))
        out.write(audio)
        return out.toByteArray()
    }

    private fun flacBlock(type: Int, last: Boolean, payload: ByteArray): ByteArray {
        val header0 = type or if (last) 0x80 else 0
        val length = payload.size
        return byteArrayOf(
            header0.toByte(),
            ((length shr 16) and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte(),
        ) + payload
    }

    private fun synchsafe(value: Int): ByteArray =
        byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte(),
        )

    private fun leInt(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    private fun ByteArray.asString(): String = toString(Charsets.ISO_8859_1)

    private companion object {
        val SAMPLE_FIELDS = AudioTagFields(
            title = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦",
            albumArtist = "Jay Chou",
            trackNumber = 7,
            discNumber = 1,
            year = 2005,
        )
        val FAKE_MP3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00, 9, 8, 7, 6)
        val TINY_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
        )
    }

    private fun silentWav(): ByteArray {
        val fmt = byteArrayOf(
            1, 0, 1, 0, 0x40, 0x1F, 0, 0, 0x80.toByte(), 0x3E, 0, 0, 2, 0, 16, 0,
        )
        val fmtChunk = wavChunk("fmt ", fmt)
        val dataChunk = wavChunk("data", ByteArray(16))
        val body = fmtChunk + dataChunk
        val header = ByteArray(12)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(header)
        val size = 4 + body.size
        header[4] = (size and 0xFF).toByte()
        header[5] = ((size shr 8) and 0xFF).toByte()
        header[6] = ((size shr 16) and 0xFF).toByte()
        header[7] = ((size shr 24) and 0xFF).toByte()
        "WAVE".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
        return header + body
    }

    private fun wavChunk(id: String, payload: ByteArray): ByteArray {
        val padded = payload.size + (payload.size and 1)
        val out = ByteArray(8 + padded)
        id.padEnd(4, ' ').take(4).toByteArray(Charsets.US_ASCII).copyInto(out)
        out[4] = (payload.size and 0xFF).toByte()
        out[5] = ((payload.size shr 8) and 0xFF).toByte()
        out[6] = ((payload.size shr 16) and 0xFF).toByte()
        out[7] = ((payload.size shr 24) and 0xFF).toByte()
        payload.copyInto(out, 8)
        return out
    }
}

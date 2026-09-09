package app.echo.android.playback

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoReplayGainReaderTest {
    @Test
    fun flacVorbisCommentsSurviveTheTenByteProbe() {
        val tags = EchoReplayGainReader.readTags(
            ByteArrayInputStream(
                flacFile(
                    comments = listOf(
                        "TITLE=Song",
                        "REPLAYGAIN_TRACK_GAIN=-6.50 dB",
                        "REPLAYGAIN_ALBUM_GAIN=-8.00 dB",
                    ),
                ),
            ),
        )
        assertEquals(-6.5f, tags.trackGainDb)
        assertEquals(-8f, tags.albumGainDb)
    }

    @Test
    fun id3TxxxGainsAreRead() {
        val tags = EchoReplayGainReader.readTags(
            ByteArrayInputStream(
                id3File(
                    frames = listOf(
                        txxxFrame("REPLAYGAIN_TRACK_GAIN", "-3.20 dB"),
                        txxxFrame("REPLAYGAIN_ALBUM_GAIN", "-5.10 dB"),
                    ),
                ),
            ),
        )
        assertEquals(-3.2f, tags.trackGainDb)
        assertEquals(-5.1f, tags.albumGainDb)
    }

    @Test
    fun id3PrefixedFlacFallsBackToVorbisComments() {
        val id3 = id3File(frames = listOf(txxxFrame("COMMENT", "Ignored")), trailer = byteArrayOf())
        val flac = flacFile(comments = listOf("REPLAYGAIN_TRACK_GAIN=+1.50 dB"))
        val tags = EchoReplayGainReader.readTags(ByteArrayInputStream(id3 + flac))
        assertEquals(1.5f, tags.trackGainDb)
    }

    @Test
    fun filesWithoutReplayGainTagsReturnEmptyNotFailure() {
        val flac = EchoReplayGainReader.readTags(
            ByteArrayInputStream(flacFile(comments = listOf("TITLE=No gain"))),
        )
        assertNull(flac.trackGainDb)
        assertNull(flac.albumGainDb)
        val mpeg = EchoReplayGainReader.readTags(
            ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)),
        )
        assertNull(mpeg.trackGainDb)
    }

    @Test
    fun largeFlacPictureBlocksDoNotHideLaterVorbisComments() {
        val picture = ByteArray(MAX_TAG_BYTES + 16) { 1 }
        val tags = EchoReplayGainReader.readTags(
            ByteArrayInputStream(
                flacFile(
                    extraBlocks = listOf(0 to ByteArray(34), 6 to picture),
                    comments = listOf("REPLAYGAIN_ALBUM_GAIN=-4.00 dB"),
                ),
            ),
        )
        assertEquals(-4f, tags.albumGainDb)
        assertNull(tags.trackGainDb)
    }

    private fun flacFile(
        comments: List<String>,
        extraBlocks: List<Pair<Int, ByteArray>> = listOf(0 to ByteArray(34)),
    ): ByteArray {
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
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
        extraBlocks.forEach { (type, payload) ->
            out.write(flacBlock(type = type, last = false, payload = payload))
        }
        out.write(flacBlock(type = 4, last = true, payload = vorbis.toByteArray()))
        out.write(byteArrayOf(9, 8, 7, 6))
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

    private fun id3File(
        frames: List<ByteArray>,
        trailer: ByteArray = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0),
    ): ByteArray {
        val body = ByteArrayOutputStream()
        frames.forEach(body::write)
        val bodyBytes = body.toByteArray()
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            4, 0, 0,
        ) + synchsafe(bodyBytes.size)
        return header + bodyBytes + trailer
    }

    private fun txxxFrame(description: String, value: String): ByteArray {
        val payload = byteArrayOf(3) +
            description.toByteArray(Charsets.UTF_8) +
            byteArrayOf(0) +
            value.toByteArray(Charsets.UTF_8)
        return "TXXX".toByteArray(Charsets.ISO_8859_1) + synchsafe(payload.size) + byteArrayOf(0, 0) + payload
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

    private companion object {
        const val MAX_TAG_BYTES = 2 * 1024 * 1024
    }
}

package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ContainerTagRewriteTest {
    @Test fun commonContainersRoundTripTagsAndKeepEveryAudioPacketAcrossRepeatedEdits() {
        val directory = File("build/format-tag-smoke").apply { mkdirs() }
        for (extension in listOf("m4a", "ogg", "opus")) {
            val source = javaClass.getResourceAsStream("/format-tags/tone.$extension")!!.use { it.readBytes() }
            val fields = AudioTagFields("新标题 🎵", "Artist A; Artist B", "Album", "Owner", 3, 1, 2020,
                lyrics = "新歌词\n第二行".repeat(5000), artworkBytes = Png, artworkMime = "image/png", replayGainTrackGainDb = -3.25f)
            val original = File(directory, "original.$extension").apply { writeBytes(source) }
            val first = File(directory, "edited.$extension")
            assertEquals(extension, AudioTagRewriteStatus.Written, AudioFileTagRewriter.rewriteFile(original, first, fields))
            val read = readLocalAudioTags(first.inputStream())!!
            assertTrue("$extension tags must verify", read.matchesWrittenTags(fields))
            assertEquals("Composer", read.composer)
            assertEquals("Ambient", read.genre)
            assertAudioEquals(source, first.readBytes(), extension)
            val cleared = fields.copy(title = "Again", lyrics = "", composer = null, composerModified = true, genreModified = true)
            val second = File(directory, "repeated.$extension")
            assertEquals(extension, AudioTagRewriteStatus.Written, AudioFileTagRewriter.rewriteFile(first, second, cleared))
            val finalTags = readLocalAudioTags(second.inputStream())!!
            assertTrue(finalTags.matchesWrittenTags(cleared))
            assertNull(finalTags.composer)
            assertNull(finalTags.genre)
            assertNull(finalTags.lyrics)
            assertAudioEquals(source, second.readBytes(), extension)
            if (extension == "m4a") assertTrue("Trailing moov must not accumulate after edits", second.length() <= first.length() + 1024)
        }
    }

    @Test fun streamEntryAndMalformedFilesKeepTheirSafetyChecks() {
        val source = javaClass.getResourceAsStream("/format-tags/tone.opus")!!.use { it.readBytes() }
        val fields = AudioTagFields("Title", "Artist", null, null, null, null, null)
        val output = ByteArrayOutputStream()
        assertEquals(AudioTagRewriteStatus.Written, AudioFileTagRewriter.rewrite(source.inputStream(), output, fields))
        assertTrue(readLocalAudioTags(output.toByteArray().inputStream()).matchesWrittenTags(fields))
        val corrupted = source.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertEquals(AudioTagRewriteStatus.InvalidSource, AudioFileTagRewriter.rewrite(corrupted.inputStream(), ByteArrayOutputStream(), fields))
        assertEquals(AudioTagRewriteStatus.InvalidSource, AudioFileTagRewriter.rewrite(source.copyOf(40).inputStream(), ByteArrayOutputStream(), fields))
        assertTrue(isSupportedIncomingAudioFile("song.TAK", "application/octet-stream"))
        assertFalse(isSupportedIncomingAudioFile("archive.zip", "application/octet-stream"))
        assertEquals("audio/ape", incomingAudioMimeType("song.ape", "application/octet-stream"))
    }

    private fun assertAudioEquals(first: ByteArray, second: ByteArray, extension: String) {
        if (extension == "m4a") {
            assertArrayEquals(mdat(first), mdat(second))
        } else {
            val old = packets(first).drop(if (extension == "opus") 2 else 3)
            val updated = packets(second).drop(if (extension == "opus") 2 else 3)
            assertEquals(old.size, updated.size)
            old.zip(updated).forEach { (a, b) -> assertArrayEquals(a, b) }
        }
    }

    private fun mdat(bytes: ByteArray): ByteArray {
        var offset = 0
        while (offset + 8 <= bytes.size) {
            val size = bytes.tagU32(offset).toInt()
            if (bytes.copyOfRange(offset + 4, offset + 8).toString(Charsets.US_ASCII) == "mdat") return bytes.copyOfRange(offset + 8, offset + size)
            offset += size
        }
        error("Missing mdat")
    }

    private fun packets(bytes: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        val current = ByteArrayOutputStream()
        var offset = 0
        while (offset < bytes.size) {
            val segments = bytes[offset + 26].toInt() and 255
            var payload = offset + 27 + segments
            repeat(segments) { index ->
                val size = bytes[offset + 27 + index].toInt() and 255
                current.write(bytes, payload, size); payload += size
                if (size < 255) { packets += current.toByteArray(); current.reset() }
            }
            offset = payload
        }
        return packets
    }

    private val Png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ioAAAAASUVORK5CYII=")
}

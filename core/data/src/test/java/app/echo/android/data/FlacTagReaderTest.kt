package app.echo.android.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlacTagReaderTest {
    @Test
    fun readsJapaneseUtf8TagsAndSkipsArtworkBeforeComments() {
        // Same tag values and metadata block ordering as the reported FLAC.
        val comments = comments("TITLE=六兆年と一夜物語", "ARTIST=KEMU VOXX/IA", "ALBUM=IA THE WORLD ～風～")
        val source = "fLaC".toByteArray() + block(0, ByteArray(34)) +
            block(3, ByteArray(3924)) + block(6, ByteArray(278137)) + block(4, comments)
        var bytesRead = 0
        val input = object : ByteArrayInputStream(source) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                super.read(bytes, offset, length).also { if (it > 0) bytesRead += it }
        }

        val tags = readLocalAudioTags(input)

        assertEquals("六兆年と一夜物語", tags?.title)
        assertEquals("KEMU VOXX/IA", tags?.artist)
        assertEquals("IA THE WORLD ～風～", tags?.album)
        assertTrue("Artwork must be skipped rather than loaded", bytesRead < 1024)
    }

    @Test
    fun stopsAtLastMetadataBlockWithoutReadingAudio() {
        val input = ByteArrayInputStream("fLaC".toByteArray() + block(0x80, ByteArray(34)) + ByteArray(256))
        assertNull(readLocalAudioTags(input))
        assertTrue(input.available() >= 256)
    }

    @Test
    fun truncatedCommentsFallBackWithoutTags() {
        val bytes = "fLaC".toByteArray() + block(0, ByteArray(34)) +
            block(0x84, comments("TITLE=六兆年と一夜物語"))
        assertNull(readLocalAudioTags(ByteArrayInputStream(bytes.copyOf(bytes.size - 1))))
    }

    @Test
    fun oversizedCommentCountDoesNotAllocateFromUntrustedCount() {
        val payload = leInt(0) + leInt(Int.MAX_VALUE)
        val bytes = "fLaC".toByteArray() + block(0, ByteArray(34)) + block(0x84, payload)
        assertNull(readLocalAudioTags(ByteArrayInputStream(bytes)))
    }

    @Test
    fun bothScanEntrypointsRecognizeFlacMimeOrFilename() {
        assertTrue(LocalAudioTagReadPolicy.prefersFileTags("audio/flac", null))
        assertTrue(LocalAudioTagReadPolicy.prefersFileTags("audio/x-flac", null))
        assertTrue(LocalAudioTagReadPolicy.prefersFileTags("application/octet-stream", "六兆年と一夜物語.FLAC"))
        assertTrue(LocalAudioTagReadPolicy.prefersFileTags(null, "content://docs/song.flac%20copy"))
        assertTrue(LocalAudioTagReadPolicy.prefersFileTags("audio/wav", null))
        assertTrue(LocalAudioTagReadPolicy.prefersFileTags("audio/mpeg", "song.mp3"))
        assertTrue(LocalAudioTagReadPolicy.prefersFileTags("application/flac", null))
        assertTrue(LocalAudioTagReadPolicy.prefersFileTags(null, "song.WAVE"))
        assertFalse(LocalAudioTagReadPolicy.prefersFileTags("audio/mp4", "song.m4a"))
    }

    private fun comments(vararg values: String): ByteArray = ByteArrayOutputStream().apply {
        val vendor = "Lavf58.45.100".toByteArray(Charsets.UTF_8)
        write(leInt(vendor.size))
        write(vendor)
        write(leInt(values.size))
        values.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            write(leInt(bytes.size))
            write(bytes)
        }
    }.toByteArray()

    private fun block(type: Int, payload: ByteArray): ByteArray = byteArrayOf(
        type.toByte(), (payload.size shr 16).toByte(), (payload.size shr 8).toByte(), payload.size.toByte(),
    ) + payload

    private fun leInt(value: Int): ByteArray = byteArrayOf(
        value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte(),
    )
}

package app.echo.android.data

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class BoundedEmbeddedLyricsTest {
    private val lyrics = "[00:01.00]Tail lyrics"

    @Test fun readsApeAndWavPackTailWithoutWalkingLargeAudio() {
        for (header in listOf("MAC ", "wvpk")) {
            val file = File.createTempFile("echo-lyrics-tail-", ".bin")
            try {
                file.outputStream().use {
                    it.write(header.toByteArray())
                    it.write(ByteArray(3 * 1024 * 1024))
                    it.write(apeTag())
                }
                file.inputStream().use { input ->
                    assertEquals(lyrics, readBoundedEmbeddedLyrics(input))
                    assertEquals(10L, input.channel.position())
                }
            } finally { file.delete() }
        }
    }

    @Test fun exhaustedBudgetIsUnknownRatherThanMissing() {
        val file = File.createTempFile("echo-lyrics-limit-", ".bin")
        try {
            file.writeBytes("MAC ".toByteArray() + ByteArray(256) + apeTag())
            file.inputStream().use { input ->
                assertThrows(IOException::class.java) { readBoundedEmbeddedLyrics(input, maxBytes = 32) }
            }
        } finally { file.delete() }
    }

    @Test fun unseekableTailIsUnknownAndCancellationPropagates() {
        val bytes = "MAC ".toByteArray() + ByteArray(256) + apeTag()
        assertThrows(IOException::class.java) { readBoundedEmbeddedLyrics(bytes.inputStream()) }
        assertThrows(CancellationException::class.java) {
            readBoundedEmbeddedLyrics(bytes.inputStream()) { throw CancellationException() }
        }
    }

    private fun apeTag(): ByteArray {
        val value = lyrics.toByteArray()
        val item = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(value.size).putInt(0).array() +
            "Lyrics\u0000".toByteArray() + value
        return item + ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put("APETAGEX".toByteArray()).putInt(2000).putInt(item.size + 32).putInt(1).putInt(0).putLong(0).array()
    }
}

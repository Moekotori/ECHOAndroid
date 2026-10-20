package app.echo.android.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class AdditionalLosslessMetadataTest {
    @Test fun referenceHeadersHaveTheExpectedRateAndDuration() {
        for ((name, rate, duration) in listOf(Triple("tone.tta", 48000, 250L),
            Triple("tone.w64", 48000, 250L), Triple("sample.tak", 44100, 9500L))) {
            val input = fixture(name).inputStream()
            val info = LocalAudioContainerMetadata.read(input)!!
            assertEquals(name, rate, info.sampleRateHz)
            assertEquals(name, duration, info.durationMs)
            assertTrue("$name should stop before audio data", input.available() > 0)
            assertTrue(LocalAudioContainerMetadata.supports(null, name))
        }
    }

    @Test fun badCrcAndUnboundedChunksDoNotBecomeDurationFacts() {
        val tta = fixture("tone.tta")
        tta[24] = (tta[24].toInt() xor 1).toByte()
        assertNull(LocalAudioContainerMetadata.read(tta.inputStream()))
        val tak = fixture("sample.tak")
        tak[5] = -1; tak[6] = -1; tak[7] = -1
        assertNull(LocalAudioContainerMetadata.read(tak.inputStream()))
        val w64 = fixture("tone.w64")
        ByteBuffer.wrap(w64).order(ByteOrder.LITTLE_ENDIAN).putLong(56, Long.MAX_VALUE)
        assertNull(LocalAudioContainerMetadata.read(w64.inputStream()))
    }

    @Test fun ttaAndTakUseTheExistingApeTagPath() {
        for (signature in listOf("TTA1", "tBaK")) {
            val text = "测试歌曲".toByteArray()
            val item = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(text.size).putInt(0).array() +
                "Title\u0000".toByteArray() + text
            val footer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).put("APETAGEX".toByteArray())
                .putInt(2000).putInt(item.size + 32).putInt(1).putInt(0).putLong(0).array()
            val bytes = signature.toByteArray() + ByteArray(32) + item + footer
            val tags = readLocalAudioTags(RandomAccessTagInput(bytes.size.toLong()) { at, count ->
                bytes.copyOfRange(at.toInt(), at.toInt() + count)
            })!!
            assertEquals("测试歌曲", tags.title)
        }
    }

    private fun fixture(name: String): ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/audio-scan/$name.header"),
    ).use { it.readBytes() }
}

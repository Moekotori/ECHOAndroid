package app.echo.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkCastFormatTest {
    @Test
    fun sniffsFlacStreamInfo() {
        val header = flacHeader(sampleRateHz = 96_000, channels = 2, bitDepth = 24)
        val format = EchoLinkCastFormat.sniff(header)
        checkNotNull(format)
        assertEquals("flac", format.codec)
        assertEquals("audio/flac", format.mimeType)
        assertEquals(96_000, format.sampleRateHz)
        assertEquals(24, format.bitDepth)
        assertEquals(2, format.channelCount)
        assertEquals(true, format.lossless)
        assertEquals("96 kHz · 24-bit · Stereo · FLAC", EchoLinkCastFormat.formatLabel(format))
    }

    @Test
    fun sniffsWavPcm() {
        val header = wavHeader(sampleRateHz = 192_000, channels = 2, bitDepth = 24)
        val format = EchoLinkCastFormat.sniff(header)
        checkNotNull(format)
        assertEquals("wav", format.codec)
        assertEquals(192_000, format.sampleRateHz)
        assertEquals(24, format.bitDepth)
        assertEquals(2, format.channelCount)
        assertEquals(true, format.lossless)
    }

    @Test
    fun sniffsDsfMagic() {
        val header = ByteArray(16)
        "DSD ".encodeToByteArray().copyInto(header)
        val format = EchoLinkCastFormat.sniff(header)
        checkNotNull(format)
        assertEquals("dsd", format.codec)
        assertEquals(true, format.lossless)
        assertEquals("audio/x-dsf", format.mimeType)
    }

    @Test
    fun fromTrackUsesExtensionWhenHeaderMissing() {
        val format = EchoLinkCastFormat.fromTrack(
            uri = "content://media/1/track.flac",
            sampleRateHz = 48_000,
        )
        assertEquals("flac", format.codec)
        assertEquals(48_000, format.sampleRateHz)
        assertEquals(true, format.lossless)
        assertTrue(EchoLinkCastFormat.httpHeaders(format).containsKey("X-ECHO-Link-Codec"))
    }

    @Test
    fun mergePrefersSniffedCodec() {
        val merged = EchoLinkCastFormat.merge(
            EchoLinkCastFormat.fromTrack("file:///a.mp3", sampleRateHz = 44_100),
            EchoLinkCastFormat.sniff(flacHeader(sampleRateHz = 96_000, channels = 2, bitDepth = 24)),
        )
        checkNotNull(merged)
        assertEquals("flac", merged.codec)
        assertEquals(96_000, merged.sampleRateHz)
    }

    @Test
    fun unknownBytesYieldNull() {
        assertNull(EchoLinkCastFormat.sniff("JSON".toByteArray()))
    }

    private fun flacHeader(sampleRateHz: Int, channels: Int, bitDepth: Int): ByteArray {
        val header = ByteArray(42)
        "fLaC".encodeToByteArray().copyInto(header)
        header[7] = 34
        val packedRate = sampleRateHz and 0xFFFFF
        val ch = (channels - 1) and 0x07
        val bits = (bitDepth - 1) and 0x1F
        header[18] = (packedRate shr 12).toByte()
        header[19] = ((packedRate shr 4) and 0xFF).toByte()
        header[20] = (((packedRate and 0x0F) shl 4) or (ch shl 1) or (bits shr 4)).toByte()
        header[21] = ((bits and 0x0F) shl 4).toByte()
        return header
    }

    private fun wavHeader(sampleRateHz: Int, channels: Int, bitDepth: Int): ByteArray {
        val header = ByteArray(44)
        "RIFF".encodeToByteArray().copyInto(header)
        "WAVE".encodeToByteArray().copyInto(header, 8)
        "fmt ".encodeToByteArray().copyInto(header, 12)
        header[16] = 16
        header[20] = 1
        header[22] = channels.toByte()
        header[24] = (sampleRateHz and 0xFF).toByte()
        header[25] = ((sampleRateHz shr 8) and 0xFF).toByte()
        header[26] = ((sampleRateHz shr 16) and 0xFF).toByte()
        header[27] = ((sampleRateHz shr 24) and 0xFF).toByte()
        header[34] = bitDepth.toByte()
        return header
    }
}

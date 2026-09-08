package app.echo.android.usbaudio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class UsbBitPerfectTest {
    @Test fun all16BitValuesSurviveEndianConversionAndWidening() {
        for (big in listOf(false, true)) {
            val input = ByteBuffer.allocate(65536 * 2).order(if (big) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
            for (v in Short.MIN_VALUE..Short.MAX_VALUE) input.putShort(v.toShort())
            input.flip()
            val output = ByteArray(65536 * 4)
            assertEquals(output.size, UsbBitPerfectPacker.pack(input, 2, 16, big, 24, 4, output, 1))
            val decoded = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
            for (v in Short.MIN_VALUE..Short.MAX_VALUE) assertEquals(v shl 16, decoded.int)
        }
    }

    @Test fun integer32Decoded24BitPcmPacksExactly() {
        val samples = intArrayOf(-8388608, -8388607, -65537, -257, -1, 0, 1, 257, 65537, 8388606, 8388607)
        val input = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { input.putInt(it shl 8) }; input.flip()
        val output = ByteArray(samples.size * 3)
        UsbBitPerfectPacker.pack(input, 4, 24, false, 24, 3, output, 1)
        val expected = samples.flatMap { v -> (0..2).map { (v shr (8 * it)).toByte() } }.toByteArray()
        assertArrayEquals(expected, output)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesUndeclaredPrecision() {
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(257).apply { flip() }
        UsbBitPerfectPacker.pack(input, 4, 24, false, 24, 3, ByteArray(3), 1)
    }

    @Test fun refusesUnknownOrNarrowerUsbFormats() {
        val format = UsbAudioStreamingFormat(1, 1, UsbAudioClassVersion.Uac2, formatType = 1,
            pcmIntegerSupported = true, channelCount = 2, subslotSize = 4, bitResolution = 24,
            sampleRates = listOf(44100), endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous)
        val spec = UsbPcmFormatSpec(44100, 2, 24)
        fun choose(f: UsbAudioStreamingFormat) = UsbPcmFormatSelector.chooseBitPerfectFormat(
            UsbAudioDescriptorInfo(streamingFormats = listOf(f)), spec)
        assertEquals(format, choose(format))
        assertNull(choose(format.copy(bitResolution = 16)))
        assertNull(choose(format.copy(bitResolution = null)))
        assertNull(choose(format.copy(pcmIntegerSupported = false)))
        assertNull(choose(format.copy(channelCount = null)))
        assertNull(choose(format.copy(sampleRates = listOf(48000))))
        assertEquals(format.copy(bitResolution = 32), choose(format.copy(bitResolution = 32)))
    }
}

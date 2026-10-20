package app.echo.android.usbaudio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class UsbBitPerfectTest {
    @Test fun dopRequiresTheExactDeviceOptInAndRespectsItsDsdLimit() {
        val limits = mapOf("123:456" to 11_289_600)
        assertEquals(true, UsbDopPolicy.permits(limits, 123, 456, 705_600))
        assertEquals(false, UsbDopPolicy.permits(limits, 123, 456, 1_411_200))
        assertEquals(false, UsbDopPolicy.permits(limits, 123, 457, 176_400))
        assertEquals(false, UsbDopPolicy.permits(emptyMap(), 123, 456, 176_400))
        assertEquals(false, UsbDopPolicy.permits(limits, 123, 456, 192_000))
    }

    @Test fun dopMarkersContinueAcrossDecoderRestartsAndPartialPacking() {
        val output = ByteArray(16)
        val trackOne = ByteBuffer.wrap(byteArrayOf(2, 1, 5, 12, 11, 5))
        val trackTwo = ByteBuffer.wrap(byteArrayOf(4, 3, 5, 14, 13, 5, 6, 5, 0xFA.toByte(), 16, 15, 0xFA.toByte()))
        assertEquals(8, UsbBitPerfectPacker.packDop(trackOne, 32, 4, output, 2, 0, true))
        // The new decoder restarts at 0x05; the session must stamp 0xFA instead.
        assertEquals(8, UsbBitPerfectPacker.packDop(trackTwo, 32, 4, output, 2, 8, false))
        assertArrayEquals(byteArrayOf(0, 2, 1, 5, 0, 12, 11, 5,
            0, 4, 3, 0xFA.toByte(), 0, 14, 13, 0xFA.toByte()), output)
        assertEquals(8, UsbBitPerfectPacker.packDop(trackTwo, 32, 4, output, 2, 0, true))
        assertArrayEquals(byteArrayOf(0, 6, 5, 5, 0, 16, 15, 5), output.copyOf(8))
    }

    @Test fun appendingDecodedFramesPreservesUsbTailAndBackpressure() {
        val output = ByteArray(24)
        val tail = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        tail.copyInto(output)
        val input = ByteBuffer.wrap(ByteArray(24) { (it + 9).toByte() })
        val written = UsbBitPerfectPacker.pack(input, 2, 16, false, 16, 2, output, 2, tail.size)
        assertEquals(16, written)
        assertEquals(16, input.position())
        assertArrayEquals(ByteArray(24) { (it + 1).toByte() }, output)
        assertEquals(0, UsbBitPerfectPacker.pack(input, 2, 16, false, 16, 2, output, 2, output.size))
        assertEquals(16, input.position())
    }

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

    @Test
    fun dsd64DopSelects176400Hz24BitPcm() {
        val format = UsbAudioStreamingFormat(
            1, 1, UsbAudioClassVersion.Uac2, formatType = 1,
            pcmIntegerSupported = true, channelCount = 2, subslotSize = 4, bitResolution = 24,
            sampleRates = listOf(44_100, 48_000, 176_400),
            endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous,
        )
        val chosen = UsbPcmFormatSelector.chooseBitPerfectFormat(
            UsbAudioDescriptorInfo(streamingFormats = listOf(format)),
            UsbPcmFormatSpec(176_400, 2, 24),
        )
        assertEquals(format, chosen)
        assertNull(
            UsbPcmFormatSelector.chooseBitPerfectFormat(
                UsbAudioDescriptorInfo(streamingFormats = listOf(format.copy(sampleRates = listOf(44_100, 48_000)))),
                UsbPcmFormatSpec(176_400, 2, 24),
            ),
        )
    }
    @Test fun highRateDopRequiresExactCarrierAndPreservesMarkersWhenWidened() {
        val format = UsbAudioStreamingFormat(1, 1, UsbAudioClassVersion.Uac2, formatType = 1,
            pcmIntegerSupported = true, channelCount = 2, subslotSize = 4, bitResolution = 32,
            sampleRates = listOf(705_600, 1_411_200), endpointDirection = UsbEndpointDirection.Out,
            endpointTransferType = UsbEndpointTransferType.Isochronous)
        val descriptor = UsbAudioDescriptorInfo(streamingFormats = listOf(format))
        for (rate in listOf(705_600, 1_411_200)) {
            assertEquals(format, UsbPcmFormatSelector.chooseBitPerfectFormat(descriptor, UsbPcmFormatSpec(rate, 2, 24)))
        }
        assertNull(UsbPcmFormatSelector.chooseBitPerfectFormat(descriptor, UsbPcmFormatSpec(352_800, 2, 24)))
        val input = ByteBuffer.wrap(byteArrayOf(0x33, 0x11, 0x05, 0x44, 0x22, 0x05,
            0x55, 0x66, 0xFA.toByte(), 0x77, 0x08, 0xFA.toByte()))
        val output = ByteArray(16)
        assertEquals(16, UsbBitPerfectPacker.pack(input, 3, 24, false, 32, 4, output, 2))
        assertArrayEquals(byteArrayOf(0, 0x33, 0x11, 0x05, 0, 0x44, 0x22, 0x05,
            0, 0x55, 0x66, 0xFA.toByte(), 0, 0x77, 0x08, 0xFA.toByte()), output)
    }

}

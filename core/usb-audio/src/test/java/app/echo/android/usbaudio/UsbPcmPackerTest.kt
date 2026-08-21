package app.echo.android.usbaudio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbPcmPackerTest {
    @Test
    fun packsLittleEndian16BitStereoUnchanged() {
        val source = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        source.putShort(0x1234)
        source.putShort(0x5678.toShort())
        source.putShort(0x1111)
        source.putShort(0x2222)
        source.flip()
        val dest = ByteArray(8)
        val written = UsbPcmPacker.pack(
            source = source,
            sourceEncoding = UsbPcmSourceEncoding.Pcm16,
            frames = 2,
            channelCount = 2,
            destBytesPerSample = 2,
            destination = dest,
        )
        assertEquals(8, written)
        assertArrayEquals(
            byteArrayOf(0x34, 0x12, 0x78, 0x56, 0x11, 0x11, 0x22, 0x22),
            dest,
        )
    }

    @Test
    fun packs24BitIn32DownToThreeByteUsbSlots() {
        val source = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        source.putInt(0x00112233)
        source.putInt(0x00445566)
        source.flip()
        val dest = ByteArray(6)
        val written = UsbPcmPacker.pack(
            source = source,
            sourceEncoding = UsbPcmSourceEncoding.Pcm24In32,
            frames = 1,
            channelCount = 2,
            destBytesPerSample = 3,
            destination = dest,
        )
        assertEquals(6, written)
        assertArrayEquals(
            byteArrayOf(0x33, 0x22, 0x11, 0x66, 0x55, 0x44),
            dest,
        )
    }

    @Test
    fun packsMedia3PackedTwentyFourBitToUsbSlots() {
        val source = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        source.put(0x33.toByte())
        source.put(0x22.toByte())
        source.put(0x11.toByte())
        source.put(0x66.toByte())
        source.put(0x55.toByte())
        source.put(0x44.toByte())
        source.flip()
        val dest = ByteArray(6)
        val written = UsbPcmPacker.pack(
            source = source,
            sourceEncoding = UsbPcmSourceEncoding.Pcm24Packed,
            frames = 1,
            channelCount = 2,
            destBytesPerSample = 3,
            destination = dest,
        )
        assertEquals(6, written)
        assertEquals(0, source.remaining())
        assertArrayEquals(
            byteArrayOf(0x33, 0x22, 0x11, 0x66, 0x55, 0x44),
            dest,
        )
    }

    @Test
    fun unityVolumeKeepsBitPerfectSixteenBit() {
        val source = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        source.putShort(0x1234)
        source.putShort(0x7fff.toShort())
        source.flip()
        val dest = ByteArray(4)
        UsbPcmPacker.pack(
            source = source,
            sourceEncoding = UsbPcmSourceEncoding.Pcm16,
            frames = 1,
            channelCount = 2,
            destBytesPerSample = 2,
            destination = dest,
            volume = 1f,
        )
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xff.toByte(), 0x7f), dest)
    }

    @Test
    fun unityLittleEndianSixteenBitCopiesDirectly() {
        val source = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        source.putShort(0x1234)
        source.putShort(0x7fff.toShort())
        source.flip()
        val dest = ByteArray(4)
        UsbPcmPacker.pack(
            source = source,
            sourceEncoding = UsbPcmSourceEncoding.Pcm16,
            frames = 1,
            channelCount = 2,
            destBytesPerSample = 2,
            destination = dest,
            volume = 1f,
        )
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xff.toByte(), 0x7f), dest)
        assertTrue(
            UsbPcmPacker.canCopyDirectly(
                UsbPcmSourceEncoding.Pcm16,
                2,
                ByteOrder.LITTLE_ENDIAN,
                1f,
            ),
        )
    }

    @Test
    fun makeupGainAboveUnityScalesSixteenBit() {
        val source = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        source.putShort(0x1000)
        source.flip()
        val dest = ByteArray(2)
        UsbPcmPacker.pack(
            source = source,
            sourceEncoding = UsbPcmSourceEncoding.Pcm16,
            frames = 1,
            channelCount = 1,
            destBytesPerSample = 2,
            destination = dest,
            volume = 2f,
        )
        val sample = (dest[0].toInt() and 0xff) or ((dest[1].toInt() and 0xff) shl 8)
        assertEquals(0x2000, sample)
    }

    @Test
    fun zeroVolumeSilencesThePacket() {
        val source = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        source.putShort(0x1234)
        source.putShort(0x5678.toShort())
        source.flip()
        val dest = ByteArray(4)
        UsbPcmPacker.pack(
            source = source,
            sourceEncoding = UsbPcmSourceEncoding.Pcm16,
            frames = 1,
            channelCount = 2,
            destBytesPerSample = 2,
            destination = dest,
            volume = 0f,
        )
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), dest)
    }
}

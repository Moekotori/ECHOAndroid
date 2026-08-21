package app.echo.android.usbaudio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}

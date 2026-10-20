package app.echo.android.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoDsdDopStreamPackerTest {
    @Test fun oddPlanarPacketsCarryBytesWithoutInsertingSilence() {
        val packer = EchoDsdDopStreamPacker(2, 176_400)
        val output = ByteArray(12)
        val first = ByteArray(6)
        assertEquals(6, packer.pack(byteArrayOf(1, 2, 3, 11, 12, 13), 6, true, false, 0, first))
        first.copyInto(output)
        val second = ByteArray(6)
        assertEquals(6, packer.pack(byteArrayOf(4, 14), 2, true, false, 8, second))
        second.copyInto(output, 6)
        assertEquals(5L, packer.outputTimeUs)
        assertEquals(0, packer.finish(second))
        val expected = ByteArray(12)
        EchoDsdDop.pack(byteArrayOf(1, 2, 3, 4, 11, 12, 13, 14), 0, 8, 2,
            true, false, true, expected)
        assertArrayEquals(expected, output)
    }

    @Test fun eosPadsOnlyTheFinalByteAndResetDiscardsOldTail() {
        val packer = EchoDsdDopStreamPacker(1, 176_400)
        val output = ByteArray(3)
        assertEquals(0, packer.pack(byteArrayOf(0x80.toByte()), 1, false, true, 123, output))
        assertEquals(3, packer.finish(output))
        assertEquals(123L, packer.outputTimeUs)
        assertArrayEquals(byteArrayOf(0x69, 1, 5), output)
        assertEquals(0, packer.finish(output))
        packer.pack(byteArrayOf(2), 1, false, false, 200, output)
        packer.reset()
        assertEquals(0, packer.finish(output))
        assertEquals(3, packer.pack(byteArrayOf(3, 4), 2, false, false, 300, output))
        assertArrayEquals(byteArrayOf(4, 3, 5), output)
    }
}

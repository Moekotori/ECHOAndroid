package app.echo.android.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoDsdDopTest {
    @Test
    fun dsd64And128MapToDopRates() {
        assertEquals(176_400, EchoDsdDop.sampleRateHz(352_800))
        assertEquals(352_800, EchoDsdDop.sampleRateHz(705_600))
        assertNull(EchoDsdDop.sampleRateHz(1_411_200))
        assertNull(EchoDsdDop.sampleRateHz(88_200))
    }

    @Test
    fun interleavedStereoPacksMarkerAnd16DsdBits() {
        val source = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val dest = ByteArray(6)
        val result = EchoDsdDop.pack(
            source = source,
            sourceOffset = 0,
            sourceLength = 4,
            channelCount = 2,
            planar = false,
            lsbf = false,
            startWithMarkerA = true,
            destination = dest,
        )
        assertEquals(6, result.bytesWritten)
        assertEquals(1, result.frames)
        assertEquals(false, result.nextMarkerA)
        assertArrayEquals(
            byteArrayOf(0x33, 0x11, EchoDsdDop.MarkerA, 0x44, 0x22, EchoDsdDop.MarkerA),
            dest,
        )
    }

    @Test
    fun planarStereoMatchesInterleavedLayout() {
        val planar = byteArrayOf(0x11, 0x33, 0x22, 0x44)
        val dest = ByteArray(6)
        EchoDsdDop.pack(
            source = planar,
            sourceOffset = 0,
            sourceLength = 4,
            channelCount = 2,
            planar = true,
            lsbf = false,
            startWithMarkerA = true,
            destination = dest,
        )
        assertArrayEquals(
            byteArrayOf(0x33, 0x11, EchoDsdDop.MarkerA, 0x44, 0x22, EchoDsdDop.MarkerA),
            dest,
        )
    }

    @Test
    fun markersAlternateEachFrameAndLsbfReversesPayload() {
        val source = byteArrayOf(0x80.toByte(), 0x01, 0x80.toByte(), 0x01)
        val dest = ByteArray(12)
        val result = EchoDsdDop.pack(
            source = source,
            sourceOffset = 0,
            sourceLength = 4,
            channelCount = 1,
            planar = false,
            lsbf = true,
            startWithMarkerA = true,
            destination = dest,
        )
        assertEquals(6, result.bytesWritten)
        assertArrayEquals(
            byteArrayOf(0x80.toByte(), 0x01, EchoDsdDop.MarkerA, 0x80.toByte(), 0x01, EchoDsdDop.MarkerB),
            dest.copyOf(6),
        )
        assertEquals(true, result.nextMarkerA)
    }

    @Test
    fun oddTrailingDsdBytesAreDropped() {
        assertEquals(0, EchoDsdDop.frameCount(3, 2))
        assertEquals(2, EchoDsdDop.frameCount(5, 1))
        assertEquals(6, EchoDsdDop.outputSize(4, 2))
    }
}

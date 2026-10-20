package app.echo.android.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoDsdDopTest {
    @Test
    fun dsd64Through512MapToDopRates() {
        assertEquals(176_400, EchoDsdDop.sampleRateHz(352_800))
        assertEquals(352_800, EchoDsdDop.sampleRateHz(705_600))
        assertEquals(705_600, EchoDsdDop.sampleRateHz(1_411_200))
        assertEquals(1_411_200, EchoDsdDop.sampleRateHz(2_822_400))
        assertNull(EchoDsdDop.sampleRateHz(5_644_800))
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
    fun oddPlanarTailKeepsBothChannelsAndPadsWithDsdSilence() {
        val dest = ByteArray(12)
        val result = EchoDsdDop.pack(
            byteArrayOf(0x80.toByte(), 0x01, 0x02, 0x04, 0x08, 0x10),
            0, 6, 2, planar = true, lsbf = true, startWithMarkerA = true,
            destination = dest,
        )
        assertEquals(2, result.frames)
        assertArrayEquals(byteArrayOf(
            0x80.toByte(), 0x01, EchoDsdDop.MarkerA, 0x10, 0x20, EchoDsdDop.MarkerA,
            0x69, 0x40, EchoDsdDop.MarkerB, 0x69, 0x08, EchoDsdDop.MarkerB,
        ), dest)
    }

    @Test
    fun oddTrailingDsdBytesAreRetained() {
        assertEquals(0, EchoDsdDop.frameCount(3, 2))
        assertEquals(3, EchoDsdDop.frameCount(5, 1))
        assertEquals(6, EchoDsdDop.outputSize(4, 2))
    }
}

package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoDsdLayoutTest {
    @Test
    fun dsd64MapsTo88200AndDsd128MapsTo176400() {
        assertEquals(88_200, EchoDsdPcm.outputSampleRateHz(352_800))
        assertEquals(176_400, EchoDsdPcm.outputSampleRateHz(705_600))
        assertEquals(176_400, EchoDsdPcm.outputSampleRateHz(1_411_200))
        assertEquals(0, EchoDsdPcm.outputSampleRateHz(0))
    }

    @Test
    fun dsfHeaderReportsDurationSeekAndPlanarMime() {
        val file = EchoDsdFixtures.dsf(channels = 2, blockSize = 64, realBytesPerChannel = 64)
        val header = file.copyOf(EchoDsfLayout.HeaderBytes)
        assertTrue(EchoDsfLayout.sniff(header))
        val layout = EchoDsfLayout.parse(header)
        assertNotNull(layout)
        assertEquals(2, layout!!.channelCount)
        assertEquals(2_822_400, layout.dsdRateHz)
        assertEquals(352_800, layout.decoderPcmRateHz)
        assertEquals(EchoDsdMime.MsbfPlanar, layout.mimeType)
        assertEquals(64L * 8 * 1_000_000L / 2_822_400L, layout.durationUs)
        assertEquals(128, layout.blockAlign)
        assertFalse(layout.padded)
        assertEquals(0L, layout.seekAudioByteOffset(0L))
        val mid = layout.seekAudioByteOffset(layout.durationUs / 2)
        assertEquals(0, mid % layout.blockAlign)
        assertTrue(mid >= 0L)
        assertTrue(mid <= layout.audioSize)
    }

    @Test
    fun dsfRejectsUnknownBitOrder() {
        val file = EchoDsdFixtures.dsf(bitsPerSample = 4)
        assertNull(EchoDsfLayout.parse(file.copyOf(EchoDsfLayout.HeaderBytes)))
    }

    @Test
    fun dsfPaddedLastBlockKeepsAudioSizeBelowDataSize() {
        val file = EchoDsdFixtures.dsf(blockSize = 64, realBytesPerChannel = 40)
        val layout = EchoDsfLayout.parse(file.copyOf(EchoDsfLayout.HeaderBytes))!!
        assertTrue(layout.padded)
        assertEquals(80L, layout.audioSize)
        assertEquals(128L, layout.dataSize)
    }

    @Test
    fun dffSniffRequiresFrm8AndDsdForm() {
        val file = EchoDsdFixtures.dff()
        assertTrue(EchoDffLayout.sniff(file.copyOf(16)))
        assertFalse(EchoDffLayout.sniff("FORM".toByteArray() + ByteArray(12)))
    }
}

package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoDsdRatesTest {
    @Test
    fun familyLabelUsesDsd64Multiples() {
        assertEquals("DSD64", EchoDsdRates.familyLabel(2_822_400))
        assertEquals("DSD128", EchoDsdRates.familyLabel(5_644_800))
        assertEquals("DSD256", EchoDsdRates.familyLabel(11_289_600))
        assertNull(EchoDsdRates.familyLabel(88_200))
        assertNull(EchoDsdRates.familyLabel(352_800))
        assertNull(EchoDsdRates.familyLabel(96_000))
        assertNull(EchoDsdRates.familyLabel(0))
    }

    @Test
    fun outputPcmMapsDsd64To88200AndHigherTo176400() {
        assertEquals(88_200, EchoDsdRates.outputPcmRateHz(2_822_400))
        assertEquals(88_200, EchoDsdRates.outputPcmRateHz(352_800))
        assertEquals(88_200, EchoDsdRates.outputPcmRateHz(88_200))
        assertEquals(176_400, EchoDsdRates.outputPcmRateHz(705_600))
        assertEquals(176_400, EchoDsdRates.outputPcmRateHz(1_411_200))
        assertEquals(176_400, EchoDsdRates.outputPcmRateHz(5_644_800))
        assertEquals(0, EchoDsdRates.outputPcmRateHz(0))
    }

    @Test
    fun decoderPcmRateIsDsdDividedByEight() {
        assertEquals(352_800, EchoDsdRates.decoderPcmRateHz(2_822_400))
        assertEquals(705_600, EchoDsdRates.decoderPcmRateHz(5_644_800))
        assertTrue(EchoDsdRates.isDsdRate(2_822_400))
        assertFalse(EchoDsdRates.isDsdRate(44_100))
    }

    @Test
    fun decoderPcmRecoversDsdFamily() {
        assertEquals(2_822_400, EchoDsdRates.dsdRateFromDecoderPcm(352_800))
        assertEquals(5_644_800, EchoDsdRates.dsdRateFromDecoderPcm(705_600))
        assertNull(EchoDsdRates.dsdRateFromDecoderPcm(88_200))
        assertNull(EchoDsdRates.dsdRateFromDecoderPcm(48_000))
    }

    @Test
    fun dopRatesCoverDsd64AndDsd128Only() {
        assertEquals(176_400, EchoDsdRates.dopSampleRateHz(2_822_400))
        assertEquals(352_800, EchoDsdRates.dopSampleRateHz(5_644_800))
        assertNull(EchoDsdRates.dopSampleRateHz(11_289_600))
        assertNull(EchoDsdRates.dopSampleRateHz(88_200))
        assertEquals(176_400, EchoDsdRates.dopSampleRateFromDecoderPcm(352_800))
        assertEquals(352_800, EchoDsdRates.dopSampleRateFromDecoderPcm(705_600))
        assertNull(EchoDsdRates.dopSampleRateFromDecoderPcm(1_411_200))
    }

    @Test
    fun dopOutputMatchesDecodedRate() {
        val dop = EchoPlaybackDiagnostics(
            codec = "DSD",
            sampleRateHz = 2_822_400,
            decodedSampleRateHz = 176_400,
        )
        assertTrue(dop.isDsdDopOutput())
        val pcm = dop.copy(decodedSampleRateHz = 88_200)
        assertFalse(pcm.isDsdDopOutput())
    }
}

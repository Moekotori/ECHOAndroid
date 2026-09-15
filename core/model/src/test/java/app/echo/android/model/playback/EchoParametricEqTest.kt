package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoParametricEqTest {
    @Test
    fun keepsThirteenBandsAndPassSlope() {
        val peak = OpraEqBand(EchoEqFilterType.PeakDip, 1_000f, 2f, 1.2f, null)
        val highPass = OpraEqBand(EchoEqFilterType.HighPass, 20f, 0f, null, 24f)
        val filters = List(12) { peak } + highPass
        val sanitized = EchoParametricEq.sanitize(filters)
        assertEquals(13, sanitized!!.size)
        assertEquals(24f, sanitized.last().slope)
        assertNull(sanitized.last().q)
    }

    @Test
    fun rejectsEmptyOrTooManyAndUnknownTypes() {
        assertNull(EchoParametricEq.sanitize(emptyList()))
        assertNull(EchoParametricEq.sanitize(List(EchoParametricEq.MaxBands + 1) {
            OpraEqBand(EchoEqFilterType.PeakDip, 1_000f, 0f, 1f, null)
        }))
        assertNull(EchoParametricEq.sanitize(listOf(OpraEqBand("mystery", 1_000f, 0f, 1f, null))))
        assertNull(EchoParametricEq.sanitize(listOf(OpraEqBand(EchoEqFilterType.PeakDip, Float.NaN, 0f, 1f, null))))
    }

    @Test
    fun clampsFiniteOutOfRangeValuesWithoutInventingPassQ() {
        val sanitized = EchoParametricEq.sanitizeBand(
            OpraEqBand(EchoEqFilterType.PeakDip, 50_000f, 25f, 30f, 12f),
        )
        assertEquals(20_000f, sanitized!!.frequencyHz, 0.01f)
        assertEquals(EchoParametricEq.MaxGainDb, sanitized.gainDb, 0.01f)
        assertEquals(30f, sanitized.q!!, 0.01f)
        assertNull(sanitized.slope)

        val pass = EchoParametricEq.sanitizeBand(
            OpraEqBand(EchoEqFilterType.LowPass, 18_000f, 0f, null, 36f),
        )
        assertEquals(36f, pass!!.slope)
        assertNull(pass.q)
        assertTrue(EchoParametricEq.snapSlope(22f) == 24f)
    }
}

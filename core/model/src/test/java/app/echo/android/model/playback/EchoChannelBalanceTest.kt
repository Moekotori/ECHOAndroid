package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EchoChannelBalanceTest {
    @Test
    fun centerConstantPowerKeepsUnityGains() {
        val gains = FloatArray(2)
        EchoChannelBalance.writeBalanceGains(0f, 0f, 0f, gains)
        assertEquals(1f, gains[0], 0.002f)
        assertEquals(1f, gains[1], 0.002f)
    }

    @Test
    fun fullRightMutesLeftAndKeepsRight() {
        val gains = FloatArray(2)
        EchoChannelBalance.writeBalanceGains(1f, 0f, 0f, gains)
        assertEquals(0f, gains[0], 0.002f)
        assertEquals(1f, gains[1], 0.002f)
    }

    @Test
    fun fullLeftMutesRightAndKeepsLeft() {
        val gains = FloatArray(2)
        EchoChannelBalance.writeBalanceGains(-1f, 0f, 0f, gains)
        assertEquals(1f, gains[0], 0.002f)
        assertEquals(0f, gains[1], 0.002f)
    }

    @Test
    fun trimMultipliesBalanceGains() {
        val gains = FloatArray(2)
        EchoChannelBalance.writeBalanceGains(0f, 6f, -6f, gains)
        assertEquals(EchoChannelBalance.dbToLinear(6f), gains[0], 0.002f)
        assertEquals(EchoChannelBalance.dbToLinear(-6f), gains[1], 0.002f)
    }

    @Test
    fun swapAndMonoFollowPcOrder() {
        val dest = FloatArray(2)
        EchoChannelBalance.applyFrameTo(
            left = 0.8f,
            right = 0.2f,
            leftGain = 1f,
            rightGain = 1f,
            swapLeftRight = true,
            invertLeft = false,
            invertRight = false,
            monoMode = EchoChannelBalanceMonoMode.Off,
            dest = dest,
        )
        assertEquals(0.2f, dest[0], 0.0001f)
        assertEquals(0.8f, dest[1], 0.0001f)

        EchoChannelBalance.applyFrameTo(
            left = 0.8f,
            right = 0.2f,
            leftGain = 1f,
            rightGain = 1f,
            swapLeftRight = false,
            invertLeft = false,
            invertRight = false,
            monoMode = EchoChannelBalanceMonoMode.Sum,
            dest = dest,
        )
        assertEquals(0.5f, dest[0], 0.0001f)
        assertEquals(0.5f, dest[1], 0.0001f)

        EchoChannelBalance.applyFrameTo(
            left = 0.8f,
            right = 0.2f,
            leftGain = 1f,
            rightGain = 1f,
            swapLeftRight = false,
            invertLeft = false,
            invertRight = false,
            monoMode = EchoChannelBalanceMonoMode.Left,
            dest = dest,
        )
        assertEquals(0.8f, dest[0], 0.0001f)
        assertEquals(0f, dest[1], 0.0001f)
    }

    @Test
    fun defaultsDoNotAffectTheSignal() {
        assertFalse(EchoChannelBalanceState().affectsSignal)
        assertFalse(EchoChannelBalanceState(enabled = true).active)
        assertTrue(EchoChannelBalanceState(enabled = true, balance = 0.2f).active)
        assertTrue(EchoChannelBalanceState(enabled = true, swapLeftRight = true).active)
        assertFalse(EchoChannelBalance.clippingRisk(EchoChannelBalanceState()))
        assertTrue(EchoChannelBalance.clippingRisk(EchoChannelBalanceState(leftGainDb = 6f)))
    }

    @Test
    fun clampsNonFiniteAndOutOfRange() {
        assertEquals(0f, EchoChannelBalance.clampBalance(Float.NaN), 0f)
        assertEquals(-1f, EchoChannelBalance.clampBalance(-8f), 0f)
        assertEquals(6f, EchoChannelBalance.clampGainDb(40f), 0f)
        assertEquals(EchoChannelBalanceMonoMode.Off, EchoChannelBalanceMonoMode.fromId("nope"))
        assertEquals(0f, abs(EchoChannelBalanceState(balance = 4f).normalized.balance - 1f), 0f)
    }

    @Test
    fun linearPanCutsTheQuieterSideOnly() {
        val gains = FloatArray(2)
        EchoChannelBalance.writeBalanceGains(0.5f, 0f, 0f, gains, constantPower = false)
        assertEquals(0.5f, gains[0], 0.002f)
        assertEquals(1f, gains[1], 0.002f)
    }

    @Test
    fun invertFlipsPolarityAfterSwap() {
        val dest = FloatArray(2)
        EchoChannelBalance.applyFrameTo(
            left = 0.4f,
            right = -0.2f,
            leftGain = 1f,
            rightGain = 1f,
            swapLeftRight = false,
            invertLeft = true,
            invertRight = false,
            monoMode = EchoChannelBalanceMonoMode.Off,
            dest = dest,
        )
        assertEquals(-0.4f, dest[0], 0.0001f)
        assertEquals(-0.2f, dest[1], 0.0001f)
    }

    @Test
    fun flatBandsReconstructTheSample() {
        val low = FloatArray(1)
        val highLp = FloatArray(1)
        val bands = floatArrayOf(0f, 0f, 0f)
        val alphaLow = EchoChannelBalance.onePoleAlpha(200f, 48_000f)
        val alphaHigh = EchoChannelBalance.onePoleAlpha(2_000f, 48_000f)
        var last = 0f
        repeat(64) {
            last = EchoChannelBalance.applyBandCompensation(0.25f, alphaLow, alphaHigh, bands, low, highLp)
        }
        assertEquals(0.25f, last, 0.02f)
    }

    @Test
    fun zeroDelayReadsTheSampleJustWritten() {
        val history = FloatArray(8)
        history[3] = 0.7f
        val delayed = EchoChannelBalance.readDelaySample(history, writeIndex = 3, delayMs = 0f, sampleRateHz = 48_000f)
        assertEquals(0.7f, delayed, 0.0001f)
    }

    @Test
    fun enabledCenterStaysInThePipelineButIsNotActive() {
        val centered = EchoChannelBalanceState(enabled = true)
        assertTrue(EchoChannelBalance.shouldProcess(centered))
        assertFalse(centered.active)
        assertTrue(EchoChannelBalance.shouldProcess(EchoChannelBalanceState(enabled = true, leftDelayMs = 1.5f)))
        assertTrue(EchoChannelBalanceState(enabled = true, invertLeft = true).active)
    }
}

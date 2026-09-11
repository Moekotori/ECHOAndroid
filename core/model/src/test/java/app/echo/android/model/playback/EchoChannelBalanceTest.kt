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
        assertFalse(EchoChannelBalance.clippingRisk(0f, 0f, 0f))
        assertTrue(EchoChannelBalance.clippingRisk(0f, 6f, 0f))
    }

    @Test
    fun clampsNonFiniteAndOutOfRange() {
        assertEquals(0f, EchoChannelBalance.clampBalance(Float.NaN), 0f)
        assertEquals(-1f, EchoChannelBalance.clampBalance(-8f), 0f)
        assertEquals(6f, EchoChannelBalance.clampGainDb(40f), 0f)
        assertEquals(EchoChannelBalanceMonoMode.Off, EchoChannelBalanceMonoMode.fromId("nope"))
        assertEquals(0f, abs(EchoChannelBalanceState(balance = 4f).normalized.balance - 1f), 0f)
    }
}

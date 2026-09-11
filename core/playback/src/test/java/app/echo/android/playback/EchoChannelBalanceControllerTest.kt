package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoChannelBalanceMonoMode
import app.echo.android.model.playback.EchoChannelBalanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EchoChannelBalanceControllerTest {
    @Test
    fun enabledCenterDoesNotBecomeActiveUntilTheImageMoves() {
        val controller = EchoChannelBalanceController()
        controller.setState(EchoChannelBalanceState(enabled = true))
        assertTrue(controller.state.value.enabled)
        assertFalse(controller.state.value.active)
        controller.setState(EchoChannelBalanceState(enabled = true, balance = -0.25f))
        assertTrue(controller.state.value.active)
        assertEquals(-0.25f, controller.state.value.balance, 0.0001f)
    }

    @Test
    fun resetClearsRoutingAndTrim() {
        val controller = EchoChannelBalanceController()
        controller.setState(
            EchoChannelBalanceState(
                enabled = true,
                balance = 0.4f,
                leftGainDb = 3f,
                rightGainDb = -2f,
                swapLeftRight = true,
                monoMode = EchoChannelBalanceMonoMode.Sum,
            ),
        )
        controller.reset()
        assertEquals(EchoChannelBalanceState(), controller.state.value.copy(processingSampleRateHz = null))
    }

    @Test
    fun clampsOutOfRangeInputs() {
        val controller = EchoChannelBalanceController()
        controller.setState(EchoChannelBalanceState(enabled = true, balance = 4f, leftGainDb = 40f, rightGainDb = -40f))
        val state = controller.state.value
        assertEquals(1f, state.balance, 0f)
        assertEquals(6f, state.leftGainDb, 0f)
        assertEquals(-12f, state.rightGainDb, 0f)
    }
}

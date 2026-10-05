package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoChannelBalanceMonoMode
import app.echo.android.model.playback.EchoChannelBalanceState
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAccessibilityListeningTest {
    private val custom = EchoChannelBalanceState(
        enabled = false, balance = -0.2f, leftGainDb = -3f, rightGainDb = -2f,
        swapLeftRight = true, monoMode = EchoChannelBalanceMonoMode.Left,
        leftBandGainsDb = listOf(-1f, 0f, 1f), rightDelayMs = 2f,
    )

    @Test
    fun monoTogglePreservesOtherChannelAdjustments() {
        val enabled = custom.withAccessibilityMono(true)
        assertEquals(custom.copy(enabled = true, monoMode = EchoChannelBalanceMonoMode.Sum), enabled)
        assertEquals(enabled.copy(monoMode = EchoChannelBalanceMonoMode.Off), enabled.withAccessibilityMono(false))
    }

    @Test
    fun balanceEnablesProcessingWithoutDiscardingRoutingOrGains() {
        assertEquals(custom.copy(enabled = true, balance = 1f), custom.withAccessibilityBalance(2f))
        assertEquals(custom.copy(enabled = true, balance = 0f), custom.withAccessibilityBalance(Float.NaN))
    }
}

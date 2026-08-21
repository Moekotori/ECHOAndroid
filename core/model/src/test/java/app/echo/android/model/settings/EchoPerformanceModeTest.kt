package app.echo.android.model.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoPerformanceModeTest {
    @Test
    fun autoFollowsBatterySaver() {
        assertEquals(
            EchoEffectivePerformanceMode.Lightweight,
            EchoPerformanceMode.Auto.resolve(systemPowerSaveMode = true),
        )
        assertEquals(
            EchoEffectivePerformanceMode.Balanced,
            EchoPerformanceMode.Auto.resolve(systemPowerSaveMode = false),
        )
    }

    @Test
    fun highPerformanceFallsBackToBalancedInBatterySaver() {
        assertEquals(
            EchoEffectivePerformanceMode.Balanced,
            EchoPerformanceMode.HighPerformance.resolve(systemPowerSaveMode = true),
        )
        assertEquals(
            EchoEffectivePerformanceMode.HighPerformance,
            EchoPerformanceMode.HighPerformance.resolve(systemPowerSaveMode = false),
        )
    }

    @Test
    fun explicitLightweightAndBalancedStayPut() {
        assertEquals(
            EchoEffectivePerformanceMode.Lightweight,
            EchoPerformanceMode.Lightweight.resolve(systemPowerSaveMode = false),
        )
        assertEquals(
            EchoEffectivePerformanceMode.Lightweight,
            EchoPerformanceMode.Lightweight.resolve(systemPowerSaveMode = true),
        )
        assertEquals(
            EchoEffectivePerformanceMode.Balanced,
            EchoPerformanceMode.Balanced.resolve(systemPowerSaveMode = true),
        )
    }
}

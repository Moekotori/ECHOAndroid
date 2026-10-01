package app.echo.android.design

import org.junit.Assert.*
import org.junit.Test

class EchoSeekHapticGateTest {
    @Test fun aLongQueueRetainsEachRowAsADetentAndCancellationResetsTheGate() {
        val gate = EchoSeekHapticGate()
        assertFalse(gate.shouldPulseStep(200, 0))
        assertTrue(gate.shouldPulseStep(201, 100))
        assertTrue(gate.shouldPulseStep(202, 200))
        assertFalse(gate.shouldPulseStep(202, 300))
        gate.reset()
        assertFalse(gate.shouldPulseStep(300, 400))
        assertTrue(gate.shouldPulseStep(301, 500))
    }
    @Test fun dragPulsesOnlyAtNewDetentsAndAtMostEvery65ms() {
        val gate = EchoSeekHapticGate()
        assertFalse(gate.shouldPulse(0f, 0))
        assertFalse(gate.shouldPulse(0.01f, 1))
        assertTrue(gate.shouldPulse(0.10f, 10))
        assertFalse(gate.shouldPulse(0.50f, 20))
        assertTrue(gate.shouldPulse(0.60f, 75))
        assertFalse(gate.shouldPulse(0.60f, 500))
        gate.reset()
        assertFalse(gate.shouldPulse(0.80f, 600))
        assertFalse(gate.shouldPulse(Float.NaN, 700))
        assertTrue(gate.shouldPulse(1f, 800))
    }
}

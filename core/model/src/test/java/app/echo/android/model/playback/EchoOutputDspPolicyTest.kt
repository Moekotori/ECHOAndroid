package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoOutputDspPolicyTest {
    @Test
    fun deviceKeyIncludesNameWhenPresent() {
        assertEquals("bluetooth", EchoOutputDspPolicy.deviceKey(EchoOutputDeviceKind.Bluetooth, "  "))
        assertEquals(
            "bluetooth:WH-1000XM5",
            EchoOutputDspPolicy.deviceKey(EchoOutputDeviceKind.Bluetooth, "WH-1000XM5"),
        )
    }

    @Test
    fun resolvePrefersExactDeviceThenKindFallback() {
        val bindings = mapOf(
            "bluetooth" to "kind-preset",
            "bluetooth:WH-1000XM5" to "xm5",
        )
        assertEquals("xm5", EchoOutputDspPolicy.resolvePresetId(bindings, "bluetooth:WH-1000XM5"))
        assertEquals("kind-preset", EchoOutputDspPolicy.resolvePresetId(bindings, "bluetooth:Other"))
        assertNull(EchoOutputDspPolicy.resolvePresetId(bindings, "usb:DAC"))
    }

    @Test
    fun unbindRemovesExactKeyOnly() {
        val map = mapOf(
            "bluetooth:WH-1000XM5" to "xm5",
            "bluetooth" to "kind-preset",
        )
        assertEquals(
            mapOf("bluetooth" to "kind-preset"),
            EchoOutputDspPolicy.unbind(map, "bluetooth:WH-1000XM5"),
        )
    }
}

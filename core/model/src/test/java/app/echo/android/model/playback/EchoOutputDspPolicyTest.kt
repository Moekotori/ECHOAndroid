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
    fun bindCapsAndReplaces() {
        var map = emptyMap<String, String>()
        repeat(EchoOutputDspPolicy.MaxBindings + 2) { index ->
            map = EchoOutputDspPolicy.bind(map, "bluetooth:dev$index", "p$index")
        }
        assertEquals(EchoOutputDspPolicy.MaxBindings, map.size)
        val first = map.keys.first()
        map = EchoOutputDspPolicy.bind(map, first, "replaced")
        assertEquals("replaced", map[first])
    }
}

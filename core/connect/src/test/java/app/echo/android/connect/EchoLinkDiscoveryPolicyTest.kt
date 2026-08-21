package app.echo.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkDiscoveryPolicyTest {
    @Test
    fun resolvedServiceBecomesLanDeviceWithoutToken() {
        val device = EchoLinkDiscoveryPolicy.deviceFromResolved(
            serviceName = "pc-echo-office",
            host = "192.168.1.12",
            port = 26789,
            txt = mapOf(
                "name" to "Office ECHO",
                "version" to "1",
                "deviceId" to "pc-device-id",
            ),
        )
        checkNotNull(device)
        assertEquals("pc-device-id", device.id)
        assertEquals("Office ECHO", device.name)
        assertEquals("192.168.1.12", device.host)
        assertEquals(26789, device.port)
        assertTrue(device.requiresPairing)
        assertEquals("192.168.1.12:26789", EchoLinkDiscoveryPolicy.addressLabel(device))
    }

    @Test
    fun missingHostIsRejected() {
        assertNull(
            EchoLinkDiscoveryPolicy.deviceFromResolved(
                serviceName = "blank",
                host = "  ",
                port = 26789,
                txt = emptyMap(),
            ),
        )
    }

    @Test
    fun upsertReplacesSameIdAndRemoveDropsService() {
        val first = EchoLinkDiscoveryPolicy.deviceFromResolved(
            serviceName = "a",
            host = "192.168.0.2",
            port = 26789,
            txt = mapOf("deviceId" to "pc-1", "name" to "A"),
        )!!
        val updated = first.copy(name = "A2", host = "192.168.0.9")
        val merged = EchoLinkDiscoveryPolicy.upsertDevice(listOf(first), updated)
        assertEquals(1, merged.size)
        assertEquals("A2", merged.single().name)
        assertEquals("192.168.0.9", merged.single().host)
        assertTrue(EchoLinkDiscoveryPolicy.removeService(merged, "a").isEmpty())
    }

    @Test
    fun txtBytesDecodeUtf8Values() {
        val decoded = EchoLinkDiscoveryPolicy.decodeTxt(
            mapOf(
                "name" to "PC ECHO".toByteArray(),
                "empty" to ByteArray(0),
                " " to "x".toByteArray(),
            ),
        )
        assertEquals(mapOf("name" to "PC ECHO"), decoded)
    }
}

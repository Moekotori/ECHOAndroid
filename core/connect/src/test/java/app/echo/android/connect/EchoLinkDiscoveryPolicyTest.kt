package app.echo.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun pickHostPrefersIpv4AndSkipsLinkLocalIpv6() {
        assertEquals(
            "192.168.1.12",
            EchoLinkDiscoveryPolicy.pickHost(listOf("fe80::1%wlan0", "192.168.1.12", "10.0.0.2")),
        )
        assertEquals(
            "2001:db8::1",
            EchoLinkDiscoveryPolicy.pickHost(listOf("fe80::1%wlan0", "2001:db8::1")),
        )
        assertNull(EchoLinkDiscoveryPolicy.pickHost(listOf("  ", null)))
    }

    @Test
    fun savedTokenIsReusedOnlyForTheSameLanAddress() {
        val device = EchoLinkDiscoveryPolicy.deviceFromResolved(
            serviceName = "pc",
            host = "192.168.1.12",
            port = 26789,
            txt = mapOf("deviceId" to "pc-1", "name" to "Office"),
        )!!
        assertEquals(
            "saved-token-value",
            EchoLinkDiscoveryPolicy.tokenAfterSelecting(
                device = device,
                savedAddress = "192.168.1.12:26789",
                savedToken = "saved-token-value",
            ),
        )
        assertEquals(
            "",
            EchoLinkDiscoveryPolicy.tokenAfterSelecting(
                device = device,
                savedAddress = "192.168.1.99:26789",
                savedToken = "saved-token-value",
            ),
        )
        assertTrue(EchoLinkDiscoveryPolicy.addressMatchesDevice("http://192.168.1.12:26789/echo-link", device))
        assertFalse(EchoLinkDiscoveryPolicy.addressMatchesDevice("192.168.1.120:26789", device))
    }

    @Test
    fun ipv6AddressUsesBracketsAndReusesSavedToken() {
        val device = EchoLinkDiscoveryPolicy.deviceFromResolved(
            serviceName = "pc",
            host = "2001:db8::1",
            port = 26789,
            txt = mapOf("deviceId" to "pc-v6", "name" to "Office"),
        )!!
        assertEquals("[2001:db8::1]:26789", EchoLinkDiscoveryPolicy.addressLabel(device))
        val unlabeled = EchoLinkDiscoveryPolicy.deviceFromResolved(
            serviceName = "pc-unlabeled",
            host = "2001:db8::1",
            port = 26789,
            txt = mapOf("name" to "Office"),
        )!!
        assertEquals("[2001:db8::1]:26789", unlabeled.id)
        assertTrue(
            EchoLinkDiscoveryPolicy.addressMatchesDevice("http://[2001:db8::1]:26789/echo-link", device),
        )
        assertEquals(
            "saved-token-value",
            EchoLinkDiscoveryPolicy.tokenAfterSelecting(
                device = device,
                savedAddress = "[2001:db8::1]:26789",
                savedToken = "saved-token-value",
            ),
        )
        assertEquals(
            "2001:db8::1" to 26789,
            EchoLinkDiscoveryPolicy.parseLanHostPort("http://[2001:db8::1]:26789/"),
        )
    }

    @Test
    fun upsertReplacesTheSameHostAndPort() {
        val first = EchoLinkDiscoveryPolicy.deviceFromResolved(
            serviceName = "a",
            host = "192.168.0.2",
            port = 26789,
            txt = mapOf("deviceId" to "pc-1", "name" to "A"),
        )!!
        val sameEndpoint = EchoLinkDiscoveryPolicy.deviceFromResolved(
            serviceName = "b",
            host = "192.168.0.2",
            port = 26789,
            txt = mapOf("deviceId" to "pc-2", "name" to "B"),
        )!!
        val merged = EchoLinkDiscoveryPolicy.upsertDevice(listOf(first), sameEndpoint)
        assertEquals(1, merged.size)
        assertEquals("B", merged.single().name)
        assertEquals("pc-2", merged.single().id)
    }
}

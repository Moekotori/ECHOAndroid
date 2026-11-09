package app.echo.android.connect

import app.echo.android.model.connect.EchoSavedPcEndpoint
import org.junit.Assert.*
import org.junit.Test

class EchoManualConnectionInputTest {
    @Test fun ipTailscaleAndFrpReconnectKeepTheirPairedV2Capabilities() {
        for (address in listOf("192.168.1.20:26789", "100.80.10.20:26789",
            "echo-pc.tailnet.ts.net:26789", "http://frp.example:36789", "https://echo.example")) {
            val request = EchoManualConnectionInput.resolve(address)!!
            val saved = EchoSavedPcEndpoint(request.address, "PC", supportsV2Events = true, token = "paired-access-token")
            val resolved = EchoManualConnectionInput.resolve(address, listOf(saved))!!
            val endpoint = EchoManualConnectionInput.endpointForConnection(resolved.address, resolved.token, listOf(saved))!!
            assertTrue(address, endpoint.supportsV2Events)
            assertEquals("paired-access-token", endpoint.token)
            assertEquals(request.address, "${endpoint.scheme}://${endpoint.host}:${endpoint.port}")
        }
    }

    @Test fun v2CapabilitiesCannotTransferToAnotherOriginOrCredential() {
        val saved = EchoSavedPcEndpoint("https://echo.example:9443", "PC", supportsV2Events = true, token = "paired-access-token")
        for (address in listOf("http://echo.example:9443", "https://other.example:9443", "https://echo.example:9444")) {
            assertFalse(EchoManualConnectionInput.endpointForConnection(address, "paired-access-token", listOf(saved))!!.supportsV2Events)
        }
        assertFalse(EchoManualConnectionInput.endpointForConnection(saved.address, "different-token", listOf(saved))!!.supportsV2Events)
        assertFalse(EchoManualConnectionInput.endpointForConnection(saved.address, "", listOf(saved))!!.supportsV2Events)
        assertTrue(EchoManualConnectionInput.endpointForConnection(saved.address, saved.token!!, currentPc = saved)!!.supportsV2Events)
    }

    @Test fun ipAndMagicDnsUseTheEnteredPortAndKeepProtocolDefaults() {
        assertEquals("http://192.168.1.20:26789", EchoManualConnectionInput.resolve("192.168.1.20")!!.address)
        assertEquals("http://100.80.10.20:31555", EchoManualConnectionInput.resolve("100.80.10.20:31555")!!.address)
        assertEquals("http://echo-pc.tailnet.ts.net:31555", EchoManualConnectionInput.resolve("echo-pc.tailnet.ts.net:31555")!!.address)
        assertEquals("https://echo-pc.tailnet.ts.net:443", EchoManualConnectionInput.resolve("https://echo-pc.tailnet.ts.net")!!.address)
        assertEquals("https://music.example:63369", EchoManualConnectionInput.resolve("https://music.example:63369")!!.address)
    }

    @Test fun savedCredentialsStayBoundToTheirExactOrigin() {
        val saved = listOf(EchoSavedPcEndpoint("https://echo-pc.tailnet.ts.net:9443", "PC", token = "saved-access-token"))
        assertEquals("saved-access-token", EchoManualConnectionInput.resolve("https://ECHO-PC.tailnet.ts.net:9443", saved)!!.token)
        assertEquals("", EchoManualConnectionInput.resolve("http://echo-pc.tailnet.ts.net:9443", saved)!!.token)
        assertEquals("", EchoManualConnectionInput.resolve("https://other.tailnet.ts.net:9443", saved)!!.token)
        assertEquals("", EchoManualConnectionInput.resolve("https://echo-pc.tailnet.ts.net:9444", saved)!!.token)
    }

    @Test fun currentPcCredentialsTakePriorityForTheSameAddress() {
        val previous = EchoSavedPcEndpoint("http://192.168.1.20:26789", "PC", token = "old-access-token")
        val current = previous.copy(token = "current-access-token")
        assertEquals("current-access-token", EchoManualConnectionInput.resolve("192.168.1.20", listOf(previous), current)!!.token)
    }

    @Test fun pairingLinksKeepTheirDestination() {
        val pairing = "echo://pair?version=2&host=pc.example&port=9443&scheme=https&pairingId=pair&secret=temporary-secret"
        val request = EchoManualConnectionInput.resolve(pairing)!!
        assertEquals(pairing, request.address)
        assertEquals("", request.token)
        assertEquals(9443, EchoPairingParser.parseManual(request.address, request.token)!!.port)
        assertTrue(EchoPairingParser.parseManual(request.address, request.token)!!.needsV2PairExchange)
    }

    @Test fun invalidPortsAndCredentialBearingUrlsCannotBecomeRequests() {
        for (port in listOf("0", "65536", "-1", "1.5", "abc")) {
            assertNull(EchoManualConnectionInput.resolve("100.80.10.20:$port"))
        }
        assertNull(EchoManualConnectionInput.resolve("https://user:secret@pc.example"))
        assertEquals("", EchoManualConnectionInput.resolve("100.80.10.20")!!.token)
    }
}

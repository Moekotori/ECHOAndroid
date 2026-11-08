package app.echo.android.connect

import app.echo.android.model.connect.EchoSavedPcEndpoint
import org.junit.Assert.*
import org.junit.Test

class EchoManualConnectionInputTest {
    @Test fun ipAndMagicDnsUseTheEnteredPortAndKeepProtocolDefaults() {
        assertEquals("http://192.168.1.20:26789", EchoManualConnectionInput.resolve("192.168.1.20", "")!!.address)
        assertEquals("http://100.80.10.20:31555", EchoManualConnectionInput.resolve("100.80.10.20", "31555")!!.address)
        assertEquals("http://echo-pc.tailnet.ts.net:31555", EchoManualConnectionInput.resolve("echo-pc.tailnet.ts.net", "31555")!!.address)
        assertEquals("https://echo-pc.tailnet.ts.net:443", EchoManualConnectionInput.resolve("https://echo-pc.tailnet.ts.net", "")!!.address)
        assertEquals("https://music.example:63369", EchoManualConnectionInput.resolve("https://music.example:63369", "")!!.address)
    }

    @Test fun savedCredentialsStayBoundToTheirExactOrigin() {
        val saved = listOf(EchoSavedPcEndpoint("https://echo-pc.tailnet.ts.net:9443", "PC", token = "saved-access-token"))
        assertEquals("saved-access-token", EchoManualConnectionInput.resolve("https://ECHO-PC.tailnet.ts.net", "9443", saved)!!.token)
        assertEquals("", EchoManualConnectionInput.resolve("http://echo-pc.tailnet.ts.net", "9443", saved)!!.token)
        assertEquals("", EchoManualConnectionInput.resolve("https://other.tailnet.ts.net", "9443", saved)!!.token)
        assertEquals("", EchoManualConnectionInput.resolve("https://echo-pc.tailnet.ts.net", "9444", saved)!!.token)
    }

    @Test fun currentPcCredentialsTakePriorityForTheSameAddress() {
        val previous = EchoSavedPcEndpoint("http://192.168.1.20:26789", "PC", token = "old-access-token")
        val current = previous.copy(token = "current-access-token")
        assertEquals("current-access-token", EchoManualConnectionInput.resolve("192.168.1.20", "", listOf(previous), current)!!.token)
    }

    @Test fun pairingLinksKeepTheirDestinationEvenWhenThePortFieldHasOldInput() {
        val pairing = "echo://pair?version=2&host=pc.example&port=9443&scheme=https&pairingId=pair&secret=temporary-secret"
        val request = EchoManualConnectionInput.resolve(pairing, "31555")!!
        assertEquals(pairing, request.address)
        assertEquals("", request.token)
        assertEquals(9443, EchoPairingParser.parseManual(request.address, request.token)!!.port)
        assertTrue(EchoPairingParser.parseManual(request.address, request.token)!!.needsV2PairExchange)
    }

    @Test fun invalidPortsAndCredentialBearingUrlsCannotBecomeRequests() {
        for (port in listOf("0", "65536", "-1", "1.5", "abc")) {
            assertNull(EchoManualConnectionInput.resolve("100.80.10.20", port))
        }
        assertNull(EchoManualConnectionInput.resolve("https://user:secret@pc.example", ""))
        assertEquals("", EchoManualConnectionInput.resolve("100.80.10.20", "")!!.token)
    }
}

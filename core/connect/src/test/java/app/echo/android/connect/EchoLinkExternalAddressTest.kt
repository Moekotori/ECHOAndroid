package app.echo.android.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoLinkExternalAddressTest {
    @Test fun explicitTunnelUrlsUseTheirProtocolPortWhileBareAddressesKeepTheLinkDefault() {
        assertEquals(443, EchoPairingParser.parseManual("https://pc.example")!!.port)
        assertEquals(80, EchoPairingParser.parseManual("http://pc.example")!!.port)
        assertEquals(31234, EchoPairingParser.parseManual("https://pc.example:31234")!!.port)
        assertEquals(26789, EchoPairingParser.parseManual("100.101.102.103")!!.port)
        assertEquals(26789, EchoPairingParser.parseManual("desktop.tailnet.ts.net")!!.port)
    }
}

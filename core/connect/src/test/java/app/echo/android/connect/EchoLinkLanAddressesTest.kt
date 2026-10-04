package app.echo.android.connect

import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Assert.*
import org.junit.Test

class EchoLinkLanAddressesTest {
    @Test fun onlyPublishesReachableIpv4Addresses() {
        assertEquals("192.168.1.5", EchoLinkLanAddresses.shareableIpv4(InetAddress.getByName("192.168.1.5")))
        for (host in listOf("127.0.0.1", "0.0.0.0", "::1", "fd00::1")) {
            assertNull(EchoLinkLanAddresses.shareableIpv4(InetAddress.getByName(host)))
        }
    }

    @Test fun probesThePeerRouteAndRejectsLoopbackInsteadOfFallingBackToAnotherInterface() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { peer ->
            assertNull(EchoLinkLanAddresses.ipv4ForPeer("127.0.0.1", peer.localPort))
        }
    }
}

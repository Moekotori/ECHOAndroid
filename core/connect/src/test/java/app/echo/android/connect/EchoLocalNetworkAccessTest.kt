package app.echo.android.connect

import org.junit.Assert.*
import org.junit.Test

class EchoLocalNetworkAccessTest {
    @Test fun localSourcesRequireAccessWithoutResolvingHostnames() {
        listOf("192.168.1.3:9000", "http://10.0.0.4/music", "smb://172.16.2.5/share",
            "https://echo.local:443", "nas", "http://[fd12::10]:9000", "http://[fe80::1]:9000").forEach {
            assertTrue(it, echoAddressNeedsLocalNetworkAccess(it))
        }
    }
    @Test fun internetLibrariesAndLoopbackDoNotPromptForNearbyDevices() {
        listOf("https://music.example.com", "https://8.8.8.8:443", "http://172.32.1.2:9000",
            "http://localhost:9000", "http://127.0.0.1:9000", "http://[::1]:9000", "http://[2001:4860::1]").forEach {
            assertFalse(it, echoAddressNeedsLocalNetworkAccess(it))
        }
    }
    @Test fun invalidAddressReachesExistingValidationWithoutAPermissionPrompt() {
        assertFalse(echoAddressNeedsLocalNetworkAccess("not a server"))
    }
}

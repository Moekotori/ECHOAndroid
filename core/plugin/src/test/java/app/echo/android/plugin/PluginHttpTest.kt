package app.echo.android.plugin

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PluginHttpTest {
    @Test
    fun rejectsNonHttpAndCredentials() {
        assertFalse(PluginHttp.fetch("file:///tmp/secret", { unexpected() }).ok)
        assertFalse(PluginHttp.fetch("javascript:alert(1)", { unexpected() }).ok)
        assertFalse(PluginHttp.fetch("https://user:pass@example.com/a", { unexpected() }).ok)
        assertEquals("invalid", PluginHttp.fetch("file:///tmp/secret", { unexpected() }).error)
    }

    @Test
    fun followsOnlyHttpRedirectsAndCapsTheBody() {
        val seen = mutableListOf<URI>()
        val response = PluginHttp.fetch("https://example.com/start") { uri ->
            seen += uri
            if (uri.path == "/start") {
                PluginHttpExchange(302, "https://example.com/next", ByteArray(0))
            } else {
                PluginHttpExchange(200, null, ByteArray(PluginHttp.MaxBodyBytes + 50) { 'a'.code.toByte() })
            }
        }
        assertEquals(listOf(URI("https://example.com/start"), URI("https://example.com/next")), seen)
        assertEquals(200, response.status)
        assertEquals(PluginHttp.MaxBodyBytes, response.body.length)
    }

    @Test
    fun stopsWhenARedirectLeavesHttp() {
        val response = PluginHttp.fetch("https://example.com/start") {
            PluginHttpExchange(302, "file:///tmp/secret", ByteArray(0))
        }
        assertEquals("invalid", response.error)
    }

    private fun unexpected(): PluginHttpExchange = error("should not open")
}

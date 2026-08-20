package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class WebDavAuthorizationTest {
    @Test
    fun encodesNonAsciiPasswordAsUtf8Basic() {
        val header = webDavBasicAuthorization("用户", "密码")
        assertTrue(header.startsWith("Basic "))
        val decoded = String(
            Base64.getDecoder().decode(header.removePrefix("Basic ")),
            StandardCharsets.UTF_8,
        )
        assertEquals("用户:密码", decoded)
    }
}

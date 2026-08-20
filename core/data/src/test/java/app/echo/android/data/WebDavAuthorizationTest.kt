package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

class WebDavAuthorizationTest {
    @Test
    fun readsPropertiesFromSuccessfulPropstatInsteadOfTheFirstPropstat() {
        val entries = parseWebDavEntries(
            base = URI("https://dav.example/music/"),
            xml =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/music/album/</D:href>
                    <D:propstat>
                      <D:prop><D:getcontentlength/></D:prop>
                      <D:status>HTTP/1.1 404 Not Found</D:status>
                    </D:propstat>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype><D:collection/></D:resourcetype>
                        <D:getcontenttype>httpd/unix-directory</D:getcontenttype>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent(),
        )

        assertEquals(1, entries.size)
        assertTrue(entries.single().isDirectory)
        assertEquals("httpd/unix-directory", entries.single().contentType)
    }

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

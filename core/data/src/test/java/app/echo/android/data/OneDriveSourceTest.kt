package app.echo.android.data

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OneDriveSourceTest {
    @Test
    fun acceptsOnlyTheMatchingLoopbackCallback() {
        val redirect = "http://localhost:43123/"
        val callback = parseOneDriveCallback(redirect, "/?code=encoded%2Bcode&state=expected", "expected")
        assertEquals("encoded+code", (callback as OneDriveCallback.Code).value)
        assertEquals(OneDriveCallback.Denied, parseOneDriveCallback(redirect, "/?error=access_denied&state=expected", "expected"))
        assertNull(parseOneDriveCallback(redirect, "/?code=wrong&state=wrong", "expected"))
        assertNull(parseOneDriveCallback(redirect, "/?code=one&code=two&state=expected", "expected"))
        assertNull(parseOneDriveCallback(redirect, "/other?code=one&state=expected", "expected"))
    }

    @Test
    fun continuationCannotSendTheTokenToAnotherOriginOrFolder() {
        val collection = "https://graph.microsoft.com/v1.0/me/drive/items/folder/children".toHttpUrl()
        val next = "$collection?\$skipToken=next"
        assertEquals(next, oneDrivePageAddress(collection, next).toString())
        for (invalid in listOf(
            next.replace("graph.microsoft.com", "example.com"),
            next.replace("/folder/", "/other/"),
            next.replace("https://", "http://"),
            next.replace("graph.microsoft.com", "graph.microsoft.com:444"),
        )) {
            assertThrows(IOException::class.java) { oneDrivePageAddress(collection, invalid) }
        }
    }
}

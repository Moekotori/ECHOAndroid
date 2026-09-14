package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoLinkStreamParseTest {
    private val endpoint = EchoRemoteEndpoint(
        id = "192.168.1.20:26789",
        name = "PC ECHO",
        host = "192.168.1.20",
        port = 26789,
        token = "abcdefghijklmnop",
    )

    @Test
    fun parsesStreamUrlAndExpiry() {
        val json = JSONObject(
            """
            {
              "streamUrl": "http://192.168.1.20:26789/echo-link/media/abc",
              "expiresAtEpochMs": 1780000300000,
              "track": { "id": "t1", "title": "Song", "artist": "Artist" }
            }
            """.trimIndent(),
        )
        val stream = json.toStreamResponse(endpoint)
        assertEquals("http://192.168.1.20:26789/echo-link/media/abc", stream.streamUrl)
        assertEquals(1_780_000_300_000L, stream.expiresAtEpochMs)
        assertEquals("t1", stream.track?.id)
    }

    @Test
    fun missingExpiryIsNotCached() {
        val json = JSONObject("""{"url":"http://pc/echo-link/media/x"}""")
        val stream = json.toStreamResponse(endpoint)
        assertEquals("http://pc/echo-link/media/x", stream.streamUrl)
        assertNull(stream.expiresAtEpochMs)
        assertFalseCache(stream.expiresAtEpochMs)
    }

    @Test
    fun jsonErrorBodyExposesMessage() {
        assertEquals(
            "This DSD source cannot be streamed to Android yet.",
            echoLinkErrorUserMessage(
                400,
                """{"code":"unsupported_format","message":"This DSD source cannot be streamed to Android yet."}""",
            ),
        )
        assertEquals(
            "PC ECHO request failed (503): stream_unavailable",
            echoLinkErrorUserMessage(503, "stream_unavailable"),
        )
    }

    private fun assertFalseCache(expiresAtEpochMs: Long?) {
        org.junit.Assert.assertFalse(EchoLinkStreamCachePolicy.shouldCache(expiresAtEpochMs))
    }
}

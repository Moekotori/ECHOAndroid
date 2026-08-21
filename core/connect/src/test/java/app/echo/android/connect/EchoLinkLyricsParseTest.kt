package app.echo.android.connect

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoLinkLyricsParseTest {
    @Test
    fun prefersSyncedLyricsOverPlainLyrics() {
        val json = JSONObject(
            """
            {
              "lyrics": "unsynced line",
              "syncedLyrics": "[00:01.00]synced line"
            }
            """.trimIndent(),
        )
        assertEquals("[00:01.00]synced line", echoLinkLyricsRawText(json))
    }

    @Test
    fun fallsBackToPlainLyricsWhenSyncedIsMissing() {
        val json = JSONObject(
            """
            { "lyrics": "plain" }
            """.trimIndent(),
        )
        assertEquals("plain", echoLinkLyricsRawText(json))
    }
}

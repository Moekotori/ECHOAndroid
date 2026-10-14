package app.echo.android.connect

import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EchoPhoneLyricsRoutesTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val headers = mapOf("authorization" to "Bearer $token")
    private val path = "/echo-link/phone/v1/library/lyrics?id=song%2F%E4%B8%80"
    private fun routes(read: (String) -> EchoPhoneLyrics?) = EchoPhoneLibraryRoutes(token, "http://127.0.0.1",
        { _, _, _ -> EchoPhoneLibraryPage(emptyList(), 0) }, { null }, lyrics = read)

    @Test fun sharesWordTimingTranslationsAndAppliedOffsetsWithoutPrivateMetadata() {
        val document = EchoLyrics(lines = listOf(EchoLyricLine(1120, 2000, "Hello", translation = "你好", romanization = "hello",
            words = listOf(EchoLyricWord(1150, 1800, "Hello")))), offsetMs = 120,
            sourceLabel = "content://private/lyrics", metadata = mapOf("provider_token" to "private-secret"))
        val response = routes { id ->
            assertEquals("song/一", id)
            EchoPhoneLyrics("Song", "Artist", "Album", 3000, document, userOffsetMs = 80)
        }.metadata(path, headers)!!
        assertEquals(200, response.status)
        val lyrics = JSONObject(response.body).getJSONObject("lyrics")
        assertEquals("synced", lyrics.getString("kind")); assertEquals(0, lyrics.getLong("offsetMs"))
        val line = lyrics.getJSONArray("lines").getJSONObject(0)
        assertEquals(1200, line.getLong("timeMs")); assertEquals("你好", line.getString("translation"))
        assertEquals(1230, line.getJSONArray("words").getJSONObject(0).getLong("startMs"))
        assertFalse(response.body.contains("private")); assertFalse(response.body.contains(token))
    }

    @Test fun authorizesBeforeReadingAndHandlesAbsentLyricsAndLegacyRoutes() {
        val calls = AtomicInteger()
        val routes = routes { calls.incrementAndGet(); null }
        assertEquals(401, routes.metadata(path, emptyMap())!!.status)
        assertEquals(401, routes.metadata(path, headers + ("origin" to "http://example.test"))!!.status)
        assertEquals(0, calls.get())
        assertTrue(JSONObject(routes.metadata(path, headers)!!.body).isNull("lyrics"))
        assertEquals(400, routes.metadata(path.substringBefore('?'), headers)!!.status)
        val legacy = EchoPhoneLibraryRoutes(token, "http://127.0.0.1", { _, _, _ -> EchoPhoneLibraryPage(emptyList(), 0) }, { null })
        assertEquals(404, legacy.metadata(path, headers)!!.status)
    }

    @Test fun preservesPlainLyricsAndRejectsOversizedDocuments() {
        val plain = EchoLyrics(lines = listOf(EchoLyricLine(-1, text = "Plain line")))
        val response = routes { EchoPhoneLyrics("Song", "Artist", null, 0, plain) }.metadata(path, headers)!!
        val lyrics = JSONObject(response.body).getJSONObject("lyrics")
        assertEquals("plain", lyrics.getString("kind")); assertEquals("Plain line", lyrics.getString("plainText"))
        assertEquals(-1, lyrics.getJSONArray("lines").getJSONObject(0).getLong("timeMs"))
        val tooLarge = plain.copy(lines = List(4001) { EchoLyricLine(-1, text = "line") })
        assertEquals(413, routes { EchoPhoneLyrics("Song", "Artist", null, 0, tooLarge) }.metadata(path, headers)!!.status)
    }
}

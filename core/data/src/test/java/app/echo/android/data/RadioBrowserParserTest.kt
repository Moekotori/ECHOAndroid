package app.echo.android.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBrowserParserTest {
    @Test
    fun prefersResolvedStreamAndDropsBrokenOrInvalidRows() {
        val body = """
            [
              {
                "stationuuid": "aaa",
                "name": "Good",
                "url": "https://example.com/page",
                "url_resolved": "https://stream.example/live",
                "country": "Japan",
                "tags": "jazz, piano, extra",
                "bitrate": 192,
                "codec": "mp3",
                "lastcheckok": 1
              },
              {
                "stationuuid": "bbb",
                "name": "Broken",
                "url": "https://stream.example/broken",
                "lastcheckok": 0
              },
              {
                "stationuuid": "ccc",
                "name": "Webpage",
                "url": "not-a-url"
              },
              {
                "stationuuid": "ddd",
                "name": "Duplicate",
                "url_resolved": "https://stream.example/live"
              }
            ]
        """.trimIndent()
        val stations = RadioBrowserParser.parseStations(body)
        assertEquals(1, stations.size)
        val station = stations.single()
        assertEquals("aaa", station.id)
        assertEquals("Good", station.name)
        assertEquals("https://stream.example/live", station.url)
        assertEquals("Japan", station.country)
        assertEquals("jazz, piano", station.tags)
        assertEquals(192, station.bitrateKbps)
        assertEquals("MP3", station.codec)
    }

    @Test
    fun fallsBackToCountryCodeAndRawUrl() {
        val station = RadioBrowserParser.parseStation(
            JSONObject(
                """
                {
                  "stationuuid": "uuid",
                  "name": "  Local FM ",
                  "url": "http://radio.example:8000/live",
                  "countrycode": "DE",
                  "bitrate": 0
                }
                """.trimIndent(),
            ),
        )
        assertEquals("uuid", station?.id)
        assertEquals("Local FM", station?.name)
        assertEquals("http://radio.example:8000/live", station?.url)
        assertEquals("DE", station?.country)
        assertNull(station?.bitrateKbps)
    }

    @Test
    fun capsResultCount() {
        val rows = buildString {
            append('[')
            repeat(RadioBrowserPolicy.MaxResults + 5) { index ->
                if (index > 0) append(',')
                append("""{"stationuuid":"id$index","name":"S$index","url":"https://stream.example/$index"}""")
            }
            append(']')
        }
        assertEquals(RadioBrowserPolicy.MaxResults, RadioBrowserParser.parseStations(rows).size)
    }

    @Test
    fun emptyArrayIsASuccessfulMiss() {
        assertTrue(RadioBrowserParser.parseStations("[]").isEmpty())
    }
}

package app.echo.android.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetlistFmParserTest {
    @Test
    fun convertsIsoAndApiDates() {
        assertEquals("17-09-2026", SetlistFmParser.toApiDate("2026-09-17"))
        assertNull(SetlistFmParser.toApiDate("2026/09/17"))
        assertEquals("2026-09-17", SetlistFmParser.fromApiDate("17-09-2026"))
    }

    @Test
    fun parsesSetlistSongsAndTapeFlag() {
        val json = JSONObject(
            """
            {"id":"abc","eventDate":"17-09-2026","url":"https://www.setlist.fm/setlist/abc",
             "artist":{"name":"Perfume"},
             "venue":{"name":"Budokan","city":{"name":"Tokyo"}},
             "sets":{"set":[{"song":[
               {"name":"Computer City"},
               {"name":"Polyrhythm","tape":true},
               {"name":"Edge","cover":{"name":"Guest"}}
             ]}]}}
            """.trimIndent(),
        )
        val setlist = SetlistFmParser.parseSetlist(json, "Fallback")
        assertEquals("abc", setlist.id)
        assertEquals("Perfume", setlist.artistName)
        assertEquals("2026-09-17", setlist.eventDate)
        assertEquals("Budokan", setlist.venue)
        assertEquals("Tokyo", setlist.city)
        assertTrue(setlist.exactDate)
        assertEquals(listOf("Computer City", "Polyrhythm", "Edge"), setlist.songs.map { it.title })
        assertTrue(setlist.songs[1].tape)
        assertEquals("Guest", setlist.songs[2].artist)
    }

    @Test
    fun filtersWantedDateThenFallsBack() {
        val json = JSONObject(
            """
            {"setlist":[
              {"id":"old","eventDate":"01-01-2024","artist":{"name":"A"},"sets":{"set":[{"song":[{"name":"Old"}]}]}},
              {"id":"new","eventDate":"17-09-2026","artist":{"name":"A"},"sets":{"set":[{"song":[{"name":"New"}]}]}}
            ]}
            """.trimIndent(),
        )
        val exact = SetlistFmParser.parseSetlists(json, "A", "2026-09-17")
        assertEquals(listOf("new"), exact.map { it.id })
        val missing = SetlistFmParser.parseSetlists(json, "A", "2025-01-01")
        assertEquals(2, missing.size)
    }

    @Test
    fun encodeDecodeKeepsCoreFields() {
        val original = SetlistFmParser.parseSetlist(
            JSONObject(
                """{"id":"x","eventDate":"17-09-2026","artist":{"name":"A"},
                    "sets":{"set":[{"song":[{"name":"Song"}]}]}}""",
            ),
            "A",
        ).copy(exactDate = false)
        val decoded = SetlistFmParser.decode(SetlistFmParser.encode(original))
        assertEquals(original.id, decoded.id)
        assertEquals(original.eventDate, decoded.eventDate)
        assertFalse(decoded.exactDate)
        assertEquals("Song", decoded.songs.single().title)
    }
}

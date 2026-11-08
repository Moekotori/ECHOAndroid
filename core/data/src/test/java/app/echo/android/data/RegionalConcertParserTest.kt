package app.echo.android.data

import app.echo.android.model.library.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class RegionalConcertParserTest {
    @Test fun structuredPerformersRequireExactNamesAndTitlesExcludeTributesAndSubstrings() {
        val record = RegionalConcertParser.boxianji(JSONObject("""{"updated":"2026-11-08","events":[
            {"id":"1","title":"Air live","date":"2026-12-05 18:00","city":"上海","performers":["Airbourne"],"link":"javascript:bad"},
            {"id":"2","title":"Live","date":"2026-12-05","performers":["Air"]}]}"""))
        assertFalse(RegionalConcertParser.matches(record[0], listOf("Air")))
        assertTrue(RegionalConcertParser.matches(record[1], listOf("Air")))
        assertEquals("", record[0].event.url)
        assertNull(record[1].event.time)
        assertNull(record[1].event.timezone)
        val titleOnly = record[1].copy(event = record[1].event.copy(details = ArtistConcertDetails()), titles = listOf("陈绮贞演唱会"))
        assertTrue(RegionalConcertParser.matches(titleOnly, listOf("陈绮贞")))
        assertFalse(RegionalConcertParser.matches(titleOnly.copy(titles = listOf("致敬陈绮贞演唱会")), listOf("陈绮贞")))
        assertFalse(RegionalConcertParser.matches(titleOnly.copy(titles = listOf("Airbourne live")), listOf("Air")))
    }

    @Test fun taiwanSessionsPreserveSourceFactsAndRejectInvalidDatesAndCoordinates() {
        val records = RegionalConcertParser.iculture(JSONArray("""[{"UID":"show","title":"Live","showUnit":"Artist、Guest",
          "webSales":"https://tickets.example/show","editModifyDate":"2026/11/08 10:00:00","comment":"<p>Note</p>",
          "showInfo":[{"time":"2026/12/05 18:00:00","endTime":"2026/12/05 20:00:00","locationName":"Hall",
            "location":"臺北市中正區","latitude":"25.03","longitude":"121.51","onSales":"Y","price":"NT$ 800"},
            {"time":"2026/12/06","latitude":100,"longitude":121,"onSales":"N"},{"time":"2026/02/30"}]}]"""))
        assertEquals(2, records.size)
        val detail = records.first().event.details
        assertEquals("2026-12-05T20:00:00", detail.endsAt)
        assertEquals(listOf("Artist", "Guest"), detail.lineup.map { it.name })
        assertEquals("NT$ 800", detail.priceText)
        assertEquals("Note", detail.notes)
        assertEquals(25.03, detail.latitude!!, 0.001)
        assertNull(records[1].event.details.latitude)
        assertNull(records[1].event.time)
        assertEquals(false, records[1].event.details.ticketed)
    }

    @Test fun hongKongFeedUsesPerformanceDatesAndBilingualTitleMatches() {
        val xml = """<BATCH><SEND_DATE>20261108</SEND_DATE><EVENT><MAIN_CAT><EG>Music</EG></MAIN_CAT>
          <EVENT_CODE>E1</EVENT_CODE><EVENT_TC>歌手演唱會</EVENT_TC><EVENT_EG>Artist Live</EVENT_EG>
          <LOCATION><VENUE_TC>音樂廳</VENUE_TC><CITY_TC>香港</CITY_TC></LOCATION>
          <PERFORMANCE><REF_NO>1</REF_NO><PERFORMANCE_DATETIME>2026-12-05 18:00:00</PERFORMANCE_DATETIME>
            <REFERENCE_LINK>https://www.urbtix.hk/event/1</REFERENCE_LINK><REMARK_TC>遲到者須候場</REMARK_TC></PERFORMANCE>
          <PERFORMANCE><REF_NO>2</REF_NO><PERFORMANCE_DATETIME>2026-12-06 20:00:00</PERFORMANCE_DATETIME></PERFORMANCE>
          </EVENT></BATCH>"""
        val records = RegionalConcertParser.urbtix(xml)
        assertEquals(2, records.size)
        assertTrue(RegionalConcertParser.matches(records.first(), listOf("Artist")))
        assertEquals("18:00", records.first().event.time)
        assertEquals("2026-11-08", records.first().event.details.sources.single().updatedAt)
        assertEquals("Asia/Hong_Kong", records.first().event.timezone)
    }

    @Test(expected = IOException::class) fun rejectsEntityBearingXml() {
        RegionalConcertParser.urbtix("""<!DOCTYPE BATCH [<!ENTITY x SYSTEM "file:///private">]><BATCH>&x;</BATCH>""")
    }

    @Test fun historyAndMetadataSurviveMergeAndDiskEncodingWithoutCollapsingDifferentVenues() {
        val html = """<div class="gb_event_list"><li class="clearfix"><p class="day1">2026-01-08</p>
          <a href="/events/1">Artist live</a><a href="/actors/Artist/1">Artist</a><a href="/places/1">Hall</a>
          開場 17:30 開演 18:00 終演 20:00</li></ul></div>"""
        val event = ArtistConcertParser.eventernote(html, listOf("Artist"), LocalDate.of(2026,11,8), includePast = true).single()
        assertEquals("2026-01-08T17:30:00", event.details.opensAt)
        val stamped = event.copy(details = event.details.copy(sources = listOf(ConcertSourceStamp("Eventernote", fetchedAt = "2026-11-08T10:00:00Z"))))
        val ticket = event.copy(id = "ticket", source = "eplus", ticketUrl = "https://tickets.example/show",
            details = ArtistConcertDetails(priceText = "JPY 5000", sources = listOf(ConcertSourceStamp("eplus"))))
        val result = ArtistConcerts(ArtistConcertParser.merge(listOf(stamped, ticket, ticket.copy(id = "other", venue = "Other Hall"))),
            fetchedAt = "2026-11-08T10:00:00Z")
        assertEquals(2, result.events.size)
        val merged = result.events.first { it.id == "ticket" }
        assertEquals("2026-01-08T20:00:00", merged.details.endsAt)
        assertEquals(2, merged.details.sources.size)
        assertEquals(result, ArtistConcertParser.decode(ArtistConcertParser.encode(result)))
        assertEquals("https://www.eventernote.com/actors/Artist/1/events?page=2", nextEventernotePage(
            """<a href="https://evil.example/?page=2">bad</a><a href="?page=2">next</a>""",
            "https://www.eventernote.com/actors/Artist/1/events"))
    }
}

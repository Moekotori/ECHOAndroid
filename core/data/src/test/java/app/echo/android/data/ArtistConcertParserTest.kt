package app.echo.android.data

import app.echo.android.model.library.ArtistConcert
import app.echo.android.model.library.ArtistConcerts
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class ArtistConcertParserTest {
    private val today = LocalDate.of(2026, 9, 14)

    @Test fun eventernoteRequiresExactActorAndPreservesUnknownTime() {
        val actors = """<form action="/actors/search"></form><a href="/actors/Band/12">Band</a><a href="/actors/BandTribute/13">Band Tribute</a>"""
        assertEquals("https://www.eventernote.com/actors/Band/12/events", ArtistConcertParser.actorPage(actors, listOf("Band")))
        assertNull(ArtistConcertParser.actorPage(actors, listOf("Other")))
        val html = """<div class="gb_event_list"><ul>
            <li class="clearfix "><p class="day4">2026-09-14</p><h4><a href="/events/1">Live &amp; More</a></h4>
              会場: <a href="/places/1">Tokyo Hall</a><a href="/actors/Band/12">Band</a></li>
            <li class="clearfix "><p class="day4">2026-09-15</p><h4><a href="/events/2">Other show</a></h4>
              <a href="/actors/Other/13">Other</a></li>
            <li class="clearfix "><p class="day4">2026-09-13</p><h4><a href="/events/3">Band past live</a></h4></li>
            </ul></div>"""
        val event = ArtistConcertParser.eventernote(html, listOf("Band"), today).single()
        assertEquals("Live & More", event.title)
        assertNull(event.time)
        assertNull(event.ticketUrl)
        assertEquals("Tokyo Hall", event.venue)
    }

    private fun eplusRow(title: String, date: String = "20260925", url: String = "/sf/detail/123", status: String = "0") =
        """{"kanren_kogyo_sub":{"kogyo_name_1":"$title"},"koenbi_term":"$date","kaien_time":"1930", "abort_henko_enki_status":"$status",
          "koen_detail_url_pc":"$url","kanren_venue":{"venue_name":"Hall","todofuken_name":"Tokyo"}}"""

    @Test fun eplusRejectsRelatedSoloActsCancelledShowsAndUnsafeLinks() {
        val html = """<script id="json" class="json" type="application/json">{"data":{"record_list":[
          ${eplusRow("Band Live")},${eplusRow("Band Member solo") .replace("Band Member solo", "Other Member solo")},
          ${eplusRow("Band Live", "20260230")},${eplusRow("Band Live", url = "https://other.test/ticket")},
          ${eplusRow("Band Live", status = "1")},${eplusRow("Band Live", "20260913")}
        ]}}</script>"""
        val result = ArtistConcertParser.eplus(html, listOf("Band"), today).single()
        assertEquals("2026-09-25", result.date)
        assertEquals("19:30", result.time)
        assertEquals("https://eplus.jp/sf/detail/123", result.ticketUrl)
    }

    @Test fun latinNameCannotMatchInsideAnotherArtistsName() {
        val html = """<script id="json">{"data":{"record_list":[${eplusRow("Airbourne Live")}]}}</script>"""
        assertTrue(ArtistConcertParser.eplus(html, listOf("Air"), today).isEmpty())
    }

    @Test fun mobileEventernoteParsesPronunciationAndRejectsMemberSoloTag() {
        val search = """<form action="/actors/search"></form><a href="/actors/Band/12">Band (ばんど)<span class="count color1">287</span></a>"""
        assertEquals("https://www.eventernote.com/actors/Band/12/events", ArtistConcertParser.actorPage(search, listOf("Band")))
        val html = """<div class="gb_listevent"><ul>
          <li class=" day4"><a href="/events/1"><div class="event"><p>Free Live</p></div><div class="date">2026-09-25(金)</div>
            <div class="actor">Band</div><div class="time">開演 19:30</div><div class="place">Hall</div></a></li>
          <li class=" day4"><a href="/events/2"><div class="event">Member Solo</div><div class="date">2026-09-25</div>
            <div class="actor">Member Band</div></a></li></ul></div>"""
        val event = ArtistConcertParser.eventernote(html, listOf("Band"), today).single()
        assertEquals("19:30", event.time)
        assertEquals("Hall", event.venue)
    }

    @Test(expected = IOException::class) fun markupChangeIsFailureInsteadOfNoConcerts() {
        ArtistConcertParser.eplus("<html>Service maintenance</html>", listOf("Band"), today)
    }

    @Test fun musicBrainzNeedsMatchingPerformerAndValidFutureDate() {
        val id = "11111111-1111-1111-1111-111111111111"
        val json = JSONObject("""{"events":[{"id":"$id","name":"Live","life-span":{"begin":"2026-09-15"},
          "relations":[{"artist":{"id":"band"}},{"place":{"name":"Hall","area":{"name":"Paris"}}}]}]}""")
        assertTrue(ArtistConcertParser.musicBrainz(json, "other", today).isEmpty())
        assertEquals("Paris", ArtistConcertParser.musicBrainz(json, "band", today).single().city)
        json.objects("events").single().put("cancelled", true)
        assertTrue(ArtistConcertParser.musicBrainz(json, "band", today).isEmpty())
    }

    @Test fun mergeKeepsSeparateShowtimesAndPrefersTicketSourceThenCacheRoundTrips() {
        val one = ArtistConcert("1", "Band Live", "2026-09-25", "13:00", "Hall", "Tokyo", "Eventernote", "https://www.eventernote.com/events/1")
        val two = one.copy(id = "2", source = "eplus", url = "https://eplus.jp/sf/detail/1", ticketUrl = "https://eplus.jp/sf/detail/1")
        val three = two.copy(id = "3", time = "19:00")
        val result = ArtistConcerts(ArtistConcertParser.merge(listOf(one.copy(title = "Band Live · Different listing title"), two, three)), listOf("MusicBrainz"))
        assertEquals(listOf("2", "3"), result.events.map { it.id })
        assertEquals(result, ArtistConcertParser.decode(ArtistConcertParser.encode(result)))
    }
}

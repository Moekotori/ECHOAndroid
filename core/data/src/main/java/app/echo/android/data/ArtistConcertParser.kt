package app.echo.android.data

import app.echo.android.model.library.ArtistConcert
import app.echo.android.model.library.ArtistConcerts
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal object ArtistConcertParser {
    private val htmlOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val anchors = Regex("<a\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", htmlOptions)

    fun text(value: String): String = value.replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) {
            val token = it.groupValues[1]
            val code = if (token.startsWith('x')) token.drop(1).toIntOrNull(16) else token.toIntOrNull()
            if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else ""
        }.replace(Regex("\\s+"), " ").trim()

    private fun date(value: String?): String? = value?.let { runCatching { LocalDate.parse(it).toString() }.getOrNull() }
    private fun time(value: String?): String? = value?.let { runCatching { LocalTime.parse(it).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull() }
    private fun sameName(value: String, names: List<String>) = names.any {
        ArtistOnlineInfoParser.normalize(value) == ArtistOnlineInfoParser.normalize(it)
    }
    private fun containsName(value: String, names: List<String>): Boolean = names.any { name ->
        // Keep word boundaries for Latin names so "Air" cannot match "Airbourne".
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(value) ||
            (name.any { it.code > 0x3000 } && ArtistOnlineInfoParser.normalize(value).contains(ArtistOnlineInfoParser.normalize(name)))
    }

    fun actorPage(html: String, names: List<String>): String? {
        if (!html.contains("/actors/search")) throw IOException("Unrecognized Eventernote search page")
        return anchors.findAll(html).filter { sameName(text(it.groupValues[2]), names) }
            .mapNotNull { match ->
                "https://www.eventernote.com".toHttpUrl().resolve(match.groupValues[1])?.takeIf {
                    it.host == "www.eventernote.com" && it.encodedPath.matches(Regex("/actors/[^/]+/[0-9]+"))
                }?.newBuilder()?.addPathSegment("events")?.build()?.toString()
            }.distinct().toList().singleOrNull()
    }

    fun eventernote(html: String, names: List<String>, today: LocalDate): List<ArtistConcert> {
        if (!html.contains("gb_event_list") && !html.contains("イベントがありません")) throw IOException("Unrecognized Eventernote event page")
        val blocks = Regex("<li\\b[^>]*class=[\"']clearfix[^\"']*[\"'][^>]*>(.*?)(?=<li\\b[^>]*class=[\"']clearfix|</ul>)", htmlOptions)
        return blocks.findAll(html).mapNotNull { match ->
            val block = match.groupValues[1]
            val date = date(Regex("class=[\"']day[0-9]*[\"']>([0-9-]{10})").find(block)?.groupValues?.get(1)) ?: return@mapNotNull null
            if (date < today.toString()) return@mapNotNull null
            val links = anchors.findAll(block).toList()
            val event = links.firstOrNull { it.groupValues[1].matches(Regex("/events/[0-9]+")) } ?: return@mapNotNull null
            val title = text(event.groupValues[2])
            val performerMatch = links.any { it.groupValues[1].startsWith("/actors/") && sameName(text(it.groupValues[2]), names) }
            if (!performerMatch && !containsName(title, names)) return@mapNotNull null
            val venue = links.firstOrNull { it.groupValues[1].startsWith("/places/") }?.groupValues?.get(2)?.let(::text)
            ArtistConcert("eventernote:${event.groupValues[1].substringAfterLast('/')}", title, date,
                time(Regex("開演\\s*([0-9]{1,2}:[0-9]{2})").find(text(block))?.groupValues?.get(1)?.padStart(5, '0')),
                venue, null, "Eventernote", "https://www.eventernote.com${event.groupValues[1]}")
        }.distinctBy { it.id }.sortedBy { it.date + it.time.orEmpty() }.take(40).toList()
    }

    fun eplus(html: String, names: List<String>, today: LocalDate): List<ArtistConcert> {
        val body = Regex("<script\\b[^>]*id=[\"']json[\"'][^>]*>(.*?)</script>", htmlOptions).find(html)?.groupValues?.get(1)
            ?: throw IOException("Unrecognized eplus event page")
        val data = JSONObject(body).optJSONObject("data") ?: throw IOException("Missing eplus event data")
        if (!data.has("record_list") && data.optInt("so_kensu", -1) != 0) throw IOException("Missing eplus records")
        return data.objects("record_list").mapNotNull { row ->
            val show = row.optJSONObject("kanren_kogyo_sub") ?: return@mapNotNull null
            val title = listOfNotNull(show.text("kogyo_name_1"), show.text("kogyo_name_2")).joinToString(" · ").let(::text)
            if (!containsName(title, names) || row.optString("abort_henko_enki_status", "0") != "0") return@mapNotNull null
            val rawDate = row.text("koenbi_term") ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(rawDate, DateTimeFormatter.BASIC_ISO_DATE).toString() }.getOrNull() ?: return@mapNotNull null
            if (date < today.toString()) return@mapNotNull null
            val url = row.text("koen_detail_url_pc")?.let { "https://eplus.jp".toHttpUrl().resolve(it) }
                ?.takeIf { it.isHttps && it.host == "eplus.jp" && it.encodedPath.startsWith("/sf/detail/") } ?: return@mapNotNull null
            val venue = row.optJSONObject("kanren_venue")
            val rawTime = row.text("kaien_time")
            ArtistConcert("eplus:${url.encodedPath}", title, date,
                rawTime?.takeIf { it.length == 4 }?.let { time(it.take(2) + ":" + it.takeLast(2)) },
                venue?.text("venue_name"), venue?.text("todofuken_name"), "eplus", url.toString(), url.toString())
        }.distinctBy { it.id }.sortedBy { it.date + it.time.orEmpty() }.take(40)
    }

    fun musicBrainz(json: JSONObject, artistId: String, today: LocalDate): List<ArtistConcert> = json.objects("events").mapNotNull { row ->
        if (row.optBoolean("cancelled")) return@mapNotNull null
        val relations = row.objects("relations")
        if (relations.none { it.optJSONObject("artist")?.text("id") == artistId }) return@mapNotNull null
        val date = date(row.optJSONObject("life-span")?.text("begin")) ?: return@mapNotNull null
        if (date < today.toString()) return@mapNotNull null
        val id = row.text("id")?.takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) } ?: return@mapNotNull null
        val place = relations.firstNotNullOfOrNull { it.optJSONObject("place") }
        ArtistConcert("musicbrainz:$id", row.text("name") ?: return@mapNotNull null, date,
            time(row.text("time")), place?.text("name"), place?.optJSONObject("area")?.text("name"),
            "MusicBrainz", "https://musicbrainz.org/event/$id")
    }

    fun merge(events: List<ArtistConcert>): List<ArtistConcert> = events.sortedBy { if (it.ticketUrl != null) 0 else 1 }
        .distinctBy { listOf(it.date, it.time.orEmpty(), ArtistOnlineInfoParser.normalize(it.title)).joinToString("|") }
        .sortedWith(compareBy({ it.date }, { it.time.orEmpty() }, { it.title })).take(60)

    fun encode(result: ArtistConcerts): JSONObject = JSONObject().put("failed", JSONArray(result.failedSources))
        .put("events", JSONArray(result.events.map {
            JSONObject().put("id", it.id).put("title", it.title).put("date", it.date).put("time", it.time)
                .put("venue", it.venue).put("city", it.city).put("source", it.source).put("url", it.url).put("ticket", it.ticketUrl)
        }))
    fun decode(json: JSONObject): ArtistConcerts = ArtistConcerts(json.objects("events").map {
        ArtistConcert(it.getString("id"), it.getString("title"), it.getString("date"), it.text("time"),
            it.text("venue"), it.text("city"), it.getString("source"), it.getString("url"), it.text("ticket"))
    }, json.optJSONArray("failed")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty())
}

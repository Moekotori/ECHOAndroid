package app.echo.android.data

import app.echo.android.model.library.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

internal object RegionalConcertParser {
    const val LIMIT = 3000
    data class Record(val event: ArtistConcert, val titles: List<String>)
    private fun url(value: String?): String? = value?.toHttpUrlOrNull()?.toString()
    private fun start(value: String?): Pair<String, String?>? {
        val date = ConcertMetadataCodec.date(value) ?: return null
        return date.take(10) to date.takeIf { it.length > 10 }?.substring(11, 16)
    }
    private fun event(id: String, title: String, starts: Pair<String, String?>, venue: String?, city: String?, source: String,
                      link: String?, ticket: String?, zone: String?, details: ArtistConcertDetails) = ArtistConcert(
        id, title.take(512), starts.first, starts.second, venue?.take(512), city?.take(160), source, link.orEmpty(), ticket, zone, details)

    fun boxianji(payload: JSONObject): List<Record> {
        val rows = payload.optJSONArray("events") ?: throw IOException("Invalid Boxianji feed")
        return (0 until rows.length()).asSequence().mapNotNull { rows.optJSONObject(it) }.mapNotNull { row ->
            val id = row.text("id") ?: return@mapNotNull null
            val title = row.text("title") ?: return@mapNotNull null
            val date = start(row.text("date")) ?: return@mapNotNull null
            val names = row.optJSONArray("performers") ?: return@mapNotNull null
            val lineup = ConcertMetadataCodec.lineup((0 until names.length()).mapNotNull {
                when (val name = names.opt(it)) {
                    is String -> ConcertPerformer(name)
                    is JSONObject -> name.text("name")?.let { n -> ConcertPerformer(n, name.text("role")) }
                    else -> null
                }
            })
            val city = row.text("city")
            // Only known places establish a timezone; an unknown location is not assumed to be mainland China.
            val zone = when {
                city?.contains("香港") == true -> "Asia/Hong_Kong"
                city?.contains("澳门") == true || city?.contains("澳門") == true -> "Asia/Macau"
                city != null && Regex("^(北京|上海|天津|重庆|重慶|广州|廣州|深圳|杭州|南京|成都|武汉|武漢|苏州|蘇州|西安|厦门|廈門)").containsMatchIn(city) -> "Asia/Shanghai"
                else -> null
            }
            Record(event("boxianji:$id", title, date, null, city, "拨弦记", url(row.text("link")), null, zone,
                ArtistConcertDetails(lineup = lineup, sources = listOf(ConcertSourceStamp("拨弦记", ConcertMetadataCodec.date(payload.text("updated")))))), listOf(title))
        }.take(LIMIT).toList()
    }

    fun iculture(payload: JSONArray): List<Record> {
        val result = ArrayList<Record>()
        for (i in 0 until payload.length()) {
            val row = payload.optJSONObject(i) ?: continue
            val uid = row.text("UID") ?: continue
            val title = row.text("title") ?: continue
            val lineup = ConcertMetadataCodec.lineup(row.text("showUnit").orEmpty().split(Regex("[、,，;；\\n|/]+"))
                .map { ConcertPerformer(it) })
            val ticket = url(row.text("webSales"))
            for (show in row.objects("showInfo")) {
                val date = start(show.text("time")) ?: continue
                val venue = show.text("locationName")
                val address = ConcertMetadataCodec.text(show.text("location"), 512)
                val lat = show.optDouble("latitude", Double.NaN).takeIf { it.isFinite() && it in -90.0..90.0 }
                val lon = show.optDouble("longitude", Double.NaN).takeIf { it.isFinite() && it in -180.0..180.0 }
                result += Record(event("iculture:$uid:${date.first}:${date.second}:$venue", title, date, venue, null,
                    "iCulture · 臺灣文化部", url(row.text("sourceWebPromote")) ?: ticket, ticket, "Asia/Taipei",
                    ArtistConcertDetails(endsAt = ConcertMetadataCodec.date(show.text("endTime")), lineup = lineup,
                        address = address, latitude = lat.takeIf { lon != null }, longitude = lon.takeIf { lat != null },
                        ticketed = when (show.text("onSales")) { "Y" -> true; "N" -> false; else -> null },
                        priceText = ConcertMetadataCodec.text(show.text("price"), 512), notes = ConcertMetadataCodec.text(row.text("comment")),
                        sources = listOf(ConcertSourceStamp("iCulture · 臺灣文化部", ConcertMetadataCodec.date(row.text("editModifyDate"))))
                    )), listOf(title))
                if (result.size >= LIMIT) return result
            }
        }
        return result
    }

    fun urbtix(xml: String): List<Record> {
        if (Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE).containsMatchIn(xml)) throw IOException("Unsupported URBTIX XML")
        val factory = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
        val root = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        if (root.tagName != "BATCH") throw IOException("Invalid URBTIX feed")
        fun Element.field(tag: String) = getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        val updated = ConcertMetadataCodec.date(root.field("SEND_DATE")?.replace(Regex("^(\\d{4})(\\d{2})(\\d{2})$"), "$1-$2-$3"))
        val records = ArrayList<Record>()
        val events = root.getElementsByTagName("EVENT")
        for (i in 0 until events.length) {
            val row = events.item(i) as Element
            val category = row.getElementsByTagName("MAIN_CAT").item(0) as? Element ?: continue
            if (!Regex("music|concert", RegexOption.IGNORE_CASE).containsMatchIn(category.field("EG").orEmpty())) continue
            val code = row.field("EVENT_CODE") ?: continue
            val title = row.field("EVENT_TC") ?: row.field("EVENT_EG") ?: continue
            val location = row.getElementsByTagName("LOCATION").item(0) as? Element ?: continue
            val shows = row.getElementsByTagName("PERFORMANCE")
            for (j in 0 until shows.length) {
                val show = shows.item(j) as Element
                val id = show.field("REF_NO") ?: continue
                val date = start(show.field("PERFORMANCE_DATETIME")) ?: continue
                val titles = listOfNotNull(title, row.field("EVENT_EG"), show.field("TITLE_TC"), show.field("TITLE_EG"))
                val link = url(show.field("REFERENCE_LINK")) ?: url(row.field("REFERENCE_LINK"))
                val remark = show.field("REMARK_TC") ?: show.field("REMARK_EG")
                records += Record(event("urbtix:$code:$id", title, date, location.field("VENUE_TC") ?: location.field("VENUE_EG"),
                    location.field("CITY_TC") ?: location.field("CITY_EG"), "URBTIX · 香港康文署", link, link, "Asia/Hong_Kong",
                    ArtistConcertDetails(notes = remark?.takeUnless { it in titles }?.let { ConcertMetadataCodec.text(it) },
                        sources = listOf(ConcertSourceStamp("URBTIX · 香港康文署", updated)))), titles)
                if (records.size >= LIMIT) return records
            }
        }
        return records
    }

    fun matches(record: Record, names: List<String>): Boolean {
        val keys = names.map(ArtistOnlineInfoParser::normalize).filter { it.length >= 2 }
        if (record.event.details.lineup.isNotEmpty()) return record.event.details.lineup.any { ArtistOnlineInfoParser.normalize(it.name) in keys }
        return record.titles.any { title ->
            if (Regex("致敬|tribute", RegexOption.IGNORE_CASE).containsMatchIn(title)) false
            else names.filter { it.trim().length >= 2 }.any { name ->
                val suffix = if (name.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN })
                    "|演唱会|演唱會|音乐会|音樂會|独奏会|獨奏會|巡回|巡演|鋼琴|钢琴|小提琴|大提琴|吉他" else ""
                Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name.trim())}(?=$|[^\\p{L}\\p{N}]$suffix)", RegexOption.IGNORE_CASE).containsMatchIn(title)
            }
        }
    }
}

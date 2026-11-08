package app.echo.android.data

import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Five shared disk snapshots; no resident whole-feed collection and no artist query in requests. */
internal class RegionalConcertFeeds(directory: File, private val transport: ArtistOnlineTransport) {
    private val cache = ArtistOnlineCache(File(directory, "regional-concerts"), 4 * 1024 * 1024, 5)
    data class Result(val events: List<ArtistConcert>, val failed: Boolean)
    private data class Feed(val key: String, val url: String, val parse: (String) -> List<RegionalConcertParser.Record>)

    suspend fun load(source: String, names: List<String>, refresh: Boolean, now: Instant, budgetMillis: Long, period: ArtistConcertPeriod): Result {
        val deadline = System.nanoTime() + budgetMillis.coerceAtLeast(1) * 1_000_000
        val feeds = when (source) {
            "拨弦记" -> listOf(Feed("boxianji", "https://boxianji.guitarkk.com/attention.json") { RegionalConcertParser.boxianji(JSONObject(it)) })
            "URBTIX · 香港康文署" -> {
                val date = now.atZone(ZoneId.of("Asia/Hong_Kong")).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                listOf(Feed("urbtix", "https://fs-open-1304240968.cos.ap-hongkong.myqcloud.com/prod/gprd/URBTIX_eventBatch_$date.xml", RegionalConcertParser::urbtix))
            }
            else -> listOf(1, 5, 17).map { category ->
                Feed("iculture-$category", "https://cloud.culture.tw/frontsite/trans/SearchShowAction.do?method=doFindTypeJ&category=$category") {
                    RegionalConcertParser.iculture(JSONArray(it))
                }
            }
        }
        val events = ArrayList<ArtistConcert>()
        var failed = false
        for (feed in feeds) {
            val entry = cache.read("v1:${feed.key}")
            val saved = entry?.value?.let { runCatching { decode(it) }.getOrNull() }
            val records = if (!refresh && saved != null && entry.age < 6 * 3600_000L) saved else {
                try {
                    val remaining = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1)
                    val body = withTimeout(minOf(6000L, remaining)) { transport.text(feed.url.toHttpUrl(), maxBytes = 4 * 1024 * 1024L) }
                    val fetchedAt = Instant.now().toString()
                    val parsed = feed.parse(body).map { record -> record.copy(event = record.event.copy(details = record.event.details.copy(
                        sources = record.event.details.sources.map { it.copy(fetchedAt = fetchedAt) }))) }
                    cache.write("v1:${feed.key}", encode(parsed))
                    parsed
                } catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    failed = true
                    saved?.takeIf { entry.age < 7 * 86400_000L }.orEmpty()
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) {
                    failed = true
                    saved?.takeIf { entry.age < 7 * 86400_000L }.orEmpty()
                }
            }
            events += records.asSequence().filter { RegionalConcertParser.matches(it, names) }.map { it.event }
                .filter { (it.phase(now) == ArtistConcertPhase.Past) == (period == ArtistConcertPeriod.Past) }.toList()
        }
        val sorted = events.distinctBy { it.id }.sortedWith(compareBy({ it.date }, { it.time.orEmpty() }))
        return Result((if (period == ArtistConcertPeriod.Past) sorted.asReversed() else sorted).take(100), failed)
    }

    private fun encode(records: List<RegionalConcertParser.Record>): JSONObject =
        ArtistConcertParser.encode(ArtistConcerts(records.map { it.event })).put("titles", JSONArray(records.map { JSONArray(it.titles) }))

    private fun decode(json: JSONObject): List<RegionalConcertParser.Record> {
        val titles = json.getJSONArray("titles")
        return ArtistConcertParser.decode(json).events.take(RegionalConcertParser.LIMIT).mapIndexed { i, event ->
            val array = titles.getJSONArray(i)
            RegionalConcertParser.Record(event, (0 until array.length()).map { array.getString(it) })
        }
    }
}

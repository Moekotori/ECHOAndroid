package app.echo.android.data

import app.echo.android.model.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal class ArtistConcertRepository(
    directory: File,
    private val transport: ArtistOnlineTransport,
    private val cache: ArtistOnlineCache,
    private val identity: suspend (ArtistOnlineQuery, Boolean) -> JSONObject?,
    private val musicBrainz: suspend (String, Map<String, String>) -> JSONObject,
) {
    private val regional = RegionalConcertFeeds(directory, transport)
    private val artistIdentity = ConcertArtistIdentity(identity)
    private data class Source(val name: String, val read: suspend (Long) -> RegionalConcertFeeds.Result)

    suspend fun load(query: ArtistOnlineQuery, queryKey: String, refresh: Boolean, period: ArtistConcertPeriod): ArtistConcerts {
        val key = "concerts-v4:$period:$queryKey"
        val entry = cache.read(key)
        val saved = entry?.value?.let { runCatching { ArtistConcertParser.decode(it) }.getOrNull() }
        val now = Instant.now()
        fun current(result: ArtistConcerts): ArtistConcerts {
            val events = result.events.filter { (it.phase(now) == ArtistConcertPhase.Past) == (period == ArtistConcertPeriod.Past) }
                .sortedWith(compareBy({ it.date }, { it.time.orEmpty() }, { it.title }))
            return result.copy(events = (if (period == ArtistConcertPeriod.Past) events.asReversed() else events).take(100))
        }
        val ttl = if (saved?.failedSources?.isNotEmpty() == true) 5 * 60_000L else 6 * 3600_000L
        if (!refresh && saved != null && entry.age < ttl) return current(saved)
        if (query.name.isBlank()) return ArtistConcerts(emptyList())
        val deadline = System.nanoTime() + 12_000_000_000L
        val known = cache.read("identity-v1:$queryKey")?.takeIf { it.age < 7 * 86400_000L }?.value
        val resolved = artistIdentity.resolve(query, known, refresh)
        val names = resolved.names
        val today = LocalDate.now(ZoneOffset.UTC)
        val jobs = mutableListOf<Source>()
        // Regional feeds get a fair share of the bounded request budget before slower identity lookups.
        listOf("拨弦记", "URBTIX · 香港康文署", "iCulture · 臺灣文化部").forEach { source ->
            jobs += Source(source) { budget -> regional.load(source, names, refresh, now, (budget - 100).coerceAtLeast(1), period) }
        }
        jobs += Source("Eventernote") {
            val events = ArrayList<ArtistConcert>()
            var failed = false
            var page: okhttp3.HttpUrl? = null
            for (name in names.take(3)) {
                try {
                    val search = transport.text(url("https://www.eventernote.com/actors/search", mapOf("keyword" to name)))
                    page = ArtistConcertParser.actorPage(search, names)?.toHttpUrl()
                    if (page != null) break
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { failed = true }
            }
            for (index in 0 until if (period == ArtistConcertPeriod.Past) 4 else 1) {
                val address = page ?: break
                try {
                    val html = transport.text(address)
                    events += ArtistConcertParser.eventernote(html, names, today, includePast = true)
                    page = nextEventernotePage(html, address.toString())?.toHttpUrl()
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { failed = true; break }
            }
            RegionalConcertFeeds.Result(events, failed)
        }
        jobs += Source("MusicBrainz") {
            val id = resolved.artist?.text("id")
            val events = ArrayList<ArtistConcert>()
            if (id != null) {
                val dates = if (period == ArtistConcertPeriod.Past) "begin:[0001-01-01 TO $today]"
                    else "(begin:[$today TO 9999-12-31] OR end:[$today TO 9999-12-31])"
                val search = musicBrainz("event/", mapOf("query" to "arid:$id AND $dates", "limit" to "12"))
                search.objects("events").take(12).forEach { row ->
                    val eventId = row.text("id")?.takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) }
                    if (eventId != null) {
                        val detail = musicBrainz("event/$eventId", mapOf("inc" to "artist-rels+place-rels"))
                        events += ArtistConcertParser.musicBrainz(JSONObject().put("events", JSONArray().put(detail)), id, today, includePast = true)
                    }
                }
            }
            RegionalConcertFeeds.Result(events, resolved.failed)
        }
        if (period == ArtistConcertPeriod.Upcoming) jobs += Source("eplus") {
            val events = ArrayList<ArtistConcert>()
            var failed = false
            for (name in names.take(3)) {
                try {
                    events += ArtistConcertParser.eplus(transport.text(url("https://eplus.jp/sf/search", mapOf("keyword" to name))), names, today)
                    if (events.isNotEmpty()) break
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { failed = true }
            }
            RegionalConcertFeeds.Result(events, failed)
        }
        val workers = Semaphore(3)
        val results = coroutineScope {
            jobs.map { job -> async {
                workers.withPermit {
                    try {
                        val remaining = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1)
                        val budget = minOf(remaining, 10_000L)
                        job.name to withTimeout(budget) { job.read(budget) }
                    } catch (_: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        job.name to RegionalConcertFeeds.Result(emptyList(), true)
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { job.name to RegionalConcertFeeds.Result(emptyList(), true) }
                }
            } }.awaitAll()
        }
        val failures = results.filter { it.second.failed }.map { it.first }
        val events = results.flatMap { (source, result) -> result.events.map { event ->
            if (event.details.sources.isNotEmpty()) event else event.copy(details = event.details.copy(
                sources = listOf(ConcertSourceStamp(source, fetchedAt = now.toString()))))
        } }
        if (events.isEmpty() && failures.isNotEmpty()) {
            if (saved != null && entry.age < 7 * 86400_000L) return current(saved.copy(stale = true, failedSources = failures))
            if (failures.size == jobs.size) throw IOException("Concert sources unavailable")
        }
        val result = current(ArtistConcerts(ArtistConcertParser.merge(events), failures, fetchedAt = now.toString()))
        cache.write(key, ArtistConcertParser.encode(result))
        return result
    }

    private fun url(base: String, parameters: Map<String, String>) = base.toHttpUrl().newBuilder().apply {
        parameters.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build()
}

/** Only follow the next numbered page on the same artist listing. */
internal fun nextEventernotePage(html: String, current: String): String? {
    val address = current.toHttpUrl()
    val next = (address.queryParameter("page")?.toIntOrNull() ?: 1) + 1
    return Regex("<a\\b[^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(html).mapNotNull {
        address.resolve(it.groupValues[1].replace("&amp;", "&"))
    }.firstOrNull { it.scheme == address.scheme && it.host == address.host && it.port == address.port &&
        it.encodedPath == address.encodedPath && it.queryParameter("page")?.toIntOrNull() == next }?.toString()
}

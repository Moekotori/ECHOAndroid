package app.echo.android.data

import app.echo.android.model.library.ArtistConcert
import app.echo.android.model.library.ArtistConcerts
import app.echo.android.model.library.ArtistOnlineInfo
import app.echo.android.model.library.ArtistOnlineInfoLoader
import app.echo.android.model.library.ArtistOnlineQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

class ArtistOnlineInfoRepository(
    cacheDirectory: File,
    appVersion: String,
    private val musicBrainzGate: MusicBrainzRequestGate,
) : ArtistOnlineInfoLoader {
    private val gate = Mutex()
    private val transport = ArtistOnlineTransport(appVersion)
    private val cache = ArtistOnlineCache(cacheDirectory)

    private fun queryKey(query: ArtistOnlineQuery): String =
        (listOf(query.name.trim()) + query.albumTitles.take(3).sorted()).joinToString("\u0000")

    override suspend fun loadProfile(query: ArtistOnlineQuery, language: String, refresh: Boolean): ArtistOnlineInfo? =
        withContext(Dispatchers.IO) { gate.withLock {
            val lang = language.takeIf { it in listOf("zh", "ja", "ko", "en") } ?: "en"
            val key = "profile-v1:$lang:${queryKey(query)}"
            val entry = cache.read(key)
            val cached = entry?.value?.let { runCatching { ArtistOnlineInfoParser.profile(it) }.getOrNull() }
            val ttl = when { cached?.partial == true -> 15 * 60_000L; cached == null -> 6 * 3600_000L; else -> 7 * 86400_000L }
            if (!refresh && entry != null && entry.age < ttl && (entry.value == null || cached != null)) return@withLock cached
            try {
                val artist = identity(query, refresh) ?: run { cache.write(key, null); return@withLock null }
                val result = JSONObject(artist.toString())
                try {
                    wikipedia(result, lang)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { result.put("partial", true) }
                cache.write(key, result)
                ArtistOnlineInfoParser.profile(result)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (cached != null && entry.age < 30 * 86400_000L) cached.copy(stale = true) else throw failure
            }
        } }

    override suspend fun loadConcerts(query: ArtistOnlineQuery, refresh: Boolean): ArtistConcerts =
        withContext(Dispatchers.IO) { gate.withLock {
            val key = "concerts-v1:${queryKey(query)}"
            val entry = cache.read(key)
            val cached = entry?.value?.let { runCatching { ArtistConcertParser.decode(it) }.getOrNull() }
            val today = LocalDate.now(ZoneId.of("Asia/Tokyo"))
            fun current(result: ArtistConcerts) = result.copy(events = result.events.filter { it.date >= today.toString() })
            val ttl = if (cached?.failedSources?.isNotEmpty() == true) 5 * 60_000L else 6 * 3600_000L
            if (!refresh && cached != null && entry.age < ttl) return@withLock current(cached)
            if (query.name.isBlank()) return@withLock ArtistConcerts(emptyList())
            suspend fun source(name: String, work: suspend () -> List<ArtistConcert>): Pair<List<ArtistConcert>, String?> {
                return try { withTimeout(24_000L) { work() } to null
                } catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    emptyList<ArtistConcert>() to name
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { emptyList<ArtistConcert>() to name }
            }
            val knownArtist = cache.read("identity-v1:${queryKey(query)}")?.takeIf { it.age < 7 * 86400_000L }?.value
            val names = (listOf(query.name) + listOfNotNull(knownArtist?.text("name")) +
                knownArtist?.objects("aliases").orEmpty().mapNotNull { it.text("name") }).distinct().filter { it.isNotBlank() }.take(16)
            // At most three sources at once. MusicBrainz still uses the application-wide serial gate.
            val results = coroutineScope {
                listOf(
                    async {
                        source("MusicBrainz") {
                            val artist = identity(query, refresh)
                            val id = artist?.text("id")
                            val found = mutableListOf<ArtistConcert>()
                            if (id != null) {
                                val search = musicBrainz("event/", mapOf("query" to "aid:$id AND begin:[$today TO *]", "limit" to "12"))
                                // Search may omit relations; verify performer and place in bounded lookups.
                                search.objects("events").take(12).forEach { event ->
                                    val eventId = event.text("id")?.takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) }
                                    if (eventId != null) {
                                        val detail = musicBrainz("event/$eventId", mapOf("inc" to "artist-rels+place-rels"))
                                        found += ArtistConcertParser.musicBrainz(JSONObject().put("events", org.json.JSONArray().put(detail)), id, today)
                                    }
                                }
                            }
                            found
                        }
                    },
                    async {
                        source("Eventernote") {
                            val search = transport.text(url("https://www.eventernote.com/actors/search", mapOf("keyword" to query.name)))
                            ArtistConcertParser.actorPage(search, names)?.let { page ->
                                ArtistConcertParser.eventernote(transport.text(page.toHttpUrl()), names, today)
                            }.orEmpty()
                        }
                    },
                    async {
                        source("eplus") {
                            ArtistConcertParser.eplus(transport.text(url("https://eplus.jp/sf/search", mapOf("keyword" to query.name))), names, today)
                        }
                    },
                ).awaitAll()
            }
            val events = results.flatMap { it.first }
            val failures = results.mapNotNull { it.second }
            if (failures.size == 3 && events.isEmpty()) {
                if (cached != null && entry.age < 7 * 86400_000L) return@withLock current(cached.copy(stale = true, failedSources = failures))
                throw IOException("Artist concert services unavailable: ${failures.joinToString()}")
            }
            val result = ArtistConcerts(ArtistConcertParser.merge(events), failures)
            // Partial outages must not replace a useful cache with a misleading empty result.
            if (events.isEmpty() && failures.isNotEmpty() && cached?.events?.isNotEmpty() == true && entry.age < 7 * 86400_000L) {
                return@withLock current(cached.copy(stale = true, failedSources = failures))
            }
            cache.write(key, ArtistConcertParser.encode(result))
            result
        } }

    private suspend fun identity(query: ArtistOnlineQuery, refresh: Boolean): JSONObject? {
        if (query.name.isBlank()) return null
        val key = "identity-v1:${queryKey(query)}"
        val saved = cache.read(key)
        if (!refresh && saved != null && saved.age < (if (saved.value == null) 6 * 3600_000L else 7 * 86400_000L)) return saved.value
        val search = musicBrainz("artist/", mapOf("query" to "artist:${ArtistOnlineInfoParser.quote(query.name)}", "limit" to "25"))
        val candidates = ArtistOnlineInfoParser.candidates(search, query)
        val albumIds = mutableSetOf<String>()
        if (candidates.size > 1) {
            query.albumTitles.filter { it.isNotBlank() }.distinct().take(3).forEach { title ->
                val groups = musicBrainz("release-group/", mapOf("query" to
                    "releasegroup:${ArtistOnlineInfoParser.quote(title)} AND artist:${ArtistOnlineInfoParser.quote(query.name)}", "limit" to "25"))
                albumIds += ArtistOnlineInfoParser.albumArtistIds(groups, title)
            }
        }
        val id = ArtistOnlineInfoParser.select(candidates, albumIds)
        val artist = id?.let { musicBrainz("artist/$it", mapOf("inc" to "aliases+genres+artist-rels+url-rels")) }
        cache.write(key, artist)
        return artist
    }

    private suspend fun wikipedia(artist: JSONObject, language: String) {
        val links = artist.objects("relations").mapNotNull { it.optJSONObject("url")?.text("resource")?.toHttpUrlOrNull() }
        val languages = listOf(language, "en", "ja", "zh", "ko").distinct()
        val titles = linkedMapOf<String, String>()
        links.forEach { link ->
            languages.firstOrNull { link.host == "$it.wikipedia.org" && link.encodedPath.startsWith("/wiki/") }
                ?.let { titles[it] = link.pathSegments.drop(1).joinToString("/") }
        }
        val entity = links.firstOrNull { it.host in setOf("www.wikidata.org", "wikidata.org") }
            ?.pathSegments?.lastOrNull()?.takeIf { it.matches(Regex("Q[0-9]+")) }
        if (entity != null) {
            val json = transport.json(url("https://www.wikidata.org/w/api.php", mapOf("action" to "wbgetentities",
                "format" to "json", "ids" to entity, "props" to "sitelinks", "sitefilter" to languages.joinToString("|") { "${it}wiki" })))
            val sites = json.optJSONObject("entities")?.optJSONObject(entity)?.optJSONObject("sitelinks")
            languages.forEach { lang -> sites?.optJSONObject("${lang}wiki")?.text("title")?.let { titles[lang] = it } }
        }
        val lang = languages.firstOrNull { titles.containsKey(it) } ?: return
        val json = transport.json(url("https://$lang.wikipedia.org/w/api.php", mapOf(
            "action" to "query", "format" to "json", "formatversion" to "2", "prop" to "extracts|info|pageprops",
            "explaintext" to "1", "exintro" to "1", "exchars" to "5000", "inprop" to "url", "redirects" to "1", "titles" to titles.getValue(lang))))
        val page = json.optJSONObject("query")?.objects("pages")?.firstOrNull() ?: return
        if (page.has("missing") || page.optJSONObject("pageprops")?.has("disambiguation") == true) return
        val link = page.text("fullurl")?.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "$lang.wikipedia.org" } ?: return
        artist.put("wikiExtract", page.text("extract")?.take(5000)).put("wikiUrl", link.toString()).put("wikiLanguage", lang)
    }

    private fun url(base: String, parameters: Map<String, String>): HttpUrl = base.toHttpUrl().newBuilder().apply {
        parameters.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build()
    private suspend fun musicBrainz(path: String, parameters: Map<String, String>): JSONObject = musicBrainzGate.run {
        transport.json(url("https://musicbrainz.org/ws/2/$path", parameters + ("fmt" to "json")))
    }
}

package app.echo.android.data

import app.echo.android.model.library.ArtistConcert
import app.echo.android.model.library.ArtistConcerts
import app.echo.android.model.library.ArtistOnlineInfo
import app.echo.android.model.library.ArtistOnlineInfoLoader
import app.echo.android.model.library.ArtistOnlineQuery
import app.echo.android.model.library.ArtistSetlist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File

class ArtistOnlineInfoRepository(
    cacheDirectory: File,
    appVersion: String,
    private val musicBrainzGate: MusicBrainzRequestGate,
    private val setlistApiKey: () -> String? = { null },
) : ArtistOnlineInfoLoader {
    private val gate = Mutex()
    private val transport = ArtistOnlineTransport(appVersion)
    private val setlistClient = SetlistFmClient(appVersion)
    private val cache = ArtistOnlineCache(cacheDirectory)

    private fun queryKey(query: ArtistOnlineQuery): String =
        (listOf(query.name.trim()) + query.albumTitles.take(3).sorted()).joinToString("\u0000")

    override suspend fun loadProfile(query: ArtistOnlineQuery, language: String, refresh: Boolean): ArtistOnlineInfo? =
        withContext(Dispatchers.IO) { gate.withLock {
            val lang = language.takeIf { it in listOf("zh", "ja", "ko", "en") } ?: "en"
            val key = "profile-v2:$lang:${queryKey(query)}"
            val entry = cache.read(key)
            val cached = entry?.value?.let { runCatching { ArtistOnlineInfoParser.profile(it) }.getOrNull() }
            val ttl = when { cached?.partial == true -> 15 * 60_000L; cached == null -> 6 * 3600_000L; else -> 7 * 86400_000L }
            if (!refresh && entry != null && entry.age < ttl && (entry.value == null || cached != null)) return@withLock cached
            try {
                val artist = identity(query, refresh) ?: run { cache.write(key, null); return@withLock null }
                val result = JSONObject(artist.toString())
                try {
                    withTimeout(28_000L) { ArtistWikipediaClient(transport).enrich(result, lang) }
                } catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    result.put("partial", true)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { result.put("partial", true) }
                cache.write(key, result)
                ArtistOnlineInfoParser.profile(result)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (cached != null && entry.age < 30 * 86400_000L) cached.copy(stale = true) else throw failure
            }
        } }

    private val concerts = ArtistConcertRepository(cacheDirectory, transport, cache, ::identity, ::musicBrainz)

    override suspend fun loadConcerts(query: ArtistOnlineQuery, refresh: Boolean): ArtistConcerts =
        loadConcerts(query, refresh, app.echo.android.model.library.ArtistConcertPeriod.Upcoming)

    override suspend fun loadConcerts(query: ArtistOnlineQuery, refresh: Boolean,
        period: app.echo.android.model.library.ArtistConcertPeriod): ArtistConcerts = withContext(Dispatchers.IO) {
        gate.withLock { concerts.load(query, queryKey(query), refresh, period) }
    }

    override suspend fun loadSetlist(
        query: ArtistOnlineQuery,
        concert: ArtistConcert,
        refresh: Boolean,
    ): ArtistSetlist = withContext(Dispatchers.IO) {
        gate.withLock {
            val key = "setlist-v1:${queryKey(query)}\u0000${concert.date}\u0000${concert.venue.orEmpty()}"
            val entry = cache.read(key)
            val cached = entry?.value?.let { runCatching { SetlistFmParser.decode(it) }.getOrNull() }
            if (!refresh && cached != null && entry.age < 6 * 3600_000L) return@withLock cached
            val apiKey = setlistApiKey()?.trim()?.takeIf { it.isNotEmpty() }
            if (apiKey == null) {
                return@withLock ArtistSetlist(
                    id = concert.id,
                    artistName = query.name,
                    eventDate = concert.date,
                    venue = concert.venue,
                    city = concert.city,
                    url = null,
                    songs = emptyList(),
                    exactDate = false,
                    missingApiKey = true,
                )
            }
            try {
                val exactDate = SetlistFmParser.toApiDate(concert.date)
                val exactJson = setlistClient.searchSetlists(apiKey, query.name, exactDate)
                val exact = SetlistFmParser.parseSetlists(exactJson, query.name, concert.date)
                    .firstOrNull { it.eventDate == concert.date && it.songs.isNotEmpty() }
                val setlist = exact ?: run {
                    val recent = SetlistFmParser.parseSetlists(
                        setlistClient.searchSetlists(apiKey, query.name, null),
                        query.name,
                        null,
                    ).firstOrNull { it.songs.isNotEmpty() }
                    recent?.copy(exactDate = false)
                } ?: ArtistSetlist(
                    id = concert.id,
                    artistName = query.name,
                    eventDate = concert.date,
                    venue = concert.venue,
                    city = concert.city,
                    url = concert.url,
                    songs = emptyList(),
                    exactDate = false,
                )
                cache.write(key, SetlistFmParser.encode(setlist))
                setlist
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (cached != null && entry != null && entry.age < 30 * 86400_000L) {
                    cached.copy(stale = true)
                } else {
                    throw failure
                }
            }
        }
    }

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

    private fun url(base: String, parameters: Map<String, String>): HttpUrl = base.toHttpUrl().newBuilder().apply {
        parameters.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build()
    private suspend fun musicBrainz(path: String, parameters: Map<String, String>): JSONObject = musicBrainzGate.run {
        transport.json(url("https://musicbrainz.org/ws/2/$path", parameters + ("fmt" to "json")))
    }
}

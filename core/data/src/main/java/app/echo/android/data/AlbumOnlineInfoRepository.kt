package app.echo.android.data

import app.echo.android.model.library.AlbumOnlineInfo
import app.echo.android.model.library.AlbumOnlineInfoLoader
import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One application-owned instance serializes lookups and the MusicBrainz rate budget. */
class AlbumOnlineInfoRepository(private val cacheDirectory: File, appVersion: String = "development") : AlbumOnlineInfoLoader {
    private val userAgent = "ECHOAndroid/$appVersion (https://github.com/moekotori/echo)"
    private val gate = Mutex()
    private var nextMusicBrainzAt = 0L
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()

    override suspend fun load(album: AlbumSummary, language: String, refresh: Boolean): AlbumOnlineInfo? =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val artist = album.albumArtist?.takeIf { it.isNotBlank() } ?: album.artist
                if (album.title.isBlank() || artist.isNullOrBlank()) return@withLock null
                val lang = language.substringBefore('-').takeIf { it in listOf("zh", "ja", "en") } ?: "en"
                val key = listOf("v1", album.title, artist, album.year, album.trackCount, lang).joinToString("\u0000")
                val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
                val file = File(cacheDirectory, "$hash.json")
                val cachedJson = runCatching { if (file.length() in 1..512_000) JSONObject(file.readText()) else null }.getOrNull()
                val cached = runCatching { cachedJson?.optJSONObject("info")?.let(AlbumOnlineInfoParser::decode) }.getOrNull()
                val now = System.currentTimeMillis()
                val age = now - (cachedJson?.optLong("storedAt") ?: 0L)
                val ttl = when { cached?.partial == true -> 15 * 60_000L; cached == null -> 6 * 3600_000L; else -> 7 * 86400_000L }
                if (!refresh && cachedJson != null && age in 0 until ttl) return@withLock cached?.copy(cached = true)
                try {
                    val result = fetch(album.copy(albumArtist = artist), lang)
                    // Cache failures must not discard successfully fetched information.
                    runCatching {
                        cacheDirectory.mkdirs()
                        val json = JSONObject().put("storedAt", System.currentTimeMillis())
                            .put("info", result?.let(AlbumOnlineInfoParser::encode) ?: JSONObject.NULL)
                        val temporary = File(cacheDirectory, "$hash.tmp")
                        temporary.writeText(json.toString())
                        if (!temporary.renameTo(file)) temporary.delete()
                        cacheDirectory.listFiles()?.filter { it.extension == "json" }
                            ?.sortedByDescending { it.lastModified() }?.drop(64)?.forEach { it.delete() }
                    }
                    result
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (cached != null && age in 0..(30 * 86400_000L)) cached.copy(cached = true, stale = true)
                    else throw failure
                }
            }
        }

    private suspend fun fetch(album: AlbumSummary, language: String): AlbumOnlineInfo? {
        val search = musicBrainz("release/", mapOf("query" to AlbumOnlineInfoParser.query(album), "limit" to "25"))
        val id = AlbumOnlineInfoParser.selectRelease(search, album) ?: return null
        val release = musicBrainz("release/$id", mapOf("inc" to
            "artist-credits+labels+recordings+release-groups+url-rels+artist-rels+recording-level-rels+work-rels+work-level-rels"))
        var result = AlbumOnlineInfoParser.release(release, System.currentTimeMillis())
        try {
            val groupId = release.optJSONObject("release-group")?.text("id")
            val group = if (groupId?.matches(Regex("[a-fA-F0-9-]{36}")) == true)
                musicBrainz("release-group/$groupId", mapOf("inc" to "url-rels")) else JSONObject()
            val relations = group.objects("relations") + release.objects("relations")
            val wikiPage = resolveWikipedia(relations, language)
            if (wikiPage != null) {
                val (lang, title) = wikiPage
                val url = "https://$lang.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
                    .addQueryParameter("action", "query").addQueryParameter("format", "json")
                    .addQueryParameter("formatversion", "2").addQueryParameter("prop", "extracts|info|pageprops")
                    .addQueryParameter("explaintext", "1").addQueryParameter("exintro", "1")
                    .addQueryParameter("exchars", "4000").addQueryParameter("inprop", "url")
                    .addQueryParameter("redirects", "1").addQueryParameter("titles", title).build()
                val page = json(url).optJSONObject("query")?.objects("pages")?.firstOrNull()
                if (page != null && !page.has("missing") && page.optJSONObject("pageprops")?.has("disambiguation") != true) {
                    val description = page.text("extract")?.take(4000)
                    val sourceUrl = page.text("fullurl")?.toHttpUrlOrNull()
                    if (description != null && sourceUrl?.host == "$lang.wikipedia.org" && sourceUrl.isHttps) {
                        result = result.copy(description = description, wikipediaUrl = sourceUrl.toString(), wikipediaLanguage = lang)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A Wikipedia outage must not hide release details or credits already obtained.
            result = result.copy(partial = true)
        }
        return result
    }

    private suspend fun resolveWikipedia(relations: List<JSONObject>, language: String): Pair<String, String>? {
        val links = relations.mapNotNull { it.optJSONObject("url")?.text("resource")?.toHttpUrlOrNull() }
        val wikidata = links.firstOrNull { it.host == "www.wikidata.org" || it.host == "wikidata.org" }
        val entityId = wikidata?.pathSegments?.lastOrNull()?.takeIf { it.matches(Regex("Q[0-9]+")) }
        if (entityId != null) {
            val data = json("https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "wbgetentities").addQueryParameter("format", "json")
                .addQueryParameter("ids", entityId).addQueryParameter("props", "sitelinks")
                .addQueryParameter("sitefilter", "${language}wiki|enwiki").build())
            val sites = data.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("sitelinks")
            for (lang in listOf(language, "en").distinct()) {
                sites?.optJSONObject("${lang}wiki")?.text("title")?.let { return lang to it }
            }
        }
        for (lang in listOf(language, "en", "ja", "zh").distinct()) {
            val link = links.firstOrNull { it.host == "$lang.wikipedia.org" && it.encodedPath.startsWith("/wiki/") }
            if (link != null) return lang to link.pathSegments.drop(1).joinToString("/")
        }
        return null
    }

    private suspend fun musicBrainz(path: String, parameters: Map<String, String>): JSONObject {
        val wait = nextMusicBrainzAt - TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        if (wait > 0) delay(wait)
        nextMusicBrainzAt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + 1100L
        val url = "https://musicbrainz.org/ws/2/$path".toHttpUrl().newBuilder().addQueryParameter("fmt", "json")
        parameters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        return json(url.build())
    }

    private suspend fun json(url: HttpUrl): JSONObject {
        val body = suspendCancellableCoroutine<String> { continuation ->
            val request = Request.Builder().url(url).header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .header("Api-User-Agent", userAgent).build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    val content = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw IOException("Online album service HTTP ${it.code}")
                            val source = it.body?.source() ?: throw IOException("Empty online album response")
                            source.request(4_000_001L)
                            if (source.buffer.size > 4_000_000L) throw IOException("Online album response too large")
                            source.readUtf8()
                        }
                    }
                    if (continuation.isActive) content.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
        return JSONObject(body).also { if (it.has("error")) throw IOException("Online album service error") }
    }
}

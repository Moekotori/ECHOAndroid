package app.echo.android.data

import app.echo.android.model.library.AlbumOnlineInfo
import app.echo.android.model.library.AlbumOnlineInfoLoader
import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
class AlbumOnlineInfoRepository(
    private val cacheDirectory: File,
    appVersion: String = "development",
    private val musicBrainzGate: MusicBrainzRequestGate = MusicBrainzRequestGate(),
) : AlbumOnlineInfoLoader {
    private val userAgent = "ECHOAndroid/$appVersion (https://github.com/moekotori/echo)"
    private val gate = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()

    override suspend fun load(album: AlbumSummary, language: String, refresh: Boolean): AlbumOnlineInfo? =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val artist = AlbumOnlineQueryPolicy.artist(album)
                if (album.title.isBlank() || artist.isNullOrBlank()) return@withLock null
                val lang = AlbumWikipediaMatch.wikiLanguage(language)
                val variant = AlbumWikipediaMatch.chineseVariant(language)
                val key = listOf("v4", album.title, artist, album.year, album.trackCount, lang, variant ?: "").joinToString("\u0000")
                val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
                val file = File(cacheDirectory, "$hash.json")
                val cachedJson = runCatching { if (file.length() in 1..512_000) JSONObject(file.readText()) else null }.getOrNull()
                val cached = runCatching { cachedJson?.optJSONObject("info")?.let(AlbumOnlineInfoParser::decode) }.getOrNull()
                val now = System.currentTimeMillis()
                val age = now - (cachedJson?.optLong("storedAt") ?: 0L)
                val ttl = when { cached?.partial == true -> 15 * 60_000L; cached == null -> 6 * 3600_000L; else -> 7 * 86400_000L }
                if (!refresh && cachedJson != null && cachedJson.has("info") &&
                    (cachedJson.isNull("info") || cached != null) && age in 0 until ttl) return@withLock cached?.copy(cached = true)
                try {
                    val result = AlbumOnlineLookup(::musicBrainz, ::json).fetch(album.copy(albumArtist = artist), lang, variant)
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

    private suspend fun musicBrainz(path: String, parameters: Map<String, String>): JSONObject = musicBrainzGate.run {
        val url = "https://musicbrainz.org/ws/2/$path".toHttpUrl().newBuilder().addQueryParameter("fmt", "json")
        parameters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        json(url.build())
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

package app.echo.android.data

import app.echo.android.model.library.LibrarySource
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class SubsonicEndpoint(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    val normalizedBaseUrl: String = normalizeSubsonicBaseUrl(baseUrl)

    val sourceId: String =
        "${LibrarySource.Subsonic.id}:${stableSourceHash("${normalizedBaseUrl.lowercase(Locale.ROOT)}|${username.trim()}")}"
}

internal data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String?,
    val coverArt: String?,
    val year: Int?,
    val songCount: Int,
)

internal data class SubsonicSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val coverArt: String?,
    val durationSeconds: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val contentType: String?,
    val suffix: String?,
    val sizeBytes: Long,
    val bitRateKbps: Int?,
    val path: String?,
)

internal class SubsonicClient(
    private val endpoint: SubsonicEndpoint,
    private val httpGet: (String) -> String? = ::defaultHttpGet,
    private val saltFactory: () -> String = ::randomTokenSalt,
) {
    fun ping() {
        request("ping.view")
    }

    fun fetchAlbums(
        pageSize: Int = AlbumPageSize,
        maxAlbums: Int = MaxAlbumsPerSync,
    ): List<SubsonicAlbum> {
        val albums = ArrayList<SubsonicAlbum>()
        val seenIds = HashSet<String>()
        var offset = 0
        var effectivePageSize = pageSize.coerceAtLeast(1)
        while (albums.size < maxAlbums) {
            val remaining = (maxAlbums - albums.size).coerceAtMost(effectivePageSize)
            val root = request(
                path = "getAlbumList2.view",
                params = listOf(
                    "type" to "alphabeticalByName",
                    "size" to remaining.toString(),
                    "offset" to offset.toString(),
                ),
            )
            val batch = root.optJSONObject("albumList2")
                ?.jsonObjects("album")
                ?.map { it.toSubsonicAlbum() }
                .orEmpty()
            if (batch.isEmpty()) break
            val unique = batch.filter { album -> album.id.isNotBlank() && seenIds.add(album.id) }
            if (unique.isEmpty()) break
            albums += unique
            offset += batch.size
            if (batch.size < remaining) {
                effectivePageSize = batch.size.coerceAtLeast(1)
                continue
            }
        }
        return albums
    }

    fun fetchAlbumSongs(album: SubsonicAlbum): List<SubsonicSong> {
        if (album.id.isBlank()) return emptyList()
        val root = request(
            path = "getAlbum.view",
            params = listOf("id" to album.id),
        )
        val albumObject = root.optJSONObject("album") ?: return emptyList()
        return albumObject.jsonObjects("song")
            .map { it.toSubsonicSong(album) }
            .filter { it.id.isNotBlank() }
    }

    fun fetchSongsBySearch3(
        pageSize: Int = SongPageSize,
        maxSongs: Int = MaxSongsPerSync,
    ): List<SubsonicSong> {
        for (query in SubsonicSyncPolicy.Search3QueryAttempts) {
            val songs = fetchSongsBySearch3Query(
                query = query,
                pageSize = pageSize,
                maxSongs = maxSongs,
            )
            if (songs.isNotEmpty()) return songs
        }
        return emptyList()
    }

    private fun fetchSongsBySearch3Query(
        query: String,
        pageSize: Int,
        maxSongs: Int,
    ): List<SubsonicSong> {
        val songs = ArrayList<SubsonicSong>()
        val seenIds = HashSet<String>()
        var offset = 0
        var effectivePageSize = pageSize.coerceAtLeast(1)
        while (songs.size < maxSongs) {
            val remaining = (maxSongs - songs.size).coerceAtMost(effectivePageSize)
            val root = request(
                path = "search3.view",
                params = listOf(
                    "query" to query,
                    "songCount" to remaining.toString(),
                    "songOffset" to offset.toString(),
                    "albumCount" to "0",
                    "artistCount" to "0",
                ),
            )
            val batch = root.optJSONObject("searchResult3")
                ?.jsonObjects("song")
                ?.map { it.toSubsonicSong() }
                .orEmpty()
            if (batch.isEmpty()) break
            val unique = batch.filter { song -> song.id.isNotBlank() && seenIds.add(song.id) }
            if (unique.isEmpty()) break
            songs += unique
            offset += batch.size
            if (batch.size < remaining) {
                effectivePageSize = batch.size.coerceAtLeast(1)
                continue
            }
        }
        return songs
    }

    fun streamUrl(songId: String): String =
        unsignedSubsonicResourceUrl(endpoint.normalizedBaseUrl, "stream.view", songId)

    fun coverArtUrl(coverArt: String?): String? =
        coverArt?.takeIf { it.isNotBlank() }
            ?.let {
                unsignedSubsonicResourceUrl(
                    baseUrl = endpoint.normalizedBaseUrl,
                    path = "getCoverArt.view",
                    id = it,
                    extraParams = listOf("size" to CoverArtSizePx.toString()),
                )
            }

    private fun request(path: String, params: List<Pair<String, String>> = emptyList()): JSONObject {
        val url = buildUrl(path, params)
        val body = httpGet(url) ?: error("远程服务器无响应")
        return parseSubsonicResponse(body)
    }

    private fun buildUrl(path: String, params: List<Pair<String, String>>): String {
        val salt = saltFactory().takeIf { it.isNotBlank() }
            ?: error("Subsonic authentication salt is blank")
        val token = md5(endpoint.password + salt)
        val authParams = listOf(
            "u" to endpoint.username.trim(),
            "t" to token,
            "s" to salt,
            "v" to ApiVersion,
            "c" to ClientId,
            "f" to "json",
        )
        return (authParams + params).joinToString(
            separator = "&",
            prefix = "${endpoint.normalizedBaseUrl}/rest/$path?",
        ) { (name, value) ->
            "${name.urlEncode()}=${value.urlEncode()}"
        }
    }

    internal companion object {
        const val ApiVersion = "1.16.1"
        const val ClientId = "ECHOAndroid"
        const val AlbumPageSize = 500
        const val SongPageSize = 500
        const val CoverArtSizePx = 600
        const val MaxAlbumsPerSync = 2_000
        const val MaxSongsPerSync = 20_000
        const val MaxResponseBytes = 8_000_000
    }
}

internal fun SubsonicSong.toLibraryTrackEntity(
    endpoint: SubsonicEndpoint,
    scanRunId: Long,
): LibraryTrackEntity {
    val contentUri = unsignedSubsonicResourceUrl(endpoint.normalizedBaseUrl, "stream.view", id)
    val artworkUri = coverArt
        ?.takeIf { it.isNotBlank() }
        ?.let {
            unsignedSubsonicResourceUrl(
                baseUrl = endpoint.normalizedBaseUrl,
                path = "getCoverArt.view",
                id = it,
                extraParams = listOf("size" to SubsonicClient.CoverArtSizePx.toString()),
            )
        }
    return LibraryTrackEntity(
        id = "${endpoint.sourceId}:song:$id",
        contentUri = contentUri,
        title = title.ifBlank { path?.substringAfterLast('/') ?: "Unknown Track" },
        artist = artist.ifBlank { "Unknown Artist" },
        album = album,
        albumArtist = albumArtist,
        artworkUri = artworkUri,
        durationMs = durationSeconds.coerceAtLeast(0L) * 1000L,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        mimeType = contentType ?: suffix?.let { "audio/$it" },
        sizeBytes = sizeBytes,
        sampleRateHz = null,
        dateModifiedSeconds = LibraryFingerprintPolicy.remoteDateModifiedSeconds(scanRunId),
        source = endpoint.sourceId,
        relativePath = path?.substringBeforeLast('/', missingDelimiterValue = ""),
        lastSeenScanRunId = scanRunId,
    ).withScanMetadata(scanRunId)
}

internal fun parseSubsonicResponse(body: String): JSONObject {
    val json = runCatching { JSONObject(body) }
        .getOrElse { error("远程服务器返回了无法解析的数据") }
    return json.subsonicRoot()
}

internal fun subsonicHttpBody(responseCode: Int, successBody: String?, errorBody: String?): String? {
    val body = if (responseCode in 200..299) successBody else (errorBody ?: successBody)
    return body?.takeIf { it.isNotBlank() }
}

private fun JSONObject.subsonicRoot(): JSONObject {
    val root = optJSONObject("subsonic-response") ?: error("不是 Subsonic 兼容响应")
    val status = root.optString("status")
    if (!status.equals("ok", ignoreCase = true)) {
        val message = root.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: "Subsonic 认证或请求失败"
        error(message)
    }
    return root
}

private fun JSONObject.toSubsonicAlbum(): SubsonicAlbum =
    SubsonicAlbum(
        id = optJsonString("id"),
        name = optJsonString("name").ifBlank { optJsonString("album") },
        artist = optJsonString("artist").takeIf { it.isNotBlank() },
        coverArt = optJsonString("coverArt").takeIf { it.isNotBlank() },
        year = optInt("year").takeIf { it > 0 },
        songCount = optInt("songCount").coerceAtLeast(0),
    )

private fun JSONObject.toSubsonicSong(album: SubsonicAlbum? = null): SubsonicSong =
    SubsonicSong(
        id = optJsonString("id"),
        title = optJsonString("title"),
        artist = optJsonString("artist").ifBlank { album?.artist.orEmpty() },
        album = optJsonString("album").ifBlank { album?.name.orEmpty() }.takeIf { it.isNotBlank() },
        albumArtist = optJsonString("albumArtist").ifBlank { album?.artist.orEmpty() }.takeIf { it.isNotBlank() },
        coverArt = optJsonString("coverArt").ifBlank { album?.coverArt.orEmpty() }.takeIf { it.isNotBlank() },
        durationSeconds = optLong("duration", 0L),
        trackNumber = optInt("track").takeIf { it > 0 },
        discNumber = optInt("discNumber").takeIf { it > 0 },
        year = optInt("year").takeIf { it > 0 } ?: album?.year,
        contentType = optJsonString("contentType").takeIf { it.isNotBlank() },
        suffix = optJsonString("suffix").takeIf { it.isNotBlank() },
        sizeBytes = optLong("size", 0L),
        bitRateKbps = optInt("bitRate").takeIf { it > 0 },
        path = optJsonString("path").takeIf { it.isNotBlank() },
    )

private fun JSONObject.optJsonString(name: String): String {
    if (!has(name) || isNull(name)) return ""
    return optString(name)
}

private val SharedSubsonicHttpClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dispatcher(
            okhttp3.Dispatcher().apply {
                maxRequestsPerHost = 16
            },
        )
        .build()

private fun defaultHttpGet(url: String): String? {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "ECHOAndroid/0.1")
        .get()
        .build()
    return runCatching {
        SharedSubsonicHttpClient.newCall(request).execute().use { response ->
            val body = response.body ?: return@use null
            val declaredLength = body.contentLength()
            if (declaredLength > SubsonicClient.MaxResponseBytes) return@use null
            val bytes = body.bytes()
            if (bytes.size > SubsonicClient.MaxResponseBytes) return@use null
            val text = String(bytes, StandardCharsets.UTF_8)
            val successBody = if (response.isSuccessful) text else null
            val errorBody = if (!response.isSuccessful) text else null
            subsonicHttpBody(response.code, successBody, errorBody)
        }
    }.getOrNull()
}

private fun JSONArray.objects(): Sequence<JSONObject> =
    sequence {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { yield(it) }
        }
    }

internal fun normalizeSubsonicBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if (hasHttpScheme(trimmed)) trimmed else "http://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return trimmed.trimEnd('/')
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
        ?.takeIf { it == "http" || it == "https" }
        ?: "http"
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return withScheme.substringBefore('#').trimEnd('/')
    var path = uri.path.orEmpty()
    while (true) {
        val collapsed = path.trimEnd('/')
        val lower = collapsed.lowercase(Locale.ROOT)
        val stripped = when {
            lower.endsWith("/app") -> collapsed.dropLast(4)
            lower.endsWith("/rest") -> collapsed.dropLast(5)
            else -> collapsed
        }
        if (stripped == collapsed) {
            path = collapsed
            break
        }
        path = stripped
    }
    val pathPart = path.trimEnd('/').let { remaining ->
        when {
            remaining.isBlank() || remaining == "/" -> ""
            remaining.startsWith("/") -> remaining
            else -> "/$remaining"
        }
    }
    val hostPart = if (host.contains(':')) "[$host]" else host
    val port = uri.port
    val defaultPort = if (scheme == "https") 443 else 80
    val portPart = if (port != -1 && port != defaultPort) ":$port" else ""
    return "$scheme://$hostPart$portPart$pathPart"
}

internal fun unsignedSubsonicResourceUrl(
    baseUrl: String,
    path: String,
    id: String,
    extraParams: List<Pair<String, String>> = emptyList(),
): String {
    val origin = baseUrl.trimEnd('/')
    val query = ArrayList<Pair<String, String>>(1 + extraParams.size)
    query += "id" to id
    query += extraParams
    return query.joinToString("&", prefix = "$origin/rest/$path?") { (name, value) ->
        "${name.urlEncode()}=${value.urlEncode()}"
    }
}

private fun hasHttpScheme(value: String): Boolean {
    val colon = value.indexOf(':')
    if (colon <= 0) return false
    val scheme = value.substring(0, colon)
    return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private val TokenSaltRandom = SecureRandom()

private fun randomTokenSalt(): String =
    ByteArray(12)
        .also(TokenSaltRandom::nextBytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun stableSourceHash(value: String): String =
    value.hashCode().absoluteValue.toString(36)

private fun md5(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

package app.echo.android.data

import android.content.Context
import androidx.annotation.StringRes
import app.echo.android.model.library.LibrarySource
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue
import okhttp3.Interceptor
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

internal data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val coverArt: String?,
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
    val sampleRateHz: Int? = null,
)

internal class SubsonicClient(
    private val endpoint: SubsonicEndpoint,
    httpGet: ((String) -> String?)? = null,
    private val saltFactory: () -> String = ::randomTokenSalt,
    private val appContext: Context? = null,
) {
    private val httpGet: (String) -> String? = httpGet ?: { defaultHttpGet(it, appContext) }
    fun ping() {
        request("ping.view")
    }

    internal fun fetchAlbums(
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

    internal fun fetchAlbumSongs(album: SubsonicAlbum): List<SubsonicSong> {
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

    internal fun fetchSongsBySearch3(
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

    internal fun fetchPlaylists(): List<SubsonicPlaylist> {
        val root = request("getPlaylists.view")
        return root.optJSONObject("playlists")
            ?.jsonObjects("playlist")
            ?.map { playlist ->
                SubsonicPlaylist(
                    id = playlist.optJsonString("id"),
                    name = playlist.optJsonString("name").ifBlank { playlist.optJsonString("title") },
                    coverArt = playlist.optJsonString("coverArt").takeIf { it.isNotBlank() },
                    songCount = playlist.optInt("songCount").coerceAtLeast(0),
                )
            }
            ?.filter { it.id.isNotBlank() }
            .orEmpty()
    }

    internal fun fetchPlaylistSongs(playlistId: String): List<SubsonicSong> {
        if (playlistId.isBlank()) return emptyList()
        val root = request("getPlaylist.view", listOf("id" to playlistId))
        return root.optJSONObject("playlist")
            ?.jsonObjects("entry")
            .orEmpty()
            .ifEmpty { root.optJSONObject("playlist")?.jsonObjects("song").orEmpty() }
            .map { it.toSubsonicSong() }
            .filter { it.id.isNotBlank() }
    }

    fun fetchLyricsText(songId: String, artist: String, title: String): String? {
        if (songId.isNotBlank()) {
            val byId = runCatching { lyricsBySongId(songId) }.getOrNull()
            if (!byId.isNullOrBlank()) return byId
        }
        if (artist.isBlank() && title.isBlank()) return null
        return runCatching { lyricsByArtistTitle(artist, title) }.getOrNull()
    }

    fun submitListen(songId: String, submission: Boolean, timeEpochMs: Long? = null) {
        if (songId.isBlank()) return
        val params = buildList {
            add("id" to songId)
            add("submission" to if (submission) "true" else "false")
            if (submission && timeEpochMs != null && timeEpochMs > 0L) {
                add("time" to timeEpochMs.toString())
            }
        }
        request("scrobble.view", params)
    }

    private fun lyricsBySongId(songId: String): String? {
        val root = request("getLyricsBySongId.view", listOf("id" to songId))
        val structured = root.optJSONObject("lyricsList")
            ?.jsonObjects("structuredLyrics")
            .orEmpty()
        val rendered = structured.joinToString("\n\n") { block ->
            val offsetMs = block.optLong("offset", 0L)
            block.jsonObjects("line").mapNotNull { line ->
                val value = line.optJsonString("value")
                if (value.isBlank()) return@mapNotNull null
                val startMs = line.optLong("start", -1L)
                if (startMs >= 0L) {
                    "${formatLrcTimestamp(startMs + offsetMs)}$value"
                } else {
                    value
                }
            }.joinToString("\n")
        }
        return rendered.takeIf { it.isNotBlank() }
    }

    private fun lyricsByArtistTitle(artist: String, title: String): String? {
        val root = request(
            "getLyrics.view",
            listOf(
                "artist" to artist,
                "title" to title,
            ),
        )
        return root.optJSONObject("lyrics")
            ?.optJsonString("value")
            ?.takeIf { it.isNotBlank() }
    }

    private fun request(path: String, params: List<Pair<String, String>> = emptyList()): JSONObject {
        val url = buildUrl(path, params)
        val body = try {
            httpGet(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IllegalStateException) {
            throw error
        } catch (error: Throwable) {
            error(subsonicTransportFailureMessage(error, appContext))
        } ?: error(subsonicUnreachableMessage(appContext))
        return parseSubsonicResponse(body, appContext)
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
        sampleRateHz = sampleRateHz,
        dateModifiedSeconds = LibraryFingerprintPolicy.remoteDateModifiedSeconds(scanRunId),
        source = endpoint.sourceId,
        relativePath = path?.substringBeforeLast('/', missingDelimiterValue = ""),
        lastSeenScanRunId = scanRunId,
    ).withScanMetadata(scanRunId)
}

internal fun parseSubsonicResponse(body: String, context: Context? = null): JSONObject {
    if (looksLikeHtml(body)) {
        error(subsonicIncompatibleResponseMessage(context))
    }
    val json = runCatching { JSONObject(body) }
        .getOrElse { error(subsonicInvalidJsonMessage(context)) }
    return json.subsonicRoot(context)
}

internal fun subsonicHttpBody(responseCode: Int, successBody: String?, errorBody: String?): String? {
    val body = if (responseCode in 200..299) successBody else (errorBody ?: successBody)
    return body?.takeIf { it.isNotBlank() }
}

internal fun subsonicHttpGetResult(
    requestUrl: String,
    responseCode: Int,
    isRedirect: Boolean,
    location: String?,
    body: String?,
    context: Context? = null,
): String {
    if (isRedirect || responseCode in 300..399) {
        error(subsonicRedirectFailureMessage(requestUrl, location, context))
    }
    val text = body?.takeIf { it.isNotBlank() }
        ?: error(subsonicHttpStatusMessage(responseCode, context))
    if (looksLikeHtml(text)) {
        error(subsonicIncompatibleResponseMessage(context))
    }
    return subsonicHttpBody(
        responseCode = responseCode,
        successBody = if (responseCode in 200..299) text else null,
        errorBody = if (responseCode !in 200..299) text else null,
    ) ?: error(subsonicHttpStatusMessage(responseCode, context))
}

internal fun looksLikeHtml(body: String): Boolean {
    val trimmed = body.trimStart()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true)
}

private fun JSONObject.subsonicRoot(context: Context? = null): JSONObject {
    val root = optJSONObject("subsonic-response") ?: error(subsonicIncompatibleResponseMessage(context))
    val status = root.optString("status")
    if (!status.equals("ok", ignoreCase = true)) {
        val message = root.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: subsonicRequestFailedMessage(context)
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
        sampleRateHz = optInt("samplingRate").takeIf { it > 0 }
            ?: optInt("sampleRate").takeIf { it > 0 },
    )

private fun JSONObject.optJsonString(name: String): String {
    if (!has(name) || isNull(name)) return ""
    return optString(name)
}

private val SharedSubsonicHttpClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(sameHostRedirectInterceptor())
        .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
        .dispatcher(
            okhttp3.Dispatcher().apply {
                maxRequestsPerHost = 16
            },
        )
        .build()

private fun defaultHttpGet(url: String, context: Context? = null): String {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "ECHOAndroid/0.1")
        .get()
        .build()
    return try {
        SharedSubsonicHttpClient.newCall(request).execute().use { response ->
            val body = response.body ?: error(subsonicHttpStatusMessage(response.code, context))
            val declaredLength = body.contentLength()
            if (declaredLength > SubsonicClient.MaxResponseBytes) {
                error(subsonicResponseTooLargeMessage(context))
            }
            val bytes = body.bytes()
            if (bytes.size > SubsonicClient.MaxResponseBytes) {
                error(subsonicResponseTooLargeMessage(context))
            }
            val text = String(bytes, StandardCharsets.UTF_8)
            subsonicHttpGetResult(
                requestUrl = url,
                responseCode = response.code,
                isRedirect = response.isRedirect,
                location = response.header("Location"),
                body = text,
                context = context,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IllegalStateException) {
        throw error
    } catch (error: Throwable) {
        error(subsonicTransportFailureMessage(error, context))
    }
}

private fun Context?.subsonicString(@StringRes id: Int, fallback: String, vararg args: Any): String {
    if (this == null) return fallback
    return try {
        if (args.isEmpty()) getString(id) else getString(id, *args)
    } catch (_: Throwable) {
        fallback
    }
}

internal fun subsonicTransportFailureMessage(cause: Throwable, context: Context? = null): String {
    val chain = ArrayList<Throwable>()
    var current: Throwable? = cause
    while (current != null && chain.add(current)) {
        val next = current.cause
        current = if (next != null && next in chain) null else next
    }
    if (chain.any(::isSubsonicCertificateFailure)) return subsonicCertificateTrustMessage(context)
    if (chain.any { it is UnknownHostException }) return subsonicUnknownHostMessage(context)
    if (chain.any(::isSubsonicTimeoutFailure)) return subsonicTimeoutMessage(context)
    if (chain.any { it is ConnectException || it is NoRouteToHostException }) {
        return subsonicConnectionRefusedMessage(context)
    }
    if (chain.any(::isSubsonicCleartextBlocked)) return subsonicCleartextBlockedMessage(context)
    return subsonicUnreachableMessage(context)
}

internal fun subsonicUnreachableMessage(context: Context? = null): String =
    context.subsonicString(R.string.subsonic_unreachable, "Can't reach the Navidrome/Subsonic server.")

internal fun subsonicCertificateTrustMessage(context: Context? = null): String =
    context.subsonicString(
        R.string.subsonic_certificate,
        "This HTTPS certificate isn't trusted. Use a certificate the system trusts, or HTTP on your LAN.",
    )

internal fun subsonicTimeoutMessage(context: Context? = null): String =
    context.subsonicString(R.string.subsonic_timeout, "The Navidrome/Subsonic server timed out.")

internal fun subsonicUnknownHostMessage(context: Context? = null): String =
    context.subsonicString(
        R.string.subsonic_unknown_host,
        "Can't resolve the server address. Check the URL and your network.",
    )

internal fun subsonicConnectionRefusedMessage(context: Context? = null): String =
    context.subsonicString(
        R.string.subsonic_connection_refused,
        "The server refused the connection. Check the address and that Navidrome is running.",
    )

internal fun subsonicCleartextBlockedMessage(context: Context? = null): String =
    context.subsonicString(R.string.subsonic_cleartext_blocked, "HTTP (not HTTPS) is blocked for this address.")

internal fun subsonicHttpStatusMessage(code: Int, context: Context? = null): String =
    context.subsonicString(R.string.subsonic_http_status, "The server returned HTTP $code.", code)

internal fun subsonicResponseTooLargeMessage(context: Context? = null): String =
    context.subsonicString(R.string.subsonic_response_too_large, "The server response was too large to read.")

internal fun subsonicInvalidJsonMessage(context: Context? = null): String =
    context.subsonicString(R.string.subsonic_invalid_json, "The server returned data that isn't valid JSON.")

internal fun subsonicIncompatibleResponseMessage(context: Context? = null): String =
    context.subsonicString(
        R.string.subsonic_incompatible,
        "This is not a Subsonic-compatible response. Check the server URL.",
    )

internal fun subsonicRedirectFailureMessage(
    requestUrl: String,
    location: String?,
    context: Context? = null,
): String {
    val host = redirectTargetHost(requestUrl, location)
    return if (host.isNullOrBlank()) {
        context.subsonicString(
            R.string.subsonic_redirected_unknown,
            "The server redirected to a different site. This address isn't serving Navidrome/Subsonic. If you use NAS remote access, make sure the NAS is online.",
        )
    } else {
        context.subsonicString(
            R.string.subsonic_redirected,
            "The server redirected to $host. This address isn't serving Navidrome/Subsonic. If you use NAS remote access, make sure the NAS is online.",
            host,
        )
    }
}

internal fun redirectTargetHost(requestUrl: String, location: String?): String? {
    if (location.isNullOrBlank()) return null
    val resolved = runCatching { URI(requestUrl).resolve(location.trim()) }.getOrNull() ?: return null
    return resolved.host?.takeIf { it.isNotBlank() }
}

internal fun subsonicRequestFailedMessage(context: Context? = null): String =
    context.subsonicString(R.string.subsonic_request_failed, "Subsonic authentication or request failed.")

private fun isSubsonicCertificateFailure(error: Throwable): Boolean {
    if (error is SSLHandshakeException ||
        error is SSLPeerUnverifiedException ||
        error is CertificateException ||
        error is SSLException
    ) {
        return true
    }
    val name = error.javaClass.name
    if (name.contains("SSLHandshake", ignoreCase = true) ||
        name.contains("SSLPeerUnverified", ignoreCase = true) ||
        name.contains("CertPath", ignoreCase = true) ||
        name.contains("CertificateException", ignoreCase = true)
    ) {
        return true
    }
    val message = error.message.orEmpty()
    return message.contains("Trust anchor", ignoreCase = true) ||
        message.contains("CertPathValidator", ignoreCase = true)
}

private fun isSubsonicTimeoutFailure(error: Throwable): Boolean {
    if (error is SocketTimeoutException) return true
    if (error is InterruptedIOException &&
        error.message.orEmpty().contains("timeout", ignoreCase = true)
    ) {
        return true
    }
    return false
}

private fun isSubsonicCleartextBlocked(error: Throwable): Boolean =
    error is UnknownServiceException ||
        error.message.orEmpty().contains("CLEARTEXT", ignoreCase = true)

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

internal fun shouldFollowSameHostRedirect(fromUrl: String, toUrl: String): Boolean {
    val from = runCatching { URI(fromUrl) }.getOrNull() ?: return false
    val to = runCatching { URI(toUrl) }.getOrNull() ?: return false
    val fromHost = from.host?.lowercase(Locale.ROOT) ?: return false
    val toHost = to.host?.lowercase(Locale.ROOT) ?: return false
    if (fromHost != toHost) return false
    val fromHttps = from.scheme.equals("https", ignoreCase = true)
    val toHttps = to.scheme.equals("https", ignoreCase = true)
    if (fromHttps && !toHttps) return false
    return to.scheme.equals("http", ignoreCase = true) || toHttps
}

internal fun sameHostRedirectInterceptor(): Interceptor =
    Interceptor { chain ->
        var request = chain.request()
        var response = chain.proceed(request)
        var hops = 0
        while (response.isRedirect && hops < 5) {
            val location = response.header("Location") ?: break
            val nextUrl = response.request.url.resolve(location) ?: break
            if (!shouldFollowSameHostRedirect(request.url.toString(), nextUrl.toString())) {
                break
            }
            response.close()
            request = request.newBuilder().url(nextUrl).build()
            response = chain.proceed(request)
            hops += 1
        }
        response
    }

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

fun fetchSubsonicLyricsText(
    endpoint: SubsonicEndpoint,
    songId: String,
    artist: String,
    title: String,
): String? = SubsonicClient(endpoint).fetchLyricsText(songId, artist, title)

fun submitSubsonicListen(
    endpoint: SubsonicEndpoint,
    songId: String,
    submission: Boolean,
    timeEpochMs: Long? = null,
) {
    SubsonicClient(endpoint).submitListen(
        songId = songId,
        submission = submission,
        timeEpochMs = timeEpochMs,
    )
}

fun subsonicSongIdFromTrack(trackId: String, source: String = ""): String? {
    val isSubsonic = source.startsWith("${LibrarySource.Subsonic.id}:") ||
        source == LibrarySource.Subsonic.id ||
        trackId.startsWith("${LibrarySource.Subsonic.id}:")
    if (!isSubsonic) return null
    val marker = ":song:"
    val index = trackId.indexOf(marker)
    if (index < 0) return null
    return trackId.substring(index + marker.length).takeIf { it.isNotBlank() }
}

private fun formatLrcTimestamp(startMs: Long): String {
    val total = startMs.coerceAtLeast(0L)
    val minutes = total / 60_000L
    val seconds = (total % 60_000L) / 1_000L
    val hundredths = (total % 1_000L) / 10L
    return "[%02d:%02d.%02d]".format(minutes, seconds, hundredths)
}

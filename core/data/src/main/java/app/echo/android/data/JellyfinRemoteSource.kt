package app.echo.android.data

import app.echo.android.model.library.LibrarySource
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class JellyfinEndpoint(
    val baseUrl: String,
    val username: String,
    val password: String,
    val accessToken: String? = null,
    val userId: String? = null,
) {
    val normalizedBaseUrl: String = normalizeJellyfinBaseUrl(baseUrl)

    val sourceId: String =
        "${LibrarySource.Jellyfin.id}:${stableJellyfinSourceHash("${normalizedBaseUrl.lowercase(Locale.ROOT)}|${username.trim()}")}"
}

internal data class JellyfinSession(
    val accessToken: String,
    val userId: String,
)

internal data class JellyfinAudioItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val sizeBytes: Long,
    val container: String?,
    val path: String?,
    val imageTag: String?,
)

internal data class JellyfinAudioPage(
    val items: List<JellyfinAudioItem>,
    val totalCount: Int,
)

internal class JellyfinClient(
    private val endpoint: JellyfinEndpoint,
    private val http: (Request) -> String = ::defaultJellyfinHttp,
) {
    fun authenticate(): JellyfinSession {
        val body = JSONObject()
            .put("Username", endpoint.username.trim())
            .put("Pw", endpoint.password)
            .put("Password", endpoint.password)
            .toString()
        val json = JSONObject(
            http(
                Request.Builder()
                    .url("${endpoint.normalizedBaseUrl}/Users/AuthenticateByName")
                    .header("Content-Type", "application/json")
                    .header("X-Emby-Authorization", jellyfinAuthorization(token = null))
                    .post(body.toRequestBody(JsonMediaType))
                    .build(),
            ),
        )
        val token = json.optString("AccessToken").trim().takeIf { it.isNotEmpty() }
            ?: error(jellyfinAuthFailedMessage())
        val userId = json.optJSONObject("User")?.optString("Id")?.trim().orEmpty()
            .ifBlank { json.optString("UserId").trim() }
            .ifBlank { error(jellyfinAuthFailedMessage()) }
        return JellyfinSession(accessToken = token, userId = userId)
    }

    fun fetchAudioPage(
        session: JellyfinSession,
        startIndex: Int,
        limit: Int = PageSize,
    ): JellyfinAudioPage {
        val url = buildString {
            append(endpoint.normalizedBaseUrl)
            append("/Users/")
            append(session.userId.urlEncode())
            append("/Items?IncludeItemTypes=Audio&Recursive=true&EnableImageTypes=Primary")
            append("&Fields=Path,ProviderIds,ProductionYear,IndexNumber,ParentIndexNumber,Album,AlbumArtist,Artists,RunTimeTicks,Size,Container")
            append("&StartIndex=")
            append(startIndex.coerceAtLeast(0))
            append("&Limit=")
            append(limit.coerceIn(1, PageSize))
            append("&SortBy=Album,IndexNumber,SortName")
        }
        val json = JSONObject(
            http(
                Request.Builder()
                    .url(url)
                    .header("X-Emby-Token", session.accessToken)
                    .header("X-Emby-Authorization", jellyfinAuthorization(session.accessToken))
                    .get()
                    .build(),
            ),
        )
        val items = json.optJSONArray("Items") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.toJellyfinAudioItem()?.let(::add)
            }
        }
        return JellyfinAudioPage(
            items = tracks,
            totalCount = json.optInt("TotalRecordCount", tracks.size).coerceAtLeast(tracks.size),
        )
    }

    internal companion object {
        const val PageSize = 500
        const val MaxTracksPerSync = 20_000
        const val CoverArtWidthPx = 600
        const val MaxResponseBytes = 8_000_000
    }
}

internal fun JellyfinAudioItem.toLibraryTrackEntity(
    endpoint: JellyfinEndpoint,
    scanRunId: Long,
): LibraryTrackEntity {
    val contentUri = jellyfinStreamUrl(endpoint.normalizedBaseUrl, id)
    val artworkUri = imageTag?.takeIf { it.isNotBlank() }?.let {
        jellyfinPrimaryImageUrl(endpoint.normalizedBaseUrl, id, it)
    }
    return LibraryTrackEntity(
        id = "${endpoint.sourceId}:item:$id",
        contentUri = contentUri,
        title = title.ifBlank { path?.substringAfterLast('/') ?: "Unknown Track" },
        artist = artist.ifBlank { "Unknown Artist" },
        album = album,
        albumArtist = albumArtist,
        artworkUri = artworkUri,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        mimeType = container?.takeIf { it.isNotBlank() }?.let { "audio/${it.lowercase(Locale.ROOT)}" },
        sizeBytes = sizeBytes,
        dateModifiedSeconds = LibraryFingerprintPolicy.remoteDateModifiedSeconds(scanRunId),
        source = endpoint.sourceId,
        relativePath = path?.substringBeforeLast('/', missingDelimiterValue = ""),
        lastSeenScanRunId = scanRunId,
    ).withScanMetadata(scanRunId)
}

internal fun normalizeJellyfinBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return trimmed.trimEnd('/')
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return trimmed.trimEnd('/')
    val scheme = uri.scheme?.lowercase(Locale.ROOT)?.takeIf { it == "http" || it == "https" } ?: "http"
    val port = uri.port.takeIf { it > 0 }
    var path = (uri.path ?: "").trimEnd('/')
    path = path.removeSuffix("/web/index.html").removeSuffix("/web").trimEnd('/')
    if (path == "/" || path.equals("/jellyfin", ignoreCase = true)) path = ""
    return buildString {
        append(scheme)
        append("://")
        append(host)
        if (port != null) {
            append(':')
            append(port)
        }
        append(path)
    }
}

internal fun jellyfinStreamUrl(baseUrl: String, itemId: String): String =
    "${baseUrl.trimEnd('/')}/Audio/${itemId.trim()}/stream?static=true"

internal fun jellyfinPrimaryImageUrl(baseUrl: String, itemId: String, tag: String): String =
    "${baseUrl.trimEnd('/')}/Items/${itemId.trim()}/Images/Primary?maxWidth=${JellyfinClient.CoverArtWidthPx}&tag=${tag.urlEncode()}"

internal fun jellyfinAuthorization(token: String?): String {
    val parts = mutableListOf(
        "Client=\"ECHOAndroid\"",
        "Device=\"Android\"",
        "DeviceId=\"echo-android\"",
        "Version=\"0.1\"",
    )
    token?.takeIf { it.isNotBlank() }?.let { parts += "Token=\"$it\"" }
    return "MediaBrowser ${parts.joinToString(", ")}"
}

private fun JSONObject.toJellyfinAudioItem(): JellyfinAudioItem? {
    val id = optString("Id").trim().ifBlank { optString("id").trim() }
    if (id.isBlank()) return null
    val artists = optJSONArray("Artists")
    val artistFromArray = if (artists != null && artists.length() > 0) {
        artists.optString(0).trim()
    } else {
        ""
    }
    val ticks = optLong("RunTimeTicks", 0L)
    return JellyfinAudioItem(
        id = id,
        title = optString("Name").ifBlank { optString("Album") },
        artist = artistFromArray.ifBlank { optString("AlbumArtist") },
        album = optString("Album").takeIf { it.isNotBlank() },
        albumArtist = optString("AlbumArtist").takeIf { it.isNotBlank() },
        year = optInt("ProductionYear").takeIf { it > 0 },
        trackNumber = optInt("IndexNumber").takeIf { it > 0 },
        discNumber = optInt("ParentIndexNumber").takeIf { it > 0 },
        durationMs = if (ticks > 0L) ticks / 10_000L else 0L,
        sizeBytes = optLong("Size", 0L),
        container = optString("Container").takeIf { it.isNotBlank() },
        path = optString("Path").takeIf { it.isNotBlank() },
        imageTag = optJSONObject("ImageTags")?.optString("Primary")?.takeIf { it.isNotBlank() },
    )
}

private fun defaultJellyfinHttp(request: Request): String {
    SharedJellyfinHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.bytes() ?: ByteArray(0)
        if (body.size > JellyfinClient.MaxResponseBytes) {
            error("Jellyfin response is too large")
        }
        val text = String(body, StandardCharsets.UTF_8)
        if (!response.isSuccessful) {
            val message = runCatching { JSONObject(text).optString("Message") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "Jellyfin request failed (${response.code})"
            error(message)
        }
        return text
    }
}

private val SharedJellyfinHttpClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

private val JsonMediaType = "application/json; charset=utf-8".toMediaType()

private fun jellyfinAuthFailedMessage(): String = "Jellyfin / Emby authentication failed"

private fun stableJellyfinSourceHash(value: String): String =
    value.hashCode().absoluteValue.toString(36)

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

package app.echo.android.connect

import app.echo.android.model.connect.EchoProtocolVersion
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemotePlaybackState
import app.echo.android.model.connect.EchoRemoteTrack
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal data class EchoLinkStatusResponse(
    val deviceName: String?,
    val playback: EchoRemotePlaybackSnapshot,
)

internal data class EchoLinkTrackPage(
    val tracks: List<EchoRemoteTrack>,
    val totalCount: Int,
)

internal data class EchoLinkStreamResponse(
    val streamUrl: String,
    val track: EchoRemoteTrack?,
)

internal interface EchoLinkTransport {
    suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse
    suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse?
    suspend fun fetchTracks(endpoint: EchoRemoteEndpoint, query: String, pageSize: Int): EchoLinkTrackPage
    suspend fun resolveStream(endpoint: EchoRemoteEndpoint, trackId: String): EchoLinkStreamResponse
    suspend fun fetchLyrics(endpoint: EchoRemoteEndpoint, trackId: String): EchoRemoteLyrics?
}

internal class EchoLinkHttpException(message: String) : IOException(message)

internal class OkHttpEchoLinkTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EchoLinkTransport {
    override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse {
        val json = executeJson(
            Request.Builder()
                .url(endpoint.url("status"))
                .authorized(endpoint)
                .get()
                .build(),
        )
        return json.toStatusResponse(endpoint)
    }

    override suspend fun sendCommand(
        endpoint: EchoRemoteEndpoint,
        command: EchoRemoteCommand,
    ): EchoLinkStatusResponse? {
        val json = executeJson(
            Request.Builder()
                .url(endpoint.url("playback", "command"))
                .authorized(endpoint)
                .post(command.toJson().toString().toRequestBody(JsonMediaType))
                .build(),
        )
        return when {
            json.has("playback") || json.has("state") -> json.toStatusResponse(endpoint)
            else -> null
        }
    }

    override suspend fun fetchTracks(
        endpoint: EchoRemoteEndpoint,
        query: String,
        pageSize: Int,
    ): EchoLinkTrackPage {
        val json = executeJson(
            Request.Builder()
                .url(
                    endpoint.url("library", "tracks") {
                        addQueryParameter("page", "1")
                        addQueryParameter("pageSize", pageSize.coerceIn(1, 500).toString())
                        query.trim().takeIf { it.isNotEmpty() }?.let { addQueryParameter("q", it) }
                    },
                )
                .authorized(endpoint)
                .get()
                .build(),
        )
        val items = json.optJSONArray("tracks") ?: json.optJSONArray("items") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.toRemoteTrack(endpoint)?.let(::add)
            }
        }
        val totalCount = json.optInt("totalCount", json.optInt("total", tracks.size))
        return EchoLinkTrackPage(tracks = tracks, totalCount = totalCount)
    }

    override suspend fun resolveStream(
        endpoint: EchoRemoteEndpoint,
        trackId: String,
    ): EchoLinkStreamResponse {
        val json = executeJson(
            Request.Builder()
                .url(endpoint.url("library", "tracks", trackId, "stream"))
                .authorized(endpoint)
                .post(JSONObject().put("target", "phone").toString().toRequestBody(JsonMediaType))
                .build(),
        )
        val streamUrl = json.optText("streamUrl")
            ?: json.optText("url")
            ?: throw EchoLinkHttpException("PC ECHO did not return a stream URL")
        return EchoLinkStreamResponse(
            streamUrl = streamUrl,
            track = json.optJSONObject("track")?.toRemoteTrack(endpoint),
        )
    }

    override suspend fun fetchLyrics(
        endpoint: EchoRemoteEndpoint,
        trackId: String,
    ): EchoRemoteLyrics? {
        val requests = listOf(
            Request.Builder()
                .url(endpoint.url("library", "tracks", trackId, "lyrics"))
                .authorized(endpoint)
                .get()
                .build(),
            Request.Builder()
                .url(endpoint.url("lyrics", trackId))
                .authorized(endpoint)
                .get()
                .build(),
        )
        requests.forEach { request ->
            runCatching { executeText(request).toRemoteLyrics() }
                .getOrNull()
                ?.takeIf { it.rawText.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private suspend fun executeJson(request: Request): JSONObject =
        withContext(ioDispatcher) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = body.take(180).ifBlank { response.message }
                    throw EchoLinkHttpException("PC ECHO request failed (${response.code}): $detail")
                }
                if (body.isBlank()) JSONObject() else JSONObject(body)
            }
        }

    private suspend fun executeText(request: Request): String =
        withContext(ioDispatcher) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = body.take(180).ifBlank { response.message }
                    throw EchoLinkHttpException("PC ECHO request failed (${response.code}): $detail")
                }
                body
            }
        }

    private fun EchoRemoteEndpoint.url(
        vararg segments: String,
        configure: HttpUrl.Builder.() -> Unit = {},
    ): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme(scheme)
            .host(host)
            .port(port)
        builder.addPathSegment("echo-link")
        builder.addPathSegment("v${protocolVersion.number}")
        segments.forEach(builder::addPathSegment)
        builder.configure()
        return builder.build()
    }

    private fun Request.Builder.authorized(endpoint: EchoRemoteEndpoint): Request.Builder =
        header("Authorization", "Bearer ${endpoint.token}")
            .header("X-ECHO-Link-Version", endpoint.protocolVersion.number.toString())

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

private val EchoProtocolVersion.number: Int
    get() = major.coerceAtLeast(1)

private fun EchoRemoteCommand.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        EchoRemoteCommand.PlayPause -> json.put("command", "playPause")
        EchoRemoteCommand.Next -> json.put("command", "next")
        EchoRemoteCommand.Previous -> json.put("command", "previous")
        EchoRemoteCommand.Stop -> json.put("command", "stop")
        is EchoRemoteCommand.SeekTo -> {
            json.put("command", "seekTo")
            json.put("positionMs", positionMs)
        }
        is EchoRemoteCommand.SetVolume -> {
            json.put("command", "setVolume")
            json.put("volume", volume.coerceIn(0f, 1f))
        }
        is EchoRemoteCommand.PlayTrackOnPc -> {
            json.put("command", "playTrack")
            json.put("trackId", trackId)
            json.put("output", "pc")
        }
    }
    return json
}

private fun JSONObject.toStatusResponse(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse {
    val device = optJSONObject("device")
    val playbackJson = optJSONObject("playback") ?: this
    return EchoLinkStatusResponse(
        deviceName = device?.optText("name") ?: optText("deviceName"),
        playback = playbackJson.toPlaybackSnapshot(endpoint),
    )
}

private fun JSONObject.toPlaybackSnapshot(endpoint: EchoRemoteEndpoint): EchoRemotePlaybackSnapshot =
    EchoRemotePlaybackSnapshot(
        state = optText("state").toPlaybackState(),
        track = optJSONObject("track")?.toRemoteTrack(endpoint),
        positionMs = optLong("positionMs", 0L).coerceAtLeast(0L),
        durationMs = optDurationMs(),
        volume = optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
        outputMode = optText("outputMode") ?: optText("output") ?: "PC ECHO",
        updatedAtEpochMs = optLong("updatedAtEpochMs", System.currentTimeMillis()),
    )

private fun JSONObject.toRemoteTrack(endpoint: EchoRemoteEndpoint): EchoRemoteTrack? {
    val title = optText("title") ?: return null
    return EchoRemoteTrack(
        id = optText("id") ?: optText("trackId"),
        title = title,
        artist = optText("artist") ?: "Unknown Artist",
        album = optText("album"),
        artworkUrl = optArtworkUrl()?.toAbsoluteEchoLinkUrl(endpoint),
        durationMs = optDurationMs(),
        sourceLabel = optText("sourceLabel") ?: optText("source"),
        canPlayOnPhone = optBoolean("canPlayOnPhone", true),
    )
}

private fun JSONObject.optArtworkUrl(): String? =
    optText("artworkUrl")
        ?: optText("coverUrl")
        ?: optText("coverThumb")
        ?: optText("cover")
        ?: optText("albumArtUrl")
        ?: optText("albumArt")
        ?: optText("imageUrl")
        ?: optText("thumbnailUrl")
        ?: optJSONObject("artwork")?.optText("url")
        ?: optJSONObject("cover")?.optText("url")

private fun String.toRemoteLyrics(): EchoRemoteLyrics? {
    val raw = trim().takeIf { it.isNotBlank() } ?: return null
    if (!raw.startsWith("{")) {
        return EchoRemoteLyrics(rawText = raw, sourceLabel = "PC ECHO")
    }
    return runCatching {
        val json = JSONObject(raw)
        val text = json.optText("lyrics")
            ?: json.optText("lyric")
            ?: json.optText("rawText")
            ?: json.optText("text")
            ?: json.optText("syncedLyrics")
            ?: json.optText("plainLyrics")
            ?: json.optJSONObject("lrc")?.optText("lyric")
            ?: json.optJSONObject("yrc")?.optText("lyric")
            ?: json.optJSONObject("tlyric")?.optText("lyric")
        text?.let {
            EchoRemoteLyrics(
                rawText = it,
                sourceLabel = json.optText("sourceLabel")
                    ?: json.optText("source")
                    ?: json.optText("provider")
                    ?: "PC ECHO",
            )
        }
    }.getOrNull()
}

private fun String.toAbsoluteEchoLinkUrl(endpoint: EchoRemoteEndpoint): String {
    val raw = trim()
    if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("content://") || raw.startsWith("file://")) {
        return raw
    }
    if (raw.startsWith("//")) {
        return "${endpoint.scheme}:$raw"
    }
    val base = HttpUrl.Builder()
        .scheme(endpoint.scheme)
        .host(endpoint.host)
        .port(endpoint.port)
        .addPathSegment("")
        .build()
    return base.resolve(raw)?.toString() ?: raw
}

private fun String?.toPlaybackState(): EchoRemotePlaybackState =
    when (this?.lowercase()) {
        "playing" -> EchoRemotePlaybackState.Playing
        "paused" -> EchoRemotePlaybackState.Paused
        "stopped" -> EchoRemotePlaybackState.Stopped
        "loading", "buffering", "seeking" -> EchoRemotePlaybackState.Loading
        "error" -> EchoRemotePlaybackState.Error
        else -> EchoRemotePlaybackState.Idle
    }

private fun JSONObject.optDurationMs(): Long {
    val durationMs = optLong("durationMs", -1L)
    if (durationMs >= 0L) return durationMs
    val durationSeconds = optDouble("durationSeconds", -1.0)
    return if (durationSeconds >= 0.0) (durationSeconds * 1000.0).toLong() else 0L
}

private fun JSONObject.optText(name: String): String? =
    optString(name, "").trim().takeIf { it.isNotEmpty() && it != "null" }

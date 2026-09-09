package app.echo.android.connect

import app.echo.android.model.connect.EchoProtocolVersion
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteAlbum
import app.echo.android.model.connect.EchoRemoteFolder
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemoteMessage
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemotePlaybackState
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
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

internal data class EchoLinkPlaylistPage(
    val playlists: List<EchoRemotePlaylist>,
    val totalCount: Int,
)

internal data class EchoLinkAlbumPage(
    val albums: List<EchoRemoteAlbum>,
    val totalCount: Int,
)

internal data class EchoLinkFolderPage(
    val path: String,
    val folders: List<EchoRemoteFolder>,
    val tracks: List<EchoRemoteTrack>,
)

internal data class EchoLinkStreamResponse(
    val streamUrl: String,
    val track: EchoRemoteTrack?,
)

internal interface EchoLinkTransport {
    suspend fun completePairing(endpoint: EchoRemoteEndpoint): EchoRemoteEndpoint
    suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse
    suspend fun createEventTicket(endpoint: EchoRemoteEndpoint): EchoLinkEventTicket
    fun subscribeEvents(
        endpoint: EchoRemoteEndpoint,
        ticket: EchoLinkEventTicket,
        onEvent: (EchoRemoteMessage) -> Unit,
        onClosed: (Throwable?) -> Unit,
    ): EchoLinkEventSubscription
    suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse?
    suspend fun fetchTracks(endpoint: EchoRemoteEndpoint, query: String, page: Int, pageSize: Int): EchoLinkTrackPage
    suspend fun fetchPlaylists(endpoint: EchoRemoteEndpoint, query: String, page: Int, pageSize: Int): EchoLinkPlaylistPage
    suspend fun fetchPlaylistTracks(endpoint: EchoRemoteEndpoint, playlistId: String, page: Int, pageSize: Int): EchoLinkTrackPage
    suspend fun fetchAlbums(endpoint: EchoRemoteEndpoint, query: String, page: Int, pageSize: Int): EchoLinkAlbumPage
    suspend fun fetchAlbumTracks(endpoint: EchoRemoteEndpoint, albumId: String, page: Int, pageSize: Int): EchoLinkTrackPage
    suspend fun fetchFolders(endpoint: EchoRemoteEndpoint, path: String): EchoLinkFolderPage
    suspend fun resolveStream(endpoint: EchoRemoteEndpoint, trackId: String): EchoLinkStreamResponse
    suspend fun fetchLyrics(endpoint: EchoRemoteEndpoint, trackId: String): EchoRemoteLyrics?
}

internal class EchoLinkHttpException(message: String, val statusCode: Int? = null) : IOException(message)

internal class OkHttpEchoLinkTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EchoLinkTransport {
    override suspend fun completePairing(endpoint: EchoRemoteEndpoint): EchoRemoteEndpoint {
        if (!endpoint.needsV2PairExchange) {
            return endpoint
        }
        val json = executeJson(
            Request.Builder()
                .url(endpoint.versionedUrl(2, "pair"))
                .post(
                    JSONObject()
                        .put("pairingId", endpoint.pairingId)
                        .put("secret", endpoint.pairingSecret)
                        .put("clientName", "ECHO Android")
                        .put("platform", "android")
                        .toString()
                        .toRequestBody(JsonMediaType),
                )
                .build(),
        )
        val accessToken = json.optText("accessToken")
            ?: throw EchoLinkHttpException("PC ECHO did not return an access token")
        return endpoint.copy(
            token = accessToken,
            pairingId = null,
            pairingSecret = null,
            protocolVersion = EchoProtocolVersion.Current,
            supportsV2Events = true,
        )
    }

    override suspend fun createEventTicket(endpoint: EchoRemoteEndpoint): EchoLinkEventTicket {
        val json = executeJson(
            Request.Builder()
                .url(endpoint.versionedUrl(2, "events", "ticket"))
                .authorized(endpoint)
                .post(JSONObject().toString().toRequestBody(JsonMediaType))
                .build(),
        )
        val ticket = json.optText("ticket")
            ?: throw EchoLinkHttpException("PC ECHO did not return an event ticket")
        val eventsUrl = endpoint.resolveEventsUrl(json.optText("eventsUrl"))
            ?: endpoint.versionedUrl(2, "events") {
                addQueryParameter("ticket", ticket)
            }
        return EchoLinkEventTicket(ticket = ticket, eventsUrl = eventsUrl)
    }

    override fun subscribeEvents(
        endpoint: EchoRemoteEndpoint,
        ticket: EchoLinkEventTicket,
        onEvent: (EchoRemoteMessage) -> Unit,
        onClosed: (Throwable?) -> Unit,
    ): EchoLinkEventSubscription {
        val request = Request.Builder()
            .url(ticket.eventsUrl)
            .header("Accept", "text/event-stream")
            .authorized(endpoint)
            .get()
            .build()
        val source = EventSources.createFactory(client).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    parseEchoLinkEventData(data, endpoint)?.let(onEvent)
                }

                override fun onClosed(eventSource: EventSource) {
                    onClosed(null)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val error = t ?: response?.let {
                        EchoLinkHttpException("PC ECHO events failed (${it.code})", it.code)
                    }
                    onClosed(error)
                }
            },
        )
        return EchoLinkEventSubscription { source.cancel() }
    }

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
        page: Int,
        pageSize: Int,
    ): EchoLinkTrackPage {
        val json = executeJson(
            Request.Builder()
                .url(echoLinkLibraryTracksUrl(endpoint, query, page, pageSize))
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

    override suspend fun fetchPlaylists(
        endpoint: EchoRemoteEndpoint,
        query: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkPlaylistPage {
        val json = executeJson(
            Request.Builder()
                .url(
                    endpoint.url("library", "playlists") {
                        addQueryParameter("page", page.toString())
                        addQueryParameter("pageSize", pageSize.coerceIn(1, 500).toString())
                        query.trim().takeIf { it.isNotEmpty() }?.let { addQueryParameter("q", it) }
                    },
                )
                .authorized(endpoint)
                .get()
                .build(),
        )
        val items = json.optJSONArray("playlists") ?: json.optJSONArray("items") ?: JSONArray()
        val playlists = buildList {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.toRemotePlaylist(endpoint)?.let(::add)
            }
        }
        return EchoLinkPlaylistPage(
            playlists = playlists,
            totalCount = json.optInt("totalCount", json.optInt("total", playlists.size)),
        )
    }

    override suspend fun fetchPlaylistTracks(
        endpoint: EchoRemoteEndpoint,
        playlistId: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkTrackPage {
        val json = executeJson(
            Request.Builder()
                .url(echoLinkPlaylistTracksUrl(endpoint, playlistId, pageSize, page))
                .authorized(endpoint)
                .get()
                .build(),
        )
        return json.toTrackPage(endpoint)
    }

    override suspend fun fetchAlbums(
        endpoint: EchoRemoteEndpoint,
        query: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkAlbumPage {
        val json = executeJson(
            Request.Builder()
                .url(echoLinkLibraryAlbumsUrl(endpoint, query, page, pageSize))
                .authorized(endpoint)
                .get()
                .build(),
        )
        val items = json.optJSONArray("albums") ?: json.optJSONArray("items") ?: JSONArray()
        val albums = buildList {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.toRemoteAlbum(endpoint)?.let(::add)
            }
        }
        return EchoLinkAlbumPage(
            albums = albums,
            totalCount = json.optInt("totalCount", json.optInt("total", albums.size)),
        )
    }

    override suspend fun fetchAlbumTracks(
        endpoint: EchoRemoteEndpoint,
        albumId: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkTrackPage {
        val json = executeJson(
            Request.Builder()
                .url(echoLinkAlbumTracksUrl(endpoint, albumId, page, pageSize))
                .authorized(endpoint)
                .get()
                .build(),
        )
        return json.toTrackPage(endpoint)
    }

    override suspend fun fetchFolders(
        endpoint: EchoRemoteEndpoint,
        path: String,
    ): EchoLinkFolderPage {
        val json = executeJson(
            Request.Builder()
                .url(echoLinkFoldersUrl(endpoint, path))
                .authorized(endpoint)
                .get()
                .build(),
        )
        return json.toFolderPage(endpoint, path)
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
                .getOrElse { if (it is CancellationException) throw it else null }
                ?.takeIf { it.rawText.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private suspend fun executeJson(request: Request): JSONObject = withContext(ioDispatcher) {
        val body = executeText(request)
        if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    private suspend fun executeText(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            throw EchoLinkHttpException("PC ECHO request failed (${it.code}): ${body.take(180).ifBlank { it.message }}", it.code)
                        }
                        body
                    }
                }
                result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    private fun EchoRemoteEndpoint.url(
        vararg segments: String,
        configure: HttpUrl.Builder.() -> Unit = {},
    ): HttpUrl = versionedUrl(protocolVersion.number, *segments, configure = configure)

    private fun EchoRemoteEndpoint.versionedUrl(
        version: Int,
        vararg segments: String,
        configure: HttpUrl.Builder.() -> Unit = {},
    ): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme(scheme)
            .host(host)
            .port(port)
        builder.addPathSegment("echo-link")
        builder.addPathSegment("v${version.coerceAtLeast(1)}")
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

private fun JSONObject.toStatusResponse(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse {
    val device = optJSONObject("device")
    val playbackJson = optJSONObject("playback") ?: this
    return EchoLinkStatusResponse(
        deviceName = device?.optText("name") ?: optText("deviceName"),
        playback = playbackJson.toPlaybackSnapshot(endpoint),
    )
}

internal fun JSONObject.toRemoteTrack(endpoint: EchoRemoteEndpoint): EchoRemoteTrack? {
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

private fun JSONObject.toRemoteAlbum(endpoint: EchoRemoteEndpoint): EchoRemoteAlbum? {
    val id = optText("id") ?: optText("albumId") ?: optText("key") ?: return null
    val title = optText("title") ?: optText("name") ?: optText("album") ?: return null
    val tracksArray = optJSONArray("tracks") ?: optJSONArray("items")
    val tracks = buildList {
        if (tracksArray != null) {
            for (index in 0 until tracksArray.length()) {
                tracksArray.optJSONObject(index)?.toRemoteTrack(endpoint)?.let(::add)
            }
        }
    }
    val artist = optText("albumArtist") ?: optText("artist") ?: tracks.firstOrNull()?.artist.orEmpty()
    return EchoRemoteAlbum(
        id = id,
        title = title,
        artist = artist.ifBlank { "Unknown Artist" },
        albumArtist = optText("albumArtist") ?: optText("artist"),
        artworkUrl = optArtworkUrl()?.toAbsoluteEchoLinkUrl(endpoint)
            ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
        trackCount = optInt("trackCount", optInt("songCount", tracks.size)).coerceAtLeast(tracks.size),
        durationMs = optDurationMs().takeIf { it > 0L } ?: tracks.sumOf { it.durationMs.coerceAtLeast(0L) },
        year = optInt("year", 0).takeIf { it > 0 },
        tracks = tracks,
    )
}

private fun JSONObject.toRemoteFolder(endpoint: EchoRemoteEndpoint): EchoRemoteFolder? {
    val path = optText("path") ?: optText("id") ?: optText("folder") ?: return null
    val name = optText("name") ?: optText("title") ?: path.substringAfterLast('/').ifBlank { path }
    return EchoRemoteFolder(
        path = path,
        name = name,
        trackCount = optInt("trackCount", optInt("songCount", 0)),
        childFolderCount = optInt("childFolderCount", optInt("folderCount", optInt("childCount", 0))),
        artworkUrl = optArtworkUrl()?.toAbsoluteEchoLinkUrl(endpoint),
    )
}

private fun JSONObject.toFolderPage(endpoint: EchoRemoteEndpoint, requestedPath: String): EchoLinkFolderPage {
    val folderItems = optJSONArray("folders") ?: optJSONArray("children")
    val folders = buildList {
        if (folderItems != null) {
            for (index in 0 until folderItems.length()) {
                val child = folderItems.optJSONObject(index) ?: continue
                val type = child.optText("type")?.lowercase()
                if (type != null && type != "folder" && type != "directory") continue
                child.toRemoteFolder(endpoint)?.let(::add)
            }
        }
    }
    val trackItems = optJSONArray("tracks") ?: optJSONArray("files")
    val tracks = buildList {
        if (trackItems != null) {
            for (index in 0 until trackItems.length()) {
                trackItems.optJSONObject(index)?.toRemoteTrack(endpoint)?.let(::add)
            }
        }
        val mixed = optJSONArray("items")
        if (mixed != null && trackItems == null) {
            for (index in 0 until mixed.length()) {
                val item = mixed.optJSONObject(index) ?: continue
                val type = item.optText("type")?.lowercase()
                if (type == "folder" || type == "directory") {
                    continue
                }
                item.toRemoteTrack(endpoint)?.let(::add)
            }
        }
    }
    val mixedFolders = optJSONArray("items")
    val extraFolders = buildList {
        if (folderItems == null && mixedFolders != null) {
            for (index in 0 until mixedFolders.length()) {
                val item = mixedFolders.optJSONObject(index) ?: continue
                val type = item.optText("type")?.lowercase()
                if (type == "folder" || type == "directory") {
                    item.toRemoteFolder(endpoint)?.let(::add)
                }
            }
        }
    }
    return EchoLinkFolderPage(
        path = optText("path") ?: requestedPath,
        folders = folders.ifEmpty { extraFolders },
        tracks = tracks,
    )
}

private fun JSONObject.toRemotePlaylist(endpoint: EchoRemoteEndpoint): EchoRemotePlaylist? {
    val id = optText("id") ?: optText("playlistId") ?: optText("key") ?: return null
    val name = optText("name") ?: optText("title") ?: "PC ECHO Playlist"
    val tracksArray = optJSONArray("tracks") ?: optJSONArray("items")
    val tracks = buildList {
        if (tracksArray != null) {
            for (index in 0 until tracksArray.length()) {
                tracksArray.optJSONObject(index)?.toRemoteTrack(endpoint)?.let(::add)
            }
        }
    }
    return EchoRemotePlaylist(
        id = id,
        name = name,
        artworkUrl = optArtworkUrl()?.toAbsoluteEchoLinkUrl(endpoint)
            ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
        trackCount = optInt("trackCount", optInt("songCount", tracks.size)).coerceAtLeast(tracks.size),
        sourceLabel = optText("sourceLabel") ?: optText("source") ?: optText("provider"),
        tracks = tracks,
    )
}

private fun JSONObject.toTrackPage(endpoint: EchoRemoteEndpoint): EchoLinkTrackPage {
    val items = optJSONArray("tracks") ?: optJSONArray("items") ?: JSONArray()
    val tracks = buildList {
        for (index in 0 until items.length()) {
            items.optJSONObject(index)?.toRemoteTrack(endpoint)?.let(::add)
        }
    }
    val totalCount = optInt("totalCount", optInt("total", tracks.size))
    return EchoLinkTrackPage(tracks = tracks, totalCount = totalCount)
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

internal fun echoLinkLyricsRawText(json: JSONObject): String? {
    val synced = json.optText("syncedLyrics")
        ?: json.optJSONObject("yrc")?.optText("lyric")
        ?: json.optJSONObject("lrc")?.optText("lyric")
    if (!synced.isNullOrBlank()) return synced
    return json.optText("lyrics")
        ?: json.optText("lyric")
        ?: json.optText("rawText")
        ?: json.optText("text")
        ?: json.optText("plainLyrics")
        ?: json.optJSONObject("tlyric")?.optText("lyric")
}

private fun String.toRemoteLyrics(): EchoRemoteLyrics? {
    val raw = trim().takeIf { it.isNotBlank() } ?: return null
    if (!raw.startsWith("{")) {
        return EchoRemoteLyrics(rawText = raw, sourceLabel = "PC ECHO")
    }
    return runCatching {
        val json = JSONObject(raw)
        val text = echoLinkLyricsRawText(json)
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

internal fun String?.toPlaybackState(): EchoRemotePlaybackState =
    when (this?.lowercase()) {
        "playing" -> EchoRemotePlaybackState.Playing
        "paused" -> EchoRemotePlaybackState.Paused
        "stopped" -> EchoRemotePlaybackState.Stopped
        "loading", "buffering", "seeking" -> EchoRemotePlaybackState.Loading
        "error" -> EchoRemotePlaybackState.Error
        else -> EchoRemotePlaybackState.Idle
    }

internal fun JSONObject.optDurationMs(): Long {
    val durationMs = optLong("durationMs", -1L)
    if (durationMs >= 0L) return durationMs
    val durationSeconds = optDouble("durationSeconds", -1.0)
    return if (durationSeconds >= 0.0) (durationSeconds * 1000.0).toLong() else 0L
}

internal fun JSONObject.optText(name: String): String? =
    optString(name, "").trim().takeIf { it.isNotEmpty() && it != "null" }

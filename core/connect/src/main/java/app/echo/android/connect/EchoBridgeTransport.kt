package app.echo.android.connect

import app.echo.android.model.connect.*
import app.echo.android.model.library.FolderSortMode
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Read-only peer protocol. Playback remains owned by the existing phone player. */
internal class EchoBridgeTransport(private val execute: suspend (Request) -> JSONObject) {
    suspend fun device(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse {
        val json = get(endpoint, "device")
        val capabilities = json.optJSONObject("capabilities")
        if (json.optInt("version") != 1 || capabilities == null || !capabilities.optBoolean("library") ||
            !capabilities.has("playbackControl") || capabilities.optBoolean("playbackControl", true)) {
            throw EchoLinkHttpException("unsupported_bridge_capabilities", 422)
        }
        return EchoLinkStatusResponse(json.getJSONObject("device").optText("name"), null, libraryOnly = true)
    }

    suspend fun tracks(endpoint: EchoRemoteEndpoint, query: String, page: Int,
        parentKind: String? = null, parentId: String? = null): EchoLinkTrackPage {
        val catalog = catalog(endpoint, "songs", query, page, parentKind, parentId)
        val rows = entries(catalog).mapNotNull { it.toRemoteTrack(endpoint)?.copy(
            canPlayOnPhone = it.bridgeCanPlayOnPhone(), sourceLabel = "ECHO Bridge",
            artworkUrl = EchoBridgeArtwork.url(endpoint, it.optText("coverId"))) }
        return EchoLinkTrackPage(rows, catalog.optInt("total", rows.size))
    }

    suspend fun albums(endpoint: EchoRemoteEndpoint, query: String, page: Int): EchoLinkAlbumPage {
        val catalog = catalog(endpoint, "albums", query, page)
        val rows = entries(catalog).map { EchoRemoteAlbum(id = it.getString("id"),
            title = it.optText("title").orEmpty(), artist = it.optText("subtitle").orEmpty(),
            trackCount = it.optInt("count").coerceAtLeast(0),
            artworkUrl = EchoBridgeArtwork.url(endpoint, it.optText("coverId"))) }
        return EchoLinkAlbumPage(rows, catalog.optInt("total", rows.size))
    }

    suspend fun playlists(endpoint: EchoRemoteEndpoint, query: String, page: Int): EchoLinkPlaylistPage {
        val catalog = catalog(endpoint, "playlists", query, page)
        val rows = entries(catalog).map { EchoRemotePlaylist(id = it.getString("id"),
            name = it.optText("title").orEmpty(), trackCount = it.optInt("count").coerceAtLeast(0),
            sourceLabel = "ECHO Bridge", artworkUrl = EchoBridgeArtwork.url(endpoint, it.optText("coverId"))) }
        return EchoLinkPlaylistPage(rows, catalog.optInt("total", rows.size))
    }

    suspend fun folders(endpoint: EchoRemoteEndpoint, path: String, page: Int = 1, query: String = "",
        sort: FolderSortMode = FolderSortMode.Path): EchoLinkFolderPage {
        val catalog = catalog(endpoint, "folders", query, page,
            if (path.isBlank()) null else "folder", path.takeIf(String::isNotBlank), when (sort) {
                FolderSortMode.Path -> "titleAsc"
                FolderSortMode.TrackCount -> "trackCountDesc"
                FolderSortMode.AlbumCount -> "albumCountDesc"
                FolderSortMode.Duration -> "durationDesc"
                FolderSortMode.Size -> "sizeDesc"
                FolderSortMode.RecentlyModified -> "fileModifiedDesc"
            })
        val rows = entries(catalog).map { EchoRemoteFolder(path = it.getString("id"),
            name = it.optText("title").orEmpty(), trackCount = it.optInt("count").coerceAtLeast(0),
            artworkUrl = EchoBridgeArtwork.url(endpoint, it.optText("coverId"))) }
        val songs = if (path.isBlank()) EchoLinkTrackPage(emptyList(), 0)
            else tracks(endpoint, query, page, "folder", path)
        return EchoLinkFolderPage(path, rows, songs.tracks, page = page, pageSize = 50,
            folderTotalCount = catalog.optInt("total", rows.size).coerceAtLeast(0), trackTotalCount = songs.totalCount)
    }

    suspend fun stream(endpoint: EchoRemoteEndpoint, id: String): EchoLinkStreamResponse {
        val track = get(endpoint, "stream") { addQueryParameter("id", id); addQueryParameter("sourceRange", "1") }.getJSONObject("track")
        if (!track.bridgeCanPlayOnPhone()) throw EchoLinkHttpException("unsupported_format", 415)
        val url = track.getString("streamUrl").toHttpUrlOrNull()
        if (url == null || url.scheme != endpoint.scheme || url.host != endpoint.host || url.port != endpoint.port ||
            !url.encodedPath.startsWith("/echo-link/media/") || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw EchoLinkHttpException("invalid_bridge_stream")
        }
        return EchoLinkStreamResponse(url.toString(), track.toRemoteTrack(endpoint), sourceRange = track.sourceRange())
    }

    suspend fun lyrics(endpoint: EchoRemoteEndpoint, id: String): EchoRemoteLyrics? {
        val json = get(endpoint, "lyrics") { addQueryParameter("id", id) }.optJSONObject("lyrics") ?: return null
        if (json.optString("kind") in listOf("empty", "instrumental")) return null
        val lines = json.optJSONArray("lines")
        if (lines != null && lines.length() > 0) {
            require(lines.length() <= 4000) { "Bridge returned too many lyric lines" }
            return EchoRemoteLyrics(rawText = json.toString(), sourceLabel = "ECHO Bridge")
        }
        val text = json.optText("syncedText") ?: json.optText("plainText") ?: return null
        return EchoRemoteLyrics(rawText = text, sourceLabel = "ECHO Bridge")
    }

    private suspend fun catalog(endpoint: EchoRemoteEndpoint, kind: String, query: String, page: Int,
        parentKind: String? = null, parentId: String? = null, sort: String? = null): JSONObject = get(endpoint, "catalog") {
        addQueryParameter("kind", kind); addQueryParameter("page", page.coerceAtLeast(1).toString())
        query.trim().takeIf(String::isNotBlank)?.let { addQueryParameter("q", it.take(256)) }
        parentKind?.let { addQueryParameter("parentKind", it); addQueryParameter("parentId", parentId) }
        sort?.let { addQueryParameter("sort", it) }
    }.getJSONObject("catalog").also {
        if (it.optInt("pageSize") != 50 || it.optInt("page") != page || it.getJSONArray("entries").length() > 50)
            throw EchoLinkHttpException("invalid_bridge_catalog")
    }

    private fun entries(catalog: JSONObject): List<JSONObject> {
        val rows = catalog.getJSONArray("entries")
        return List(rows.length()) { rows.getJSONObject(it) }
    }

    private suspend fun get(endpoint: EchoRemoteEndpoint, route: String,
        query: HttpUrl.Builder.() -> Unit = {}): JSONObject {
        check(endpoint.token.isBlank() && !endpoint.needsV2PairExchange)
        val url = HttpUrl.Builder().scheme(endpoint.scheme).host(endpoint.host).port(endpoint.port)
            .addPathSegments("echo-link/peer/v1").addPathSegment(route).apply(query).build()
        return ReadSlots.withPermit {
            execute(Request.Builder().url(url).header("X-ECHO-Link-Direct", "1")
                .header("X-ECHO-Link-Version", "1").get().build())
        }
    }

    internal companion object {
        // Bridge allows four active requests; leave room for original-media reads and seek.
        val ReadSlots = Semaphore(2)
    }
}

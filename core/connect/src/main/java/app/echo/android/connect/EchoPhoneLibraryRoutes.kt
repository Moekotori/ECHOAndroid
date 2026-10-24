package app.echo.android.connect

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import app.echo.android.model.connect.EchoPhoneCatalogQuery
import app.echo.android.model.connect.EchoPhoneCatalogPage

data class EchoPhoneLibraryPage(val tracks: List<EchoLinkCastPublication>, val totalCount: Int)

/** Queries execute on the bounded HTTP workers, never on a Compose or audio thread. */
class EchoPhoneLibraryRoutes(
    private val token: String,
    private val baseUrl: String,
    private val page: (String, Int, Int) -> EchoPhoneLibraryPage,
    private val track: (String) -> EchoLinkCastPublication?,
    private val catalog: ((EchoPhoneCatalogQuery) -> EchoPhoneCatalogPage)? = null,
    private val lyrics: ((String) -> EchoPhoneLyrics?)? = null,
) {
    fun metadata(path: String, headers: Map<String, String>): EchoLinkHttpReply? {
        val route = path.substringBefore('?')
        if (route !in listOf("/echo-link/phone/v1/library/tracks", "/echo-link/phone/v1/library/track", "/echo-link/phone/v1/library/catalog", "/echo-link/phone/v1/library/lyrics")) return null
        if (headers["authorization"] != "Bearer $token" || headers.containsKey("origin")) {
            return EchoLinkHttpReply(401, "{\"error\":\"unauthorized\"}")
        }
        return try {
            val params = URI(path).rawQuery.orEmpty().split('&').associate { entry ->
                decode(entry.substringBefore('=')) to decode(entry.substringAfter('=', ""))
            }
            if (route.endsWith("/lyrics")) {
                val read = lyrics ?: return EchoLinkHttpReply(404, "{\"error\":\"lyrics_unavailable\"}")
                val id = params["id"].orEmpty()
                require(id.isNotBlank() && id.length <= 1024)
                return phoneLyricsReply(read(id))
            }
            if (route.endsWith("/catalog")) {
                val read = catalog ?: return EchoLinkHttpReply(404, "{\"error\":\"catalog_unavailable\"}")
                val result = read(EchoPhoneCatalogQuery(params["kind"].orEmpty(), params["page"]?.toIntOrNull() ?: 1,
                    params["q"].orEmpty(), params["parentKind"], params["parentId"]))
                val entries = JSONArray()
                require(result.entries.size <= 50)
                result.entries.forEach { row -> entries.put(JSONObject().put("kind", row.kind).put("id", row.id)
                    .put("title", row.title).put("subtitle", row.subtitle).put("count", row.count)
                    .put("coverId", row.coverId ?: JSONObject.NULL).putOpt("artist", row.artist).putOpt("album", row.album)
                    .putOpt("durationMs", row.durationMs).putOpt("canPlay", row.canPlay).putOpt("sampleRate", row.sampleRate)) }
                return EchoLinkHttpReply(body = JSONObject().put("catalog", JSONObject().put("entries", entries)
                    .put("total", result.total).put("page", result.page).put("pageSize", 50).put("supportsScopedSearch", true)
                    .apply { if (result.parentKind != null && result.parentId != null) put("parent", JSONObject()
                        .put("kind", result.parentKind).put("id", result.parentId)) }).toString())
            }
            if (route.endsWith("/track")) {
                val item = track(params["id"].orEmpty()) ?: return EchoLinkHttpReply(404, "{\"error\":\"track_not_found\"}")
                if (item.sourceRange != null && params["sourceRange"] != "1")
                    return EchoLinkHttpReply(415, "{\"error\":\"source_range_not_supported\"}")
                return EchoLinkHttpReply(body = JSONObject().put("track", json(item)).toString())
            }
            val pageNumber = params["page"]?.toIntOrNull()?.coerceIn(1, 100_000) ?: 1
            val size = params["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            val result = page(params["q"].orEmpty().take(256), (pageNumber - 1) * size, size)
            val rows = JSONArray()
            result.tracks.forEach { item -> rows.put(json(item)) }
            EchoLinkHttpReply(body = JSONObject().put("version", 1).put("tracks", rows)
                .put("totalCount", result.totalCount).toString())
        } catch (_: IllegalArgumentException) {
            EchoLinkHttpReply(400, "{\"error\":\"invalid_library_query\"}")
        } catch (_: java.net.URISyntaxException) {
            // Malformed query strings (unencoded |, {, < …) are client errors, not server ones.
            EchoLinkHttpReply(400, "{\"error\":\"invalid_library_query\"}")
        } catch (_: Exception) {
            EchoLinkHttpReply(500, "{\"error\":\"library_unavailable\"}")
        }
    }

    fun publication(path: String): Pair<EchoLinkCastPublication, Boolean>? {
        val parts = path.substringBefore('?').split('/')
        if (parts.size != 5 || parts[1] != "echo-link" || parts[2] !in listOf("phone-stream", "phone-art") || parts[3] != token) return null
        val id = runCatching { String(Base64.getUrlDecoder().decode(parts[4]), Charsets.UTF_8) }.getOrNull() ?: return null
        return track(id)?.let { it to (parts[2] == "phone-art") }
    }

    private fun json(item: EchoLinkCastPublication): JSONObject {
        val id = Base64.getUrlEncoder().withoutPadding().encodeToString(item.trackId.toByteArray(Charsets.UTF_8))
        return JSONObject().put("id", item.trackId).put("title", item.title).put("artist", item.artist)
            .put("album", item.album.orEmpty()).put("durationMs", item.durationMs)
            .put("streamUrl", "$baseUrl/echo-link/phone-stream/$token/$id")
            .putOpt("sourceRange", item.sourceRange?.toJson())
            .putOpt("artworkUrl", item.artworkUrl?.let { "$baseUrl/echo-link/phone-art/$token/$id" })
            .putOpt("audio", item.format?.let { format -> JSONObject().putOpt("mimeType", format.mimeType)
                .putOpt("sampleRateHz", format.sampleRateHz).putOpt("codec", format.codec) })
    }

    private fun decode(value: String) = URLDecoder.decode(value, Charsets.UTF_8.name())
}

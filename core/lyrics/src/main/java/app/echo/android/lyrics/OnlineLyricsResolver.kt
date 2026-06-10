package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

data class EchoLyricsSearchRequest(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
)

class OnlineLyricsResolver(
    private val httpGet: (String, Map<String, String>) -> String? = ::defaultHttpGet,
) {
    fun loadForTrack(request: EchoLyricsSearchRequest): EchoLyrics? {
        if (request.title.isBlank() || request.artist.isBlank()) return null
        return loadFromNetease(request)
            ?: loadFromLrclib(request)
    }

    fun loadFromNeteaseSongId(songId: Long): EchoLyrics? {
        if (songId <= 0L) return null
        val lyricsUrl = buildUrl(
            base = "https://music.163.com/api/song/lyric",
            params = listOf(
                "id" to songId.toString(),
                "lv" to "-1",
                "kv" to "-1",
                "tv" to "-1",
            ),
        )
        val lyricsJson = httpGet(lyricsUrl, NeteaseHeaders) ?: return null
        val rawLyrics = runCatching {
            val root = JSONObject(lyricsJson)
            root.optJSONObject("lrc")?.optLyricsText("lyric")
                ?: root.optJSONObject("yrc")?.optLyricsText("lyric")
                ?: root.optJSONObject("tlyric")?.optLyricsText("lyric")
        }.getOrNull() ?: return null

        return parseOnlineLyrics(rawLyrics, sourceLabel = "NetEase Cloud Music")
    }

    private fun loadFromNetease(request: EchoLyricsSearchRequest): EchoLyrics? {
        val query = "${request.title} ${request.artist}".trim()
        val searchUrl = buildUrl(
            base = "https://music.163.com/api/search/get/web",
            params = listOf(
                "s" to query,
                "type" to "1",
                "limit" to "5",
                "offset" to "0",
            ),
        )
        val searchJson = httpGet(searchUrl, NeteaseHeaders) ?: return null
        val songs = runCatching {
            JSONObject(searchJson)
                .optJSONObject("result")
                ?.optJSONArray("songs")
        }.getOrNull() ?: return null

        val song = songs.objects()
            .mapNotNull { candidate ->
                val id = candidate.optLong("id").takeIf { it > 0L } ?: return@mapNotNull null
                ScoredNeteaseSong(id, scoreNeteaseSong(request, candidate))
            }
            .filter { it.score >= MinimumNeteaseScore }
            .maxByOrNull { it.score }
            ?: return null

        val lyricsUrl = buildUrl(
            base = "https://music.163.com/api/song/lyric",
            params = listOf(
                "id" to song.id.toString(),
                "lv" to "-1",
                "kv" to "-1",
                "tv" to "-1",
            ),
        )
        val lyricsJson = httpGet(lyricsUrl, NeteaseHeaders) ?: return null
        val rawLyrics = runCatching {
            val root = JSONObject(lyricsJson)
            root.optJSONObject("lrc")?.optLyricsText("lyric")
                ?: root.optJSONObject("yrc")?.optLyricsText("lyric")
                ?: root.optJSONObject("tlyric")?.optLyricsText("lyric")
        }.getOrNull() ?: return null

        return parseOnlineLyrics(rawLyrics, sourceLabel = "NetEase Cloud Music")
    }

    private fun loadFromLrclib(request: EchoLyricsSearchRequest): EchoLyrics? {
        val params = buildList {
            add("track_name" to request.title)
            add("artist_name" to request.artist)
            request.album?.takeIf { it.isNotBlank() }?.let { add("album_name" to it) }
            request.durationMs.takeIf { it > 0L }?.let { add("duration" to (it / 1000L).toString()) }
        }
        val searchUrl = buildUrl(base = "https://lrclib.net/api/search", params = params)
        val searchJson = httpGet(searchUrl, LrclibHeaders) ?: return null
        val records = runCatching { JSONArray(searchJson) }.getOrNull() ?: return null

        val record = records.objects()
            .map { candidate -> candidate to scoreLrclibRecord(request, candidate) }
            .filter { (_, score) -> score >= MinimumLrclibScore }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?: return null

        val rawLyrics = record.optLyricsText("syncedLyrics")
            ?: record.optLyricsText("plainLyrics")
            ?: return null

        return parseOnlineLyrics(rawLyrics, sourceLabel = "LRCLIB")
    }

    private fun parseOnlineLyrics(rawLyrics: String, sourceLabel: String): EchoLyrics? =
        runCatching { EchoLyricsParser.parse(rawLyrics, sourceLabel = sourceLabel) }
            .getOrNull()
            ?.takeIf { it.lines.isNotEmpty() }
            ?.let { lyrics ->
                lyrics.copy(metadata = lyrics.metadata + ("provider" to sourceLabel))
            }

    private fun scoreNeteaseSong(request: EchoLyricsSearchRequest, song: JSONObject): Int {
        val title = song.optString("name")
        val album = song.optJSONObject("album")?.optString("name").orEmpty()
        val artists = song.optJSONArray("artists")
            ?.objects()
            ?.joinToString(" ") { it.optString("name") }
            .orEmpty()
        val durationMs = song.optLong("duration", 0L)
        return scoreCandidate(
            request = request,
            candidateTitle = title,
            candidateArtist = artists,
            candidateAlbum = album,
            candidateDurationMs = durationMs,
        )
    }

    private fun scoreLrclibRecord(request: EchoLyricsSearchRequest, record: JSONObject): Int {
        val durationMs = (record.optDouble("duration", 0.0) * 1000.0).toLong()
        return scoreCandidate(
            request = request,
            candidateTitle = record.optString("trackName", record.optString("name")),
            candidateArtist = record.optString("artistName"),
            candidateAlbum = record.optString("albumName"),
            candidateDurationMs = durationMs,
        )
    }

    private fun scoreCandidate(
        request: EchoLyricsSearchRequest,
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String,
        candidateDurationMs: Long,
    ): Int {
        val targetTitle = request.title.normalizedKey()
        val targetArtist = request.artist.normalizedKey()
        val targetAlbum = request.album.orEmpty().normalizedKey()
        val title = candidateTitle.normalizedKey()
        val artist = candidateArtist.normalizedKey()
        val album = candidateAlbum.normalizedKey()
        var score = 0

        score += when {
            title == targetTitle -> 60
            title.contains(targetTitle) || targetTitle.contains(title) -> 28
            else -> 0
        }
        score += when {
            artist == targetArtist -> 28
            artist.contains(targetArtist) || targetArtist.contains(artist) -> 18
            else -> 0
        }
        if (targetAlbum.isNotBlank()) {
            score += when {
                album == targetAlbum -> 12
                album.contains(targetAlbum) || targetAlbum.contains(album) -> 6
                else -> 0
            }
        }
        if (request.durationMs > 0L && candidateDurationMs > 0L) {
            val delta = abs(request.durationMs - candidateDurationMs)
            score += when {
                delta <= 3_000L -> 24
                delta <= 8_000L -> 12
                else -> -14
            }
        }
        return score
    }

    private data class ScoredNeteaseSong(
        val id: Long,
        val score: Int,
    )

    private companion object {
        const val MinimumNeteaseScore = 58
        const val MinimumLrclibScore = 58

        val NeteaseHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0",
            "Referer" to "https://music.163.com/",
        )
        val LrclibHeaders = mapOf(
            "User-Agent" to "ECHOAndroid/0.1",
        )
    }
}

private fun defaultHttpGet(url: String, headers: Map<String, String>): String? {
    val connection = (URI(url).toURL().openConnection() as? HttpURLConnection) ?: return null
    return runCatching {
        connection.connectTimeout = 2_500
        connection.readTimeout = 2_500
        connection.instanceFollowRedirects = true
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (connection.responseCode in 200..299) {
            connection.inputStream.use { it.readUtf8Limited(maxBytes = 1_500_000) }
        } else {
            null
        }
    }.getOrNull().also {
        connection.disconnect()
    }
}

private fun InputStream.readUtf8Limited(maxBytes: Int): String? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

private fun buildUrl(base: String, params: List<Pair<String, String>>): String =
    params.joinToString(separator = "&", prefix = "$base?") { (name, value) ->
        "${name.urlEncode()}=${value.urlEncode()}"
    }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8)

private fun String.normalizedKey(): String =
    lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), "")
        .trim()

private fun JSONArray.objects(): Sequence<JSONObject> =
    sequence {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { yield(it) }
        }
    }

private fun JSONObject.optLyricsText(name: String): String? =
    optString(name)
        .takeIf { it.isNotBlank() && it != "null" }

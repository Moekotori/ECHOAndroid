package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricsCandidate
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
    fun loadForTrack(request: EchoLyricsSearchRequest): EchoLyrics? =
        neteaseCandidates(request, limit = 1).firstOrNull()?.lyrics
            ?: lrclibCandidates(request, limit = 1).firstOrNull()?.lyrics

    fun search(request: EchoLyricsSearchRequest): List<EchoLyricsCandidate> {
        if (request.title.isBlank() || request.artist.isBlank()) return emptyList()
        return neteaseCandidates(request, 5) + lrclibCandidates(request, 5)
    }

    fun loadFromNeteaseSongId(songId: Long): EchoLyrics? {
        if (songId <= 0L) return null
        val url = buildUrl("https://music.163.com/api/song/lyric", listOf(
            "id" to songId.toString(), "lv" to "-1", "kv" to "-1", "tv" to "-1",
            "yv" to "-1", "rv" to "-1", "yrv" to "-1", "ytv" to "-1",
        ))
        val response = httpGet(url, NeteaseHeaders) ?: return null
        val root = runCatching { JSONObject(response) }.getOrNull() ?: return null
        fun lyrics(key: String): EchoLyrics? = root.optJSONObject(key)?.optLyricsText("lyric")
            ?.let { parseOnlineLyrics(it, "NetEase Cloud Music") }
        val primary = lyrics("yrc") ?: lyrics("lrc") ?: return null
        val translated = lyrics("ytlrc") ?: lyrics("tlyric")
        val romanized = lyrics("yromalrc") ?: lyrics("romalrc")
        return primary.copy(lines = primary.lines.map { line ->
            fun matching(aux: EchoLyrics?): String? = aux?.lines
                ?.minByOrNull { abs(it.startMs - line.startMs) }
                ?.takeIf { abs(it.startMs - line.startMs) <= 500L }?.text?.takeIf(String::isNotBlank)
            line.copy(translation = matching(translated), romanization = matching(romanized))
        })
    }

    private fun neteaseCandidates(request: EchoLyricsSearchRequest, limit: Int): List<EchoLyricsCandidate> {
        if (request.title.isBlank() || request.artist.isBlank()) return emptyList()
        val url = buildUrl("https://music.163.com/api/search/get/web", listOf(
            "s" to "${request.title} ${request.artist}", "type" to "1", "limit" to "5", "offset" to "0",
        ))
        val response = httpGet(url, NeteaseHeaders) ?: return emptyList()
        val songs = runCatching { JSONObject(response).optJSONObject("result")?.optJSONArray("songs") }
            .getOrNull() ?: return emptyList()
        return songs.objects().map { it to scoreNeteaseSong(request, it) }
            .filter { it.second >= MinimumNeteaseScore }.sortedByDescending { it.second }
            .mapNotNull { (song, _) ->
                val id = song.optLong("id")
                val lyrics = loadFromNeteaseSongId(id) ?: return@mapNotNull null
                EchoLyricsCandidate("netease:$id", song.optString("name"),
                    song.optJSONArray("artists")?.objects()?.joinToString(" / ") { it.optString("name") }.orEmpty(),
                    song.optJSONObject("album")?.optString("name"), song.optLong("duration"), lyrics)
            }.take(limit).toList()
    }

    private fun lrclibCandidates(request: EchoLyricsSearchRequest, limit: Int): List<EchoLyricsCandidate> {
        val url = buildUrl("https://lrclib.net/api/search", buildList {
            add("track_name" to request.title); add("artist_name" to request.artist)
            request.album?.takeIf(String::isNotBlank)?.let { add("album_name" to it) }
        })
        val response = httpGet(url, LrclibHeaders) ?: return emptyList()
        val records = runCatching { JSONArray(response) }.getOrNull() ?: return emptyList()
        return records.objects().map { it to scoreLrclibRecord(request, it) }
            .filter { it.second >= MinimumLrclibScore }.sortedByDescending { it.second }
            .mapNotNull { (record, _) ->
                val raw = record.optLyricsText("syncedLyrics") ?: record.optLyricsText("plainLyrics")
                    ?: return@mapNotNull null
                val lyrics = parseOnlineLyrics(raw, "LRCLIB") ?: return@mapNotNull null
                EchoLyricsCandidate("lrclib:${record.optLong("id")}", record.optString("trackName"),
                    record.optString("artistName"), record.optString("albumName"),
                    (record.optDouble("duration", 0.0) * 1000).toLong(), lyrics)
            }.take(limit).toList()
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
        if (targetTitle.isBlank() || targetArtist.isBlank() || title.isBlank() || artist.isBlank()) return 0
        if (!(artist == targetArtist || artist.contains(targetArtist) || targetArtist.contains(artist))) return 0
        if (!(title == targetTitle || title.contains(targetTitle) || targetTitle.contains(title))) return 0
        if (request.durationMs > 0L && candidateDurationMs > 0L &&
            abs(request.durationMs - candidateDurationMs) > 15_000L) return 0
        val versions = listOf("live", "remix", "instrumental", "karaoke", "现场", "伴奏")
        if (versions.any { targetTitle.contains(it) != title.contains(it) }) return 0
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

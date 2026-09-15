package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricsCandidate
import app.echo.android.model.lyrics.EchoLyrics
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
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
    fun loadForTrack(request: EchoLyricsSearchRequest, checkCancelled: () -> Unit = {}): EchoLyrics? {
        if (request.title.isBlank() || request.artist.isBlank()) return null
        val matcher = LyricsCandidateMatcher(request)
        val lrclibFetch = startSpeculative { lrclibRecords(request, matcher, checkCancelled) }
        try {
            val netease = neteaseRecords(request, matcher, checkCancelled).filter { it.match.automatic }
            val attempted = mutableSetOf<String>()
            var fastResult: EchoLyricsCandidate? = null
            // A strong identity match needs only one search and one lyric download.
            netease.firstOrNull()?.takeIf { it.match.fast }?.let { record ->
                attempted += record.id
                val loaded = record.load(checkCancelled)
                fastResult = loaded
                if (loaded?.lyrics?.isSynced == true) {
                    lrclibFetch.cancel()
                    return loaded.lyrics
                }
            }
            val ranked = (netease + lrclibFetch.await(checkCancelled))
                .filter { it.match.automatic }.sortedByDescending { it.match.score }
            var plainFallback: EchoLyrics? = null
            var downloads = attempted.size
            for (record in ranked) {
                checkCancelled()
                val candidate = if (record.id in attempted) fastResult else {
                    if (record.songId != null && downloads >= MaxAutomaticDownloads) continue
                    if (record.songId != null) downloads++
                    record.load(checkCancelled)
                } ?: continue
                if (candidate.lyrics.isSynced) return candidate.lyrics
                if (plainFallback == null) plainFallback = candidate.lyrics
            }
            return plainFallback
        } catch (error: Throwable) {
            lrclibFetch.cancel()
            throw error
        }
    }

    fun search(request: EchoLyricsSearchRequest, checkCancelled: () -> Unit = {}): List<EchoLyricsCandidate> {
        if (request.title.isBlank() || request.artist.isBlank()) return emptyList()
        val matcher = LyricsCandidateMatcher(request)
        val neteaseFetch = startSpeculative { neteaseRecords(request, matcher, checkCancelled, allowTitleOnly = true) }
        val lrclibFetch = startSpeculative { lrclibRecords(request, matcher, checkCancelled, allowTitleOnly = true) }
        val records = try {
            (neteaseFetch.await(checkCancelled) + lrclibFetch.await(checkCancelled))
                .sortedByDescending { it.match.score }
        } catch (error: Throwable) {
            neteaseFetch.cancel()
            lrclibFetch.cancel()
            throw error
        }
        var downloads = 0
        return records.asSequence().mapNotNull { record ->
            checkCancelled()
            if (record.songId != null && downloads++ >= MaxManualDownloads) return@mapNotNull null
            record.load(checkCancelled)
        }.take(10).toList()
    }

    fun loadFromNeteaseSongId(songId: Long, checkCancelled: () -> Unit = {}): EchoLyrics? {
        if (songId <= 0L) return null
        val url = buildUrl("https://music.163.com/api/song/lyric", listOf(
            "id" to songId.toString(), "lv" to "-1", "kv" to "-1", "tv" to "-1",
            "yv" to "-1", "rv" to "-1", "yrv" to "-1", "ytv" to "-1",
        ))
        checkCancelled()
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

    private fun neteaseRecords(
        request: EchoLyricsSearchRequest, matcher: LyricsCandidateMatcher, checkCancelled: () -> Unit,
        allowFallback: Boolean = true,
        allowTitleOnly: Boolean = false,
        queryTitle: String = lyricsSearchTitle(request),
        queryArtist: String? = request.artist,
    ): List<CandidateRecord> {
        val search = if (queryArtist.isNullOrBlank()) queryTitle else "$queryTitle $queryArtist"
        val url = buildUrl("https://music.163.com/api/search/get/web", listOf(
            "s" to search, "type" to "1", "limit" to "15", "offset" to "0",
        ))
        checkCancelled()
        val response = httpGet(url, NeteaseHeaders) ?: return emptyList()
        checkCancelled()
        val root = runCatching { JSONObject(response) }.getOrNull() ?: return emptyList()
        if (root.optInt("code", 200) != 200) return emptyList()
        val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
        val candidates = songs.objects().take(15).mapNotNull { song ->
            val id = song.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
            val title = song.optString("name")
            val artist = song.optJSONArray("artists")?.objects()?.joinToString(" / ") { it.optString("name") }.orEmpty()
            val album = song.optJSONObject("album")?.optString("name")
            val duration = song.optLong("duration")
            val artistAliases = song.optJSONArray("artists")?.objects()?.map { person ->
                listOf(person.optString("name")) + person.lyricsAliases("alias", "trans")
            }?.toList().orEmpty()
            val match = matcher.match(title, artist, album, duration,
                song.lyricsAliases("alias", "tns"), artistAliases) ?: return@mapNotNull null
            CandidateRecord("netease:$id", title, artist, album, duration, match, songId = id)
        }.distinctBy { it.id }.sortedByDescending { it.match.score }.toList()
        if (allowFallback && candidates.isEmpty()) {
            lyricsSearchFallback(request)?.let { fallback ->
                val found = neteaseRecords(fallback, matcher, checkCancelled, allowFallback = false)
                if (found.isNotEmpty()) return found
            }
        }
        if (allowFallback && shouldSearchTitleOnly(request, candidates, allowTitleOnly)) {
            val extra = neteaseRecords(
                request, matcher, checkCancelled,
                allowFallback = false,
                queryTitle = lyricsSearchTitle(request),
                queryArtist = null,
            )
            return (candidates + extra).distinctBy { it.id }.sortedByDescending { it.match.score }
        }
        return candidates
    }

    private fun lrclibRecords(
        request: EchoLyricsSearchRequest, matcher: LyricsCandidateMatcher, checkCancelled: () -> Unit,
        allowFallback: Boolean = true,
        allowTitleOnly: Boolean = false,
        queryTitle: String = lyricsSearchTitle(request),
        queryArtist: String? = request.artist,
    ): List<CandidateRecord> {
        // Album is ranking evidence, not a search constraint: compilation tags must not hide the song.
        val params = buildList {
            add("track_name" to queryTitle)
            if (!queryArtist.isNullOrBlank()) add("artist_name" to queryArtist)
        }
        val url = buildUrl("https://lrclib.net/api/search", params)
        checkCancelled()
        val response = httpGet(url, LrclibHeaders) ?: return emptyList()
        checkCancelled()
        val records = runCatching { JSONArray(response) }.getOrNull() ?: return emptyList()
        val candidates = records.objects().take(100).mapNotNull { record ->
            val id = record.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
            val title = record.optString("trackName", record.optString("name"))
            val artist = record.optString("artistName")
            val album = record.optString("albumName")
            val duration = (record.optDouble("duration", 0.0) * 1000).toLong()
            val match = matcher.match(title, artist, album, duration) ?: return@mapNotNull null
            CandidateRecord("lrclib:$id", title, artist, album, duration, match,
                synced = record.optLyricsText("syncedLyrics"), plain = record.optLyricsText("plainLyrics"))
        }.distinctBy { it.id }.toList()
        if (allowFallback && candidates.isEmpty()) {
            lyricsSearchFallback(request)?.let { fallback ->
                val found = lrclibRecords(fallback, matcher, checkCancelled, allowFallback = false)
                if (found.isNotEmpty()) return found
            }
        }
        if (allowFallback && shouldSearchTitleOnly(request, candidates, allowTitleOnly)) {
            val extra = lrclibRecords(
                request, matcher, checkCancelled,
                allowFallback = false,
                queryTitle = lyricsSearchTitle(request),
                queryArtist = null,
            )
            return (candidates + extra).distinctBy { it.id }
        }
        return candidates
    }

    private fun shouldSearchTitleOnly(
        request: EchoLyricsSearchRequest,
        candidates: List<CandidateRecord>,
        allowTitleOnly: Boolean,
    ): Boolean {
        if (candidates.any { it.match.automatic || it.match.sameArtists }) return false
        if (lyricsCoverTitleOnlyQuery(request) != null) return true
        return allowTitleOnly && request.durationMs > 0L
    }

    private inner class CandidateRecord(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val duration: Long,
        val match: LyricsCandidateMatcher.Match,
        val songId: Long? = null,
        val synced: String? = null,
        val plain: String? = null,
    ) {
        fun load(checkCancelled: () -> Unit): EchoLyricsCandidate? {
            checkCancelled()
            val lyrics = if (songId != null) loadFromNeteaseSongId(songId, checkCancelled) else {
                synced?.let { parseOnlineLyrics(it, "LRCLIB") }
                    ?: plain?.let { parseOnlineLyrics(it, "LRCLIB") }
            }
            checkCancelled()
            return lyrics?.let { EchoLyricsCandidate(id, title, artist, album, duration, it) }
        }
    }

    private fun parseOnlineLyrics(rawLyrics: String, sourceLabel: String): EchoLyrics? =
        runCatching { EchoLyricsParser.parse(rawLyrics, sourceLabel = sourceLabel) }
            .getOrNull()
            ?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
            ?.let { lyrics ->
                lyrics.copy(metadata = lyrics.metadata + ("provider" to sourceLabel))
            }

    private fun <T> startSpeculative(block: () -> T): LyricsSpeculative<T> {
        val fetch = LyricsSpeculative<T>()
        Thread({ fetch.run(block) }, "echo-lyrics").apply {
            isDaemon = true
            start()
        }
        return fetch
    }

    private companion object {
        const val MaxAutomaticDownloads = 3
        const val MaxManualDownloads = 5

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

private fun JSONArray.objects(): Sequence<JSONObject> =
    sequence {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { yield(it) }
        }
    }

private fun JSONObject.optLyricsText(name: String): String? =
    optString(name)
        .takeIf { it.isNotBlank() && it != "null" }


private class LyricsSpeculative<T> {
    @Volatile private var abandoned = false
    private val done = ArrayBlockingQueue<Result<T>>(1)

    fun run(block: () -> T) {
        val result = runCatching(block)
        if (!abandoned) done.offer(result)
    }

    fun cancel() {
        abandoned = true
    }

    fun await(checkCancelled: () -> Unit): T {
        while (true) {
            checkCancelled()
            val result = done.poll(50, TimeUnit.MILLISECONDS) ?: continue
            return result.getOrThrow()
        }
    }
}

private fun JSONObject.lyricsAliases(vararg fields: String): List<String> = fields.flatMap { field ->
    when (val value = opt(field)) {
        is JSONArray -> (0 until minOf(value.length(), 12)).mapNotNull { (value.opt(it) as? String)?.takeIf(String::isNotBlank) }
        is String -> listOf(value).filter(String::isNotBlank)
        else -> emptyList()
    }
}

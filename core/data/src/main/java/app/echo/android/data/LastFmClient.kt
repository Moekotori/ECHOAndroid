package app.echo.android.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LastFmApiException(val code: Int, message: String) : IOException(message) {
    val requiresAuthorization: Boolean get() = code == 9 || code == 4
    val retryable: Boolean get() = code in setOf(11, 16, 29)
}

data class LastFmLocalTrack(val id: String, val title: String, val artist: String)

data class LastFmImportTrack(val title: String, val artist: String, val loved: Boolean, val playCount: Int)
data class LastFmImportPage(val tracks: List<LastFmImportTrack>, val totalPages: Int)

private suspend fun <T> lastFmResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

data class LastFmSession(
    val username: String,
    val sessionKey: String,
)

data class LastFmWebAuthStart(
    val token: String,
    val url: String,
)

data class LastFmCredentials(
    val apiKey: String,
    val sharedSecret: String,
    val sessionKey: String,
)

data class LastFmTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
)

class LastFmClient(
    private val endpoint: String = "https://ws.audioscrobbler.com/2.0/",
) {
    suspend fun love(credentials: LastFmCredentials, title: String, artist: String): Result<Unit> = lastFmResult {
        request(linkedMapOf("method" to "track.love", "api_key" to credentials.apiKey,
            "sk" to credentials.sessionKey, "track" to title, "artist" to artist), credentials.sharedSecret)
        Unit
    }

    suspend fun importPage(credentials: LastFmCredentials, username: String, loved: Boolean, page: Int): LastFmImportPage {
        require(page in 1..100_000)
        val rootKey = if (loved) "lovedtracks" else "toptracks"
        val response = request(linkedMapOf(
            "method" to if (loved) "user.getLovedTracks" else "user.getTopTracks",
            "api_key" to credentials.apiKey, "user" to username, "period" to "overall",
            "limit" to "200", "page" to page.toString(),
        ), sharedSecret = null, post = false).getJSONObject(rootKey)
        val rows = response.optJSONArray("track")
        val tracks = buildList {
            if (rows != null) for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val title = row.optString("name").trim()
                val artist = (row.optJSONObject("artist")?.optString("name") ?: row.optString("artist")).trim()
                if (title.isNotBlank() && artist.isNotBlank()) add(LastFmImportTrack(
                    title.take(500), artist.take(500), loved, row.optLong("playcount").coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                ))
            }
        }
        val total = response.optJSONObject("@attr")?.optInt("totalPages", 1) ?: 1
        require(total in 0..100_000) { "Last.fm library exceeds supported page count" }
        return LastFmImportPage(tracks, total)
    }
    suspend fun createWebAuthToken(apiKey: String, sharedSecret: String): Result<LastFmWebAuthStart> = lastFmResult {
        val normalizedApiKey = apiKey.trim()
        val params = linkedMapOf(
            "method" to "auth.getToken",
            "api_key" to normalizedApiKey,
        )
        val response = request(params, sharedSecret)
        val token = response.getString("token").trim()
        LastFmWebAuthStart(
            token = token,
            url = authorizationUrl(normalizedApiKey, token),
        )
    }

    suspend fun completeWebAuth(
        apiKey: String,
        sharedSecret: String,
        token: String,
    ): Result<LastFmSession> = lastFmResult {
        val params = linkedMapOf(
            "method" to "auth.getSession",
            "api_key" to apiKey.trim(),
            "token" to token.trim(),
        )
        val response = request(params, sharedSecret)
        val session = response.getJSONObject("session")
        LastFmSession(
            username = session.getString("name"),
            sessionKey = session.getString("key"),
        )
    }

    suspend fun authenticate(
        apiKey: String,
        sharedSecret: String,
        username: String,
        password: String,
    ): Result<LastFmSession> = lastFmResult {
        val params = linkedMapOf(
            "method" to "auth.getMobileSession",
            "username" to username.trim(),
            "password" to password,
            "api_key" to apiKey.trim(),
        )
        val response = request(params, sharedSecret)
        val session = response.getJSONObject("session")
        LastFmSession(
            username = session.getString("name"),
            sessionKey = session.getString("key"),
        )
    }

    suspend fun updateNowPlaying(credentials: LastFmCredentials, track: LastFmTrack): Result<Unit> = lastFmResult {
        val params = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to track.artist,
            "track" to track.title,
            "api_key" to credentials.apiKey,
            "sk" to credentials.sessionKey,
        )
        track.album?.let { params["album"] = it }
        if (track.durationMs > 0L) params["duration"] = (track.durationMs / 1000L).toString()
        request(params, credentials.sharedSecret).let { }
    }

    suspend fun scrobble(
        credentials: LastFmCredentials,
        track: LastFmTrack,
        startedAtEpochSeconds: Long,
    ): Result<Unit> = lastFmResult {
        val params = linkedMapOf(
            "method" to "track.scrobble",
            "artist" to track.artist,
            "track" to track.title,
            "timestamp" to startedAtEpochSeconds.toString(),
            "api_key" to credentials.apiKey,
            "sk" to credentials.sessionKey,
        )
        track.album?.let { params["album"] = it }
        val response = request(params, credentials.sharedSecret)
        val scrobbles = response.optJSONObject("scrobbles")
        if (scrobbles?.optJSONObject("@attr")?.optInt("accepted", 0) != 1) {
            val ignored = scrobbles?.optJSONObject("scrobble")?.optJSONObject("ignoredMessage")
            throw LastFmApiException(100 + (ignored?.optInt("code") ?: 0),
                ignored?.optString("#text")?.takeIf(String::isNotBlank) ?: "Last.fm did not accept the listen")
        }
    }

    private suspend fun request(
        params: LinkedHashMap<String, String>,
        sharedSecret: String?,
        post: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val signedParams = LinkedHashMap(params)
        if (sharedSecret != null) signedParams["api_sig"] = sign(params, sharedSecret.trim())
        signedParams["format"] = "json"
        val encoded = signedParams.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        val target = if (post) endpoint else endpoint + (if ('?' in endpoint) "&" else "?") + encoded
        val connection = (java.net.URI(target).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = if (post) "POST" else "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = post
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (post) connection.outputStream.use { it.write(encoded.toByteArray(StandardCharsets.UTF_8)) }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readTextBounded() }
            } else {
                connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readTextBounded() }.orEmpty()
            }
            val json = JSONObject(responseText.ifBlank { "{}" })
            if (connection.responseCode !in 200..299 || json.has("error") || json.optString("status") == "failed") {
                val message = json.optString("message").ifBlank { "HTTP ${connection.responseCode}" }
                throw LastFmApiException(json.optInt("error", if (connection.responseCode >= 500) 16 else 6), message.take(300))
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun sign(params: Map<String, String>, sharedSecret: String): String {
        val signatureBase = buildString {
            params.toSortedMap().forEach { (key, value) ->
                if (key != "format" && key != "callback") {
                    append(key)
                    append(value)
                }
            }
            append(sharedSecret)
        }
        val bytes = MessageDigest.getInstance("MD5")
            .digest(signatureBase.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun authorizationUrl(apiKey: String, token: String): String =
        "https://www.last.fm/api/auth/?api_key=${apiKey.urlEncode()}&token=${token.urlEncode()}"
}

private fun java.io.Reader.readTextBounded(): String {
    val buffer = CharArray(8192)
    val result = StringBuilder()
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.length + count <= 2 * 1024 * 1024) { "Last.fm response exceeds size limit" }
        result.append(buffer, 0, count)
    }
    return result.toString()
}

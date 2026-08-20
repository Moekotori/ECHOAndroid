package app.echo.android.data

import app.echo.android.model.library.LibrarySource
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.absoluteValue
import org.json.JSONArray
import org.json.JSONObject

data class SubsonicEndpoint(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    val normalizedBaseUrl: String =
        baseUrl.trim().trimEnd('/')

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
)

internal class SubsonicClient(
    private val endpoint: SubsonicEndpoint,
    private val httpGet: (String) -> String? = ::defaultHttpGet,
    private val saltFactory: () -> String = ::randomTokenSalt,
) {
    fun ping() {
        val response = request("ping.view")
        response.subsonicRoot()
    }

    fun fetchAlbums(
        pageSize: Int = AlbumPageSize,
        maxAlbums: Int = MaxAlbumsPerSync,
    ): List<SubsonicAlbum> {
        val albums = ArrayList<SubsonicAlbum>()
        var offset = 0
        while (albums.size < maxAlbums) {
            val root = request(
                path = "getAlbumList2.view",
                params = listOf(
                    "type" to "alphabeticalByName",
                    "size" to pageSize.toString(),
                    "offset" to offset.toString(),
                ),
            ).subsonicRoot()
            val batch = root.optJSONObject("albumList2")
                ?.jsonObjects("album")
                ?.map { it.toSubsonicAlbum() }
                .orEmpty()
            if (batch.isEmpty()) break
            albums += batch
            if (batch.size < pageSize) break
            offset += batch.size
        }
        return albums
    }

    fun fetchAlbumSongs(album: SubsonicAlbum): List<SubsonicSong> {
        val root = request(
            path = "getAlbum.view",
            params = listOf("id" to album.id),
        ).subsonicRoot()
        val albumObject = root.optJSONObject("album") ?: return emptyList()
        return albumObject.jsonObjects("song").map { it.toSubsonicSong(album) }
    }

    fun fetchSongsBySearch3(
        pageSize: Int = SongPageSize,
        maxSongs: Int = MaxSongsPerSync,
    ): List<SubsonicSong> {
        for (query in listOf("*", "")) {
            val songs = ArrayList<SubsonicSong>()
            var offset = 0
            while (songs.size < maxSongs) {
                val remaining = (maxSongs - songs.size).coerceAtMost(pageSize)
                val root = request(
                    path = "search3.view",
                    params = listOf(
                        "query" to query,
                        "songCount" to remaining.toString(),
                        "songOffset" to offset.toString(),
                        "albumCount" to "0",
                        "artistCount" to "0",
                    ),
                ).subsonicRoot()
                val batch = root.optJSONObject("searchResult3")
                    ?.jsonObjects("song")
                    ?.map { it.toSubsonicSong() }
                    .orEmpty()
                if (batch.isEmpty()) break
                songs += batch
                if (batch.size < remaining) break
                offset += batch.size
            }
            if (songs.isNotEmpty()) return songs
        }
        return emptyList()
    }

    fun streamUrl(songId: String): String =
        buildUrl("stream.view", listOf("id" to songId))

    fun coverArtUrl(coverArt: String?): String? =
        coverArt?.takeIf { it.isNotBlank() }
            ?.let { buildUrl("getCoverArt.view", listOf("id" to it)) }

    private fun request(path: String, params: List<Pair<String, String>> = emptyList()): JSONObject {
        val url = buildUrl(path, params)
        val body = httpGet(url) ?: error("远程服务器无响应")
        return runCatching { JSONObject(body) }
            .getOrElse { error("远程服务器返回了无法解析的数据") }
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
        const val AlbumPageSize = 100
        const val SongPageSize = 500
        const val MaxAlbumsPerSync = 2_000
        const val MaxSongsPerSync = 20_000
    }
}

internal fun SubsonicSong.toLibraryTrackEntity(
    endpoint: SubsonicEndpoint,
    client: SubsonicClient,
    scanRunId: Long,
): LibraryTrackEntity {
    val contentUri = client.streamUrl(id)
    val artworkUri = client.coverArtUrl(coverArt)
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
        sampleRateHz = null,
        dateModifiedSeconds = LibraryFingerprintPolicy.remoteDateModifiedSeconds(scanRunId),
        source = endpoint.sourceId,
        relativePath = path?.substringBeforeLast('/', missingDelimiterValue = ""),
        lastSeenScanRunId = scanRunId,
    ).withScanMetadata(scanRunId)
}

private fun JSONObject.subsonicRoot(): JSONObject {
    val root = optJSONObject("subsonic-response") ?: error("不是 Subsonic 兼容响应")
    val status = root.optString("status")
    if (!status.equals("ok", ignoreCase = true)) {
        val message = root.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: "Subsonic 认证或请求失败"
        error(message)
    }
    return root
}

private fun JSONObject.toSubsonicAlbum(): SubsonicAlbum =
    SubsonicAlbum(
        id = optString("id"),
        name = optString("name").ifBlank { optString("album") },
        artist = optString("artist").takeIf { it.isNotBlank() },
        coverArt = optString("coverArt").takeIf { it.isNotBlank() },
        year = optInt("year").takeIf { it > 0 },
        songCount = optInt("songCount").coerceAtLeast(0),
    )

private fun JSONObject.toSubsonicSong(album: SubsonicAlbum? = null): SubsonicSong =
    SubsonicSong(
        id = optString("id"),
        title = optString("title"),
        artist = optString("artist").ifBlank { album?.artist.orEmpty() },
        album = optString("album").ifBlank { album?.name.orEmpty() }.takeIf { it.isNotBlank() },
        albumArtist = optString("albumArtist").ifBlank { album?.artist.orEmpty() }.takeIf { it.isNotBlank() },
        coverArt = optString("coverArt").ifBlank { album?.coverArt.orEmpty() }.takeIf { it.isNotBlank() },
        durationSeconds = optLong("duration", 0L),
        trackNumber = optInt("track").takeIf { it > 0 },
        discNumber = optInt("discNumber").takeIf { it > 0 },
        year = optInt("year").takeIf { it > 0 } ?: album?.year,
        contentType = optString("contentType").takeIf { it.isNotBlank() },
        suffix = optString("suffix").takeIf { it.isNotBlank() },
        sizeBytes = optLong("size", 0L),
        bitRateKbps = optInt("bitRate").takeIf { it > 0 },
        path = optString("path").takeIf { it.isNotBlank() },
    )

private fun defaultHttpGet(url: String): String? {
    val connection = (URI(url).toURL().openConnection() as? HttpURLConnection) ?: return null
    return runCatching {
        connection.connectTimeout = 4_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ECHOAndroid/0.1")
        if (connection.responseCode in 200..299) {
            connection.inputStream.use { it.readUtf8Limited(maxBytes = 2_500_000) }
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

private fun JSONArray.objects(): Sequence<JSONObject> =
    sequence {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { yield(it) }
        }
    }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

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

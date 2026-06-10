package app.echo.android.data

import app.echo.android.model.library.NeteaseAudioQuality
import app.echo.android.model.library.NeteaseRemotePlaylist
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class NeteaseSession(
    val userId: Long,
    val nickname: String,
    val cookie: String,
)

data class NeteasePlaylistImport(
    val playlist: LibraryPlaylistEntity,
    val tracks: List<LibraryTrackEntity>,
    val playlistTracks: List<LibraryPlaylistTrackEntity>,
)

internal data class NeteaseSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
)

class NeteaseCloudMusicClient(
    private val client: OkHttpClient = SharedNeteaseHttpClient,
) {
    fun loginByPhone(
        phone: String,
        password: String,
        countryCode: String = "86",
    ): NeteaseSession {
        val response = postJson(
            url = "$ApiBase/login/cellphone",
            cookie = "",
            form = FormBody.Builder()
                .add("phone", phone.trim())
                .add("password", md5(password))
                .add("countrycode", countryCode.trim().ifBlank { "86" })
                .add("rememberLogin", "true")
                .build(),
        )
        val profile = response.body.optJSONObject("profile")
            ?: error(response.body.optString("message").ifBlank { "NetEase login requires verification or failed" })
        val cookie = response.cookie.ifBlank {
            response.body.optString("cookie").takeIf { it.isNotBlank() } ?: error("NetEase login did not return a session")
        }
        return NeteaseSession(
            userId = profile.optLong("userId"),
            nickname = profile.optString("nickname").ifBlank { "NetEase User" },
            cookie = normalizeCookie(cookie),
        )
    }

    fun loginWithCookie(rawCookie: String): NeteaseSession {
        val cookie = normalizeCookie(rawCookie)
        val body = getJson("$ApiBase/user/account", cookie)
        val profile = body.optJSONObject("profile")
            ?: body.optJSONObject("account")
            ?: error("NetEase cookie is invalid or expired")
        val userId = profile.optLong("userId", profile.optLong("id", 0L))
        if (userId <= 0L) error("NetEase cookie is missing user id")
        return NeteaseSession(
            userId = userId,
            nickname = profile.optString("nickname").ifBlank { "NetEase User" },
            cookie = cookie,
        )
    }

    fun fetchUserPlaylists(session: NeteaseSession): List<NeteaseRemotePlaylist> {
        val url = "$ApiBase/user/playlist".withQuery(
            "uid" to session.userId.toString(),
            "limit" to "1000",
            "offset" to "0",
        )
        val body = getJson(url, session.cookie)
        return body.optJSONArray("playlist")
            ?.objects()
            ?.map { playlist ->
                NeteaseRemotePlaylist(
                    id = playlist.optLong("id"),
                    name = playlist.optString("name").ifBlank { "NetEase Playlist" },
                    trackCount = playlist.optInt("trackCount"),
                    artworkUri = playlist.optString("coverImgUrl").takeIf { it.isNotBlank() },
                    creatorName = playlist.optJSONObject("creator")?.optString("nickname")?.takeIf { it.isNotBlank() },
                )
            }
            ?.filter { it.id > 0L }
            ?.toList()
            .orEmpty()
    }

    fun fetchPlaylistImport(
        session: NeteaseSession,
        playlistId: Long,
        maxTracks: Int = MaxPlaylistTracks,
    ): NeteasePlaylistImport {
        val playlist = fetchPlaylistHeader(session, playlistId)
        val songs = fetchPlaylistSongs(session, playlistId, maxTracks)
        val sourcePlaylistId = neteasePlaylistId(playlistId)
        val scanRunId = System.currentTimeMillis()
        val tracks = songs.map { song -> song.toLibraryTrackEntity(scanRunId) }
        return NeteasePlaylistImport(
            playlist = LibraryPlaylistEntity(
                id = sourcePlaylistId,
                name = playlist.name,
                source = NeteaseSourceId,
                artworkUri = playlist.artworkUri,
                trackCount = tracks.size,
                updatedAtEpochMs = scanRunId,
            ),
            tracks = tracks,
            playlistTracks = tracks.mapIndexed { index, track ->
                LibraryPlaylistTrackEntity(
                    playlistId = sourcePlaylistId,
                    trackId = track.id,
                    position = index,
                )
            },
        )
    }

    fun resolvePlaybackUrls(
        sessionCookie: String?,
        songIds: List<Long>,
        quality: NeteaseAudioQuality,
    ): Map<Long, String> {
        if (songIds.isEmpty()) return emptyMap()
        val encodedIds = songIds.distinct().joinToString(prefix = "[", postfix = "]")
        val url = "$ApiBase/song/enhance/player/url/v1".withQuery(
            "ids" to encodedIds,
            "level" to quality.id,
            "encodeType" to if (quality == NeteaseAudioQuality.Standard) "mp3" else "flac",
        )
        val body = getJson(url, sessionCookie.orEmpty())
        return body.optJSONArray("data")
            ?.objects()
            ?.mapNotNull { item ->
                val id = item.optLong("id").takeIf { it > 0L } ?: return@mapNotNull null
                val streamUrl = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to streamUrl
            }
            ?.toMap()
            .orEmpty()
    }

    fun fetchLyrics(songId: Long): String? {
        val url = "$ApiBase/song/lyric".withQuery(
            "id" to songId.toString(),
            "lv" to "-1",
            "kv" to "-1",
            "tv" to "-1",
        )
        val body = getJson(url, "")
        return body.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
            ?: body.optJSONObject("yrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
            ?: body.optJSONObject("tlyric")?.optString("lyric")?.takeIf { it.isNotBlank() }
    }

    private fun fetchPlaylistHeader(session: NeteaseSession, playlistId: Long): NeteaseRemotePlaylist {
        val url = "$ApiBase/playlist/detail".withQuery(
            "id" to playlistId.toString(),
            "n" to "1",
            "s" to "0",
        )
        val playlist = getJson(url, session.cookie).optJSONObject("playlist")
            ?: error("NetEase playlist not found")
        return NeteaseRemotePlaylist(
            id = playlist.optLong("id", playlistId),
            name = playlist.optString("name").ifBlank { "NetEase Playlist" },
            trackCount = playlist.optInt("trackCount"),
            artworkUri = playlist.optString("coverImgUrl").takeIf { it.isNotBlank() },
            creatorName = playlist.optJSONObject("creator")?.optString("nickname")?.takeIf { it.isNotBlank() },
        )
    }

    private fun fetchPlaylistSongs(
        session: NeteaseSession,
        playlistId: Long,
        maxTracks: Int,
    ): List<NeteaseSong> {
        val tracks = ArrayList<NeteaseSong>()
        var offset = 0
        while (tracks.size < maxTracks) {
            val limit = minOf(PlaylistPageSize, maxTracks - tracks.size)
            val body = getJson(
                "$ApiBase/playlist/track/all".withQuery(
                    "id" to playlistId.toString(),
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                ),
                session.cookie,
            )
            val batch = body.optJSONArray("songs")
                ?.objects()
                ?.mapNotNull { it.toNeteaseSong() }
                ?.toList()
                .orEmpty()
            if (batch.isEmpty()) break
            tracks += batch
            if (batch.size < limit) break
            offset += batch.size
        }
        return tracks
    }

    private fun getJson(url: String, cookie: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .neteaseHeaders(cookie)
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("NetEase HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun postJson(url: String, cookie: String, form: FormBody): NeteaseResponse {
        val request = Request.Builder()
            .url(url)
            .neteaseHeaders(cookie)
            .post(form)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("NetEase HTTP ${response.code}")
            NeteaseResponse(
                body = JSONObject(response.body?.string().orEmpty()),
                cookie = response.headers("Set-Cookie")
                    .joinToString("; ") { it.substringBefore(';') },
            )
        }
    }

    private data class NeteaseResponse(
        val body: JSONObject,
        val cookie: String,
    )

    private companion object {
        const val ApiBase = "https://music.163.com/api"
        const val PlaylistPageSize = 500
        const val MaxPlaylistTracks = 5_000
    }
}

const val NeteaseSourceId = "netease"

fun neteasePlaylistId(playlistId: Long): String =
    "$NeteaseSourceId:playlist:$playlistId"

fun neteaseSongId(songId: Long): String =
    "$NeteaseSourceId:song:$songId"

fun parseNeteaseSongId(trackId: String): Long? =
    trackId.removePrefix("$NeteaseSourceId:song:")
        .takeIf { it != trackId }
        ?.toLongOrNull()

private val SharedNeteaseHttpClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

private fun Request.Builder.neteaseHeaders(cookie: String): Request.Builder =
    apply {
        header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ECHOAndroid/0.1")
        header("Referer", "https://music.163.com/")
        header("Origin", "https://music.163.com")
        val normalizedCookie = normalizeCookie(cookie)
        if (normalizedCookie.isNotBlank()) {
            header("Cookie", normalizedCookie)
        }
    }

private fun String.withQuery(vararg params: Pair<String, String>): String {
    val base = toHttpUrl().newBuilder()
    params.forEach { (name, value) -> base.addQueryParameter(name, value) }
    return base.build().toString()
}

private fun NeteaseSong.toLibraryTrackEntity(scanRunId: Long): LibraryTrackEntity =
    LibraryTrackEntity(
        id = neteaseSongId(id),
        contentUri = "netease://song/$id",
        title = title,
        artist = artist.ifBlank { "Unknown Artist" },
        album = album,
        albumArtist = albumArtist,
        artworkUri = artworkUri,
        durationMs = durationMs.coerceAtLeast(0L),
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = null,
        mimeType = null,
        sizeBytes = 0L,
        sampleRateHz = null,
        dateModifiedSeconds = System.currentTimeMillis() / 1000L,
        source = NeteaseSourceId,
        relativePath = null,
        lastSeenScanRunId = scanRunId,
    ).withScanMetadata(scanRunId)

private fun JSONObject.toNeteaseSong(): NeteaseSong? {
    val id = optLong("id").takeIf { it > 0L } ?: return null
    val albumObject = optJSONObject("al") ?: optJSONObject("album")
    val artistObjects = optJSONArray("ar") ?: optJSONArray("artists")
    val artists = artistObjects.objects()
        .map { it.optString("name") }
        .filter { it.isNotBlank() }
        .joinToString(" / ")
    val discAndTrack = optInt("no", 0).takeIf { it > 0 }
    return NeteaseSong(
        id = id,
        title = optString("name").ifBlank { "NetEase Track $id" },
        artist = artists,
        album = albumObject?.optString("name")?.takeIf { it.isNotBlank() },
        albumArtist = artists.takeIf { it.isNotBlank() },
        artworkUri = albumObject?.optString("picUrl")?.takeIf { it.isNotBlank() },
        durationMs = optLong("dt", optLong("duration", 0L)),
        trackNumber = discAndTrack,
        discNumber = null,
    )
}

private fun JSONArray?.objects(): Sequence<JSONObject> =
    sequence {
        val array = this@objects ?: return@sequence
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { yield(it) }
        }
    }

internal fun normalizeCookie(rawCookie: String): String {
    val cookie = rawCookie.trim()
    if (cookie.isBlank()) return ""
    return when {
        "=" in cookie -> cookie
        else -> "MUSIC_U=${cookie.urlEncode()}"
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun md5(value: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

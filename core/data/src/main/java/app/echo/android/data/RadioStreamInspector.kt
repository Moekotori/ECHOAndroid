package app.echo.android.data

import app.echo.android.model.radio.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.*

/** Only explicit user probes read a short prefix. Saving and browsing never probe audio. */
class RadioStreamInspector {
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build()
    suspend fun inspect(url: String): RadioProbeResult = withTimeout(8000) {
        require(EchoRadioStation.validUrl(url))
        var current = url
        repeat(6) {
            val response = request(current)
            if (response.first != null) current = response.first!! else return@withTimeout response.second!!
        }
        throw IOException("Radio redirect limit")
    }
    private suspend fun request(url: String): Pair<String?, RadioProbeResult?> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "ECHOAndroid").header("Icy-MetaData", "0").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use { r ->
                        if (r.code in listOf(301, 302, 303, 307, 308)) {
                            val next = r.header("Location")?.let(r.request.url::resolve)?.toString()
                            require(next != null && EchoRadioStation.validUrl(next))
                            return@use next to null
                        }
                        if (!r.isSuccessful) throw IOException("Radio HTTP ${r.code}")
                        val type = r.header("Content-Type").orEmpty().lowercase()
                        val playlist = type.contains("mpegurl") || type.contains("scpls") || type.contains("text/") || type.contains("json") ||
                            r.request.url.encodedPath.matches(Regex("(?i).*\\.(m3u|pls|txt|json)$"))
                        var hls = false
                        val stations = if (playlist) {
                            val source = r.body?.source() ?: throw IOException("Empty radio response")
                            source.request(256 * 1024L + 1)
                            require(source.buffer.size <= 256 * 1024L)
                            val text = source.readUtf8()
                            // HLS is a live media source, not a station playlist.
                            hls = text.contains("#EXT-X-")
                            if (hls) emptyList() else RadioPlaylistCodec.parse(text, r.request.url.toString()).also { require(it.isNotEmpty()) }
                        } else {
                            require(type.startsWith("audio/") || type.startsWith("video/") || type.contains("ogg") || type.contains("octet-stream") || r.header("icy-name") != null) { "Not a direct radio stream" }
                            emptyList()
                        }
                        val codec = when {
                            hls || type.contains("mpegurl") -> "HLS"
                            type.contains("mpeg") -> "MP3"
                            type.contains("aac") -> "AAC"
                            type.contains("flac") -> "FLAC"
                            type.contains("opus") -> "Opus"
                            type.contains("ogg") -> "OGG"
                            type.contains("wav") -> "WAV"
                            else -> null
                        }
                        null to RadioProbeResult(stations, r.header("icy-name")?.take(120), r.header("icy-description")?.take(512),
                            r.header("icy-genre")?.take(512), r.header("icy-url")?.takeIf(EchoRadioStation::validUrl), codec,
                            r.header("icy-br")?.toIntOrNull()?.takeIf { it > 0 })
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
}

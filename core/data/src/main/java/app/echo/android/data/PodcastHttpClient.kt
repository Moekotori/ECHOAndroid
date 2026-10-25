package app.echo.android.data

import app.echo.android.model.podcast.PodcastLimits
import app.echo.android.model.radio.EchoRadioStation
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.*

data class PodcastHttpResponse(
    val bytes: ByteArray?, val finalUrl: String, val etag: String?, val modified: String?, val charset: String?,
)

class PodcastHttpClient(
    version: String,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).retryOnConnectionFailure(false).build(),
) {
    private val userAgent = "ECHOAndroid/$version"
    suspend fun fetch(url: String, etag: String?, modified: String?): PodcastHttpResponse = withTimeout(12_000) {
        require(EchoRadioStation.validUrl(url))
        var current = url
        repeat(6) {
            val response = request(current, etag, modified)
            if (response.redirect != null) {
                current = response.redirect
            } else return@withTimeout response.result!!
        }
        throw IOException("Podcast redirect limit")
    }

    private data class Result(val result: PodcastHttpResponse? = null, val redirect: String? = null)
    private suspend fun request(url: String, etag: String?, modified: String?): Result = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("User-Agent", userAgent)
            .header("Accept", "application/rss+xml, application/xml, text/xml").apply {
                etag?.let { header("If-None-Match", it) }
                modified?.let { header("If-Modified-Since", it) }
            }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code in listOf(301, 302, 303, 307, 308)) {
                            val target = it.header("Location")?.let(it.request.url::resolve)?.toString()
                            require(target != null && EchoRadioStation.validUrl(target)) { "Invalid RSS redirect" }
                            return@use Result(redirect = target)
                        }
                        if (it.code != 304 && !it.isSuccessful) throw IOException("Podcast HTTP ${it.code}")
                        val bytes = if (it.code == 304) null else {
                            val source = it.body?.source() ?: throw IOException("Empty podcast response")
                            source.request(PodcastLimits.FeedBytes + 1L)
                            if (source.buffer.size > PodcastLimits.FeedBytes) throw IOException("Podcast response too large")
                            source.readByteArray()
                        }
                        Result(PodcastHttpResponse(bytes, it.request.url.toString(),
                            it.header("ETag")?.take(2048), it.header("Last-Modified")?.take(2048),
                            it.body?.contentType()?.charset()?.name()))
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
}

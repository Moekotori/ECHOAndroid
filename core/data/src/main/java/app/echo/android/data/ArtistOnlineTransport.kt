package app.echo.android.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ArtistOnlineTransport(appVersion: String) {
    private val userAgent = "ECHOAndroid/$appVersion (https://github.com/moekotori/echoandroid)"
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(16, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()

    suspend fun json(url: HttpUrl): JSONObject = JSONObject(text(url)).also {
        if (it.has("error") && !it.isNull("error")) throw IOException("Artist information service error")
    }

    suspend fun text(url: HttpUrl, redirects: Int = 0): String {
        val response = suspendCancellableCoroutine<Pair<String?, String?>> { continuation ->
            val call = client.newCall(Request.Builder().url(url).header("User-Agent", userAgent)
                .header("Api-User-Agent", userAgent).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (it.isRedirect) return@use null to it.header("Location")
                            if (!it.isSuccessful) throw IOException("${url.host}: HTTP ${it.code}")
                            val source = it.body?.source() ?: throw IOException("Empty artist response")
                            source.request(2_000_001L)
                            if (source.buffer.size > 2_000_000L) throw IOException("Artist response too large")
                            source.readUtf8() to null
                        }
                    }
                    if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
        response.first?.let { return it }
        val next = response.second?.let(url::resolve)
        if (redirects >= 2 || next == null || !next.isHttps || next.host != url.host) throw IOException("Unsupported artist service redirect")
        return text(next, redirects + 1)
    }
}

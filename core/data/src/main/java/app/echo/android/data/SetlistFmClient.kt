package app.echo.android.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class SetlistFmClient(
    appVersion: String,
) {
    private val userAgent = "ECHOAndroid/$appVersion (https://github.com/moekotori/echoandroid)"
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val gate = Mutex()
    private var lastRequestAt = 0L

    suspend fun searchSetlists(apiKey: String, artistName: String, apiDate: String?): JSONObject {
        val url = "https://api.setlist.fm/rest/1.0/search/setlists".toHttpUrl().newBuilder()
            .addQueryParameter("artistName", artistName)
            .addQueryParameter("p", "1")
            .apply { if (!apiDate.isNullOrBlank()) addQueryParameter("date", apiDate) }
            .build()
        return get(apiKey, url)
    }

    private suspend fun get(apiKey: String, url: HttpUrl): JSONObject = gate.withLock {
        val wait = lastRequestAt + MinIntervalMs - System.currentTimeMillis()
        if (wait > 0L) delay(wait)
        lastRequestAt = System.currentTimeMillis()
        val body = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("x-api-key", apiKey)
                    .header("User-Agent", userAgent)
                    .build(),
            )
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (it.code == 404) return@use """{"setlist":[]}"""
                            if (!it.isSuccessful) throw IOException("setlist.fm: HTTP ${it.code}")
                            val source = it.body?.source() ?: throw IOException("Empty setlist response")
                            source.request(1_000_001L)
                            if (source.buffer.size > 1_000_000L) throw IOException("Setlist response too large")
                            source.readUtf8()
                        }
                    }
                    if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
        JSONObject(body)
    }

    private companion object {
        const val MinIntervalMs = 600L
    }
}

package app.echo.android.data

import app.echo.android.model.radio.EchoRadioDirectoryStation
import app.echo.android.model.radio.RadioDirectoryQuery
import app.echo.android.model.radio.RadioDirectoryPage
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RadioBrowserClient(
    appVersion: String,
    private val client: OkHttpClient = defaultClient(),
    private val servers: List<String> = RadioBrowserPolicy.Servers,
    private val discoverMirrors: (suspend () -> List<String>)? = null,
) {
    private val userAgent = "ECHOAndroid/$appVersion (https://github.com/moekotori/echoandroid)"

    suspend fun browse(query: RadioDirectoryQuery): RadioDirectoryPage = withTimeout(14_000) {
        withContext(Dispatchers.IO) {
            var error: Exception? = null
            val mirrors = ((discoverMirrors?.invoke() ?: emptyList()) + servers).distinct()
            for (server in mirrors.take(RadioBrowserPolicy.MaxServerAttempts)) {
                try {
                    val rows = RadioBrowserParser.parseStations(fetch(browseUrl(server, query)))
                    return@withContext RadioDirectoryPage(rows.take(20), rows.size > 20)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure }
            }
            throw error ?: IOException("Radio directory unavailable")
        }
    }

    suspend fun search(query: String): List<EchoRadioDirectoryStation> = withContext(Dispatchers.IO) {
        val normalized = RadioBrowserPolicy.normalizedQuery(query) ?: return@withContext emptyList()
        var lastError: Exception? = null
        for (server in servers.take(RadioBrowserPolicy.MaxServerAttempts)) {
            try {
                return@withContext RadioBrowserParser.parseStations(fetch(searchUrl(server, normalized)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IOException("Radio directory unavailable")
    }

    private suspend fun fetch(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                val content = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("Radio directory HTTP ${it.code}")
                        val source = it.body?.source() ?: throw IOException("Empty radio directory response")
                        source.request(RadioBrowserPolicy.MaxBodyBytes + 1L)
                        if (source.buffer.size > RadioBrowserPolicy.MaxBodyBytes) {
                            throw IOException("Radio directory response too large")
                        }
                        source.readUtf8()
                    }
                }
                if (continuation.isActive) content.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    companion object {
        fun browseUrl(server: String, query: RadioDirectoryQuery): HttpUrl = server.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegments(RadioBrowserPolicy.SearchPath)
            .addQueryParameter("name", query.name.trim().take(80))
            .addQueryParameter("countrycode", query.countryCode.trim().uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) }.orEmpty())
            .addQueryParameter("language", query.language.trim().take(80))
            .addQueryParameter("tag", query.tag.trim().take(80))
            .addQueryParameter("offset", (query.page.coerceIn(0, 1000) * 20).toString())
            .addQueryParameter("limit", "21")
            .addQueryParameter("hidebroken", "true")
            .addQueryParameter("order", "clickcount")
            .addQueryParameter("reverse", "true").build()

        fun searchUrl(server: String, query: String): HttpUrl {
            val origin = server.trim().trimEnd('/').toHttpUrl()
            return origin.newBuilder()
                .addPathSegments(RadioBrowserPolicy.SearchPath)
                .addQueryParameter("name", query)
                .addQueryParameter("limit", RadioBrowserPolicy.MaxResults.toString())
                .addQueryParameter("hidebroken", "true")
                .addQueryParameter("order", "clickcount")
                .addQueryParameter("reverse", "true")
                .build()
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }
}

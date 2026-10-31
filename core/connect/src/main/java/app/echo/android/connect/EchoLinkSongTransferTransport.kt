package app.echo.android.connect

import app.echo.android.model.connect.*
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject

class EchoSongTransferException(val code: String, val status: Int = 0) : IOException(code)

/** One dialog-owned client. JSON and streamed file I/O run on OkHttp workers, never the UI thread. */
class EchoLinkSongTransferTransport(private val endpoint: EchoRemoteEndpoint) : AutoCloseable {
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    private fun request(route: String, session: String? = null, query: Map<String, String> = emptyMap()): Request.Builder {
        val url = HttpUrl.Builder().scheme(endpoint.scheme).host(endpoint.host).port(endpoint.port)
            .addPathSegments("echo-link/v1/transfers").addPathSegment(route)
        query.forEach { (key, value) -> url.addQueryParameter(key, value) }
        return Request.Builder().url(url.build()).header("X-ECHO-Link-Version", "1").apply {
            if (endpoint.token.isBlank()) header("X-ECHO-Link-Direct", "1") else header("Authorization", "Bearer ${endpoint.token}")
            session?.let { header("X-ECHO-Link-Transfer", it) }
        }
    }
    private fun JSONObject.body(): RequestBody = toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    private suspend fun <T> execute(request: Request, read: (Response, () -> Unit) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    val check = { if (!continuation.isActive) throw CancellationException("transfer_cancelled") }
                    check()
                    if (!it.isSuccessful) {
                        val code = runCatching { JSONObject(it.peekBody(4096).string()).optString("error") }.getOrDefault("")
                        throw EchoSongTransferException(code.ifBlank { "transfer_failed" }, it.code)
                    }
                    read(it, check)
                } }
                continuation.resumeWith(result)
            }
        })
    }
    private suspend fun json(request: Request): JSONObject = execute(request) { response, check ->
        val source = checkNotNull(response.body).source()
        source.request(65537)
        if (source.buffer.size > 65536) throw EchoSongTransferException("response_too_large")
        check(); JSONObject(source.readUtf8())
    }
    suspend fun start(name: String, receiveReady: Boolean): String = json(request("session").post(
        JSONObject().put("version", 1).put("name", name.take(80)).put("receiveReady", receiveReady).body()).build()).getString("id")

    suspend fun poll(session: String, receiveReady: Boolean): List<EchoSongTransferItem> {
        val array = json(request("session", session, mapOf("receiveReady" to if (receiveReady) "1" else "0")).get().build()).getJSONArray("items")
        require(array.length() <= 64)
        return List(array.length()) { index -> array.getJSONObject(index).let { item ->
            val name = item.getString("name"); val size = item.getLong("size")
            require(EchoSongTransferPolicy.validFile(name, size))
            EchoSongTransferItem(item.getString("id"), name, size, item.optLong("bytes").coerceIn(0, size), item.getString("direction"), item.getString("state"))
        } }
    }
    suspend fun end(session: String) { json(request("session", session).delete().build()) }
    suspend fun cancel(session: String, id: String) { json(request("cancel", session).post(JSONObject().put("id", id).body()).build()) }
    suspend fun acknowledge(session: String, id: String, success: Boolean) {
        json(request("ack", session).post(JSONObject().put("id", id).put("success", success).body()).build())
    }
    suspend fun upload(session: String, name: String, size: Long, open: () -> InputStream, progress: (Long) -> Unit) {
        require(EchoSongTransferPolicy.validFile(name, size))
        val context = currentCoroutineContext()
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                open().use { input ->
                    val buffer = ByteArray(64 * 1024); var sent = 0L
                    while (sent < size) {
                        context.ensureActive()
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), size - sent).toInt())
                        if (count < 0) throw EchoSongTransferException("incomplete_file")
                        if (count == 0) continue
                        sink.write(buffer, 0, count); sent += count; progress(sent)
                    }
                    context.ensureActive()
                    if (input.read() != -1) throw EchoSongTransferException("source_changed")
                }
            }
        }
        json(request("upload", session).header("X-ECHO-Link-File-Name", URLEncoder.encode(name, "UTF-8").replace("+", "%20")).post(body).build())
    }
    suspend fun download(session: String, item: EchoSongTransferItem,
        save: (String, Long, InputStream, () -> Unit) -> String): String =
        execute(request("file", session, mapOf("id" to item.id)).get().build()) { response, check ->
            val body = checkNotNull(response.body)
            if (body.contentLength() != item.size) throw EchoSongTransferException("size_mismatch")
            save(item.name, item.size, body.byteStream(), check)
        }
    fun cancelActive() { client.dispatcher.cancelAll() }
    override fun close() { cancelActive(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
}

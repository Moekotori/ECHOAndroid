package app.echo.android.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

private val oneDriveHttp = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .callTimeout(20, TimeUnit.SECONDS)
    .build()

internal suspend fun <T> oneDriveRequest(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    val call = oneDriveHttp.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(IOException("OneDrive request failed"))
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resumeWith(runCatching { response.use(read) })
        }
    })
}

internal fun Response.oneDriveJson(): JSONObject {
    val source = body?.source() ?: throw IOException("Empty OneDrive response")
    if (source.request(2_097_153)) throw IOException("OneDrive response exceeds the size limit")
    return JSONObject(source.readUtf8())
}

internal class OneDriveGraph(private val token: suspend (rejected: String?) -> String) {
    suspend fun driveId(access: String): String = oneDriveRequest(request(DriveUrl, access)) { response ->
        response.readGraphJson().getString("id").also { require(it.isNotBlank() && it.length <= 512) }
    }

    suspend fun children(folderId: String?, next: String?, pageSize: Int = 100): OneDrivePage {
        val collection = (if (folderId == null) DriveUrl.newBuilder().addPathSegment("root") else itemUrl(folderId).newBuilder())
            .addPathSegment("children").build()
        val address = if (next == null) collection.newBuilder()
            .addQueryParameter("\$top", pageSize.toString())
            .addQueryParameter("\$select", "id,name,folder,file,audio,size,lastModifiedDateTime,remoteItem")
            .build() else oneDrivePageAddress(collection, next)
        return get(address) { response ->
            val payload = response.readGraphJson()
            val values = payload.getJSONArray("value")
            if (values.length() > pageSize) throw IOException("OneDrive page exceeds the item limit")
            OneDrivePage(
                List(values.length()) { index -> values.getJSONObject(index) }.filter { it.optJSONObject("remoteItem") == null },
                (payload.opt("@odata.nextLink") as? String)?.takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun streamUrl(itemId: String): String = get(itemUrl(itemId).newBuilder().addPathSegment("content").build()) { response ->
        if (response.code != 302) throw IOException("OneDrive did not return a download address")
        val address = response.header("Location")?.takeIf { it.length <= 16_384 }?.toHttpUrlOrNull()
        if (address?.isHttps != true || address.username.isNotEmpty() || address.password.isNotEmpty() || address.fragment != null) {
            throw IOException("Invalid OneDrive download address")
        }
        address.toString()
    }

    private suspend fun <T : Any> get(address: HttpUrl, read: (Response) -> T): T {
        val access = token(null)
        val result = oneDriveRequest(request(address, access)) { if (it.code == 401) null else read(it) }
        if (result != null) return result
        return oneDriveRequest(request(address, token(access)), read)
    }

    private fun itemUrl(id: String): HttpUrl {
        require(id.isNotBlank() && id.length <= 512 && id != "." && id != "..")
        return DriveUrl.newBuilder().addPathSegment("items").addPathSegment(id).build()
    }

    private fun request(address: HttpUrl, access: String): Request =
        Request.Builder().url(address).header("Authorization", "Bearer $access").build()

    private fun Response.readGraphJson(): JSONObject {
        if (!isSuccessful) throw IOException("OneDrive library request failed (HTTP $code)")
        return oneDriveJson()
    }

    private companion object {
        val DriveUrl = "https://graph.microsoft.com/v1.0/me/drive".toHttpUrl()
    }
}

internal class OneDrivePage(val items: List<JSONObject>, val next: String?)

internal fun oneDrivePageAddress(collection: HttpUrl, next: String): HttpUrl {
    val address = next.takeIf { it.length <= 16_384 }?.toHttpUrlOrNull()
    if (address == null || address.scheme != collection.scheme || address.host != collection.host ||
        address.port != collection.port || address.encodedPath != collection.encodedPath ||
        address.username.isNotEmpty() || address.password.isNotEmpty() || address.fragment != null
    ) throw IOException("Invalid OneDrive continuation address")
    return address
}

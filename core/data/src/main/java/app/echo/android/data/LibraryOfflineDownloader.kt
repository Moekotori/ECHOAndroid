package app.echo.android.data

import app.echo.android.model.library.LibraryOfflinePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class LibraryOfflineDownloadRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

class LibraryOfflineDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build(),
) {
    suspend fun download(
        request: LibraryOfflineDownloadRequest,
        destination: File,
        remainingQuotaBytes: Long,
        usableSpaceBytes: Long,
    ): Long = withContext(Dispatchers.IO) {
        require(request.url.startsWith("http://") || request.url.startsWith("https://"))
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.part")
        if (partial.exists()) partial.delete()
        val builder = Request.Builder().url(request.url).header("User-Agent", "ECHOAndroid-Offline")
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = requireNotNull(response.body)
            val declared = body.contentLength()
            if (declared > 0L) {
                check(declared <= LibraryOfflinePolicy.MaxFileBytes) { "file_too_large" }
                check(LibraryOfflinePolicy.quotaAllows(0, declared, remainingQuotaBytes)) { "quota" }
                check(declared + LibraryOfflinePolicy.MinFreeSpaceBytes <= usableSpaceBytes) { "disk" }
            }
            var total = 0L
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= LibraryOfflinePolicy.MaxFileBytes) { "file_too_large" }
                        check(LibraryOfflinePolicy.quotaAllows(0, total, remainingQuotaBytes)) { "quota" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(total > 0L) { "empty" }
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination) || (destination.exists() && destination.length() == total)) {
                "rename"
            }
            total
        }
    }
}

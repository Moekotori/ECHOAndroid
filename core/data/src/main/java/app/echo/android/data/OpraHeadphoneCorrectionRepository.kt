package app.echo.android.data

import android.content.Context
import app.echo.android.model.playback.OpraHeadphoneCorrectionProduct
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resumeWithException

class OpraHeadphoneCorrectionRepository(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    private val cacheFile = File(context.cacheDir, "opra/database_v1.jsonl")
    private val mutex = Mutex()
    private var cachedDatabase: OpraDatabase? = null
    private var cachedAt = 0L

    suspend fun search(query: String, refresh: Boolean = false, limit: Int = 16): Result<OpraSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val database = mutex.withLock { loadDatabase(refresh) }
                Result.success(OpraSearchResult(OpraDatabaseParser.search(database, query, limit), database.status))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    private fun parse(file: File, source: String): OpraDatabase =
        file.useLines { OpraDatabaseParser.parseLines(it, source) }

    private suspend fun loadDatabase(refresh: Boolean): OpraDatabase {
        val now = System.currentTimeMillis()
        cachedDatabase?.takeIf { !refresh && now - cachedAt < CacheLifetimeMs }?.let {
            return it.copy(status = it.status.copy(source = if (it.status.source == "cache-fallback") "cache-fallback" else "cache"))
        }
        val cached = cachedDatabase ?: cacheFile.takeIf { it.isFile && it.length() <= MaxDatabaseBytes }
            ?.let { runCatching { parse(it, "cache") }.getOrNull() }
        if (!refresh && cached != null && now - cacheFile.lastModified() < CacheLifetimeMs) {
            cachedDatabase = cached; cachedAt = cacheFile.lastModified()
            return cached.copy(status = cached.status.copy(source = "cache"))
        }
        cacheFile.parentFile?.mkdirs()
        var lastError: Exception? = null
        for (url in DatabaseUrls) {
            var temporary: File? = null
            try {
                temporary = download(url)
                val database = parse(temporary, "network")
                Files.move(temporary.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                cachedDatabase = database; cachedAt = System.currentTimeMillis()
                return database
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
            } finally {
                temporary?.delete()
            }
        }
        if (cached != null) {
            val fallback = cached.copy(status = cached.status.copy(source = "cache-fallback"))
            cachedDatabase = fallback
            cachedAt = now - CacheLifetimeMs + TimeUnit.MINUTES.toMillis(5)
            return fallback
        }
        throw lastError ?: IOException("opra_fetch_failed")
    }

    private suspend fun download(url: String): File = suspendCancellableCoroutine { continuation ->
        val temporary = File.createTempFile("opra-", ".jsonl", cacheFile.parentFile)
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "ECHOAndroid/1.0").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                temporary.delete()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw IOException("opra_fetch_failed_${it.code}")
                        val body = it.body ?: throw IOException("opra_empty_response")
                        if (body.contentLength() > MaxDatabaseBytes) throw IOException("opra_database_too_large")
                        body.byteStream().use { input -> temporary.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                val size = input.read(buffer)
                                if (size < 0) break
                                total += size
                                if (total > MaxDatabaseBytes) throw IOException("opra_database_too_large")
                                output.write(buffer, 0, size)
                            }
                            if (total == 0L) throw IOException("opra_empty_response")
                        } }
                    }
                    continuation.resume(temporary, onCancellation = { _, file, _ -> file.delete() })
                } catch (error: Exception) {
                    temporary.delete()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private companion object {
        const val MaxDatabaseBytes = 32L * 1024 * 1024
        val CacheLifetimeMs = TimeUnit.HOURS.toMillis(24)
        val DatabaseUrls = listOf("https://opra.roonlabs.net/database_v1.jsonl",
            "https://cdn.jsdelivr.net/gh/opra-project/OPRA@main/dist/database_v1.jsonl",
            "https://raw.githubusercontent.com/opra-project/OPRA/main/dist/database_v1.jsonl")
        fun defaultClient() = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    }
}

data class OpraSearchResult(val products: List<OpraHeadphoneCorrectionProduct>,
    val status: app.echo.android.model.playback.OpraDatabaseStatus)

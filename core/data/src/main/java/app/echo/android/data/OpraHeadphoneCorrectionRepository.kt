package app.echo.android.data

import android.content.Context
import app.echo.android.model.playback.OpraHeadphoneCorrectionProduct
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OpraHeadphoneCorrectionRepository(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    private val cacheFile = File(context.cacheDir, "opra/database_v1.jsonl")
    private var cachedDatabase: OpraDatabase? = null

    suspend fun search(
        query: String,
        refresh: Boolean = false,
        limit: Int = 16,
    ): Result<OpraSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val database = loadDatabase(refresh)
            val products = OpraDatabaseParser.search(database, query, limit)
            OpraSearchResult(products, database.status)
        }
    }

    private fun loadDatabase(refresh: Boolean): OpraDatabase {
        cachedDatabase?.takeIf { !refresh }?.let { return it }
        val cachedText = cacheFile.takeIf { it.isFile }?.readText()
        if (!refresh && cachedText != null) {
            return parseAndRemember(cachedText, "cache")
        }
        val fetched = runCatching { fetchDatabaseText() }
        val rawText = fetched.getOrElse { error ->
            if (!cachedText.isNullOrBlank()) {
                return parseAndRemember(cachedText, "cache")
            }
            throw error
        }
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(rawText)
        return parseAndRemember(rawText, "network")
    }

    private fun parseAndRemember(rawText: String, source: String): OpraDatabase =
        OpraDatabaseParser.parse(rawText, source).also { cachedDatabase = it }

    private fun fetchDatabaseText(): String {
        var lastError: Throwable? = null
        for (url in DatabaseUrls) {
            runCatching { fetchUrl(url) }
                .onSuccess { text ->
                    if (text.isNotBlank()) return text
                }
                .onFailure { error -> lastError = error }
        }
        error(lastError?.message ?: "opra_fetch_failed")
    }

    private fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("opra_fetch_failed_${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() } ?: error("opra_empty_response")
        }
    }

    private companion object {
        const val UserAgent = "ECHOAndroid/1.0 (Android)"
        val DatabaseUrls = listOf(
            "https://opra.roonlabs.net/database_v1.jsonl",
            "https://cdn.jsdelivr.net/gh/opra-project/OPRA@main/dist/database_v1.jsonl",
            "https://raw.githubusercontent.com/opra-project/OPRA/main/dist/database_v1.jsonl",
        )

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build()
    }
}

data class OpraSearchResult(
    val products: List<OpraHeadphoneCorrectionProduct>,
    val status: app.echo.android.model.playback.OpraDatabaseStatus,
)

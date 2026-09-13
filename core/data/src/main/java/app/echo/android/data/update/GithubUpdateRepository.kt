package app.echo.android.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class GithubUpdate(val versionCode: Long, val versionName: String, val notes: String,
    val url: String, val size: Long, val sha256: String)

/** Public releases only. Metadata and APK must belong to the Android repository. */
class GithubUpdateRepository(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "updates")
    private val prefs = context.applicationContext.getSharedPreferences("github_updates", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES)
        .followSslRedirects(false).build()

    @Volatile private var activeCall: Call? = null

    fun cancelPendingRequests() { activeCall?.cancel() }

    private suspend fun execute(call: Call): okhttp3.Response {
        activeCall = call
        currentCoroutineContext().ensureActive()
        return call.execute()
    }

    suspend fun check(currentCode: Long, manual: Boolean): GithubUpdate? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val elapsed = now - prefs.getLong("checked", 0)
        if (!manual && elapsed in 0 until TimeUnit.HOURS.toMillis(12)) return@withContext null
        // Persist attempts as well as successes, so offline launches do not repeatedly connect.
        prefs.edit().putLong("checked", now).apply()
        val releaseText = getText("https://api.github.com/repos/moekotori/echoandroid/releases/latest", true)
            ?: return@withContext null
        val release = JSONObject(releaseText)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
        val assets = release.getJSONArray("assets")
        val metadata = (0 until assets.length()).map { assets.getJSONObject(it) }
            .singleOrNull { it.optString("name") == "update.json" } ?: error("Missing update metadata")
        val metadataUrl = metadata.getString("browser_download_url")
        require(isReleaseAssetUrl(metadataUrl))
        val update = parseUpdate(getText(metadataUrl)!!, release.optString("body"))
        require((0 until assets.length()).any {
            val asset = assets.getJSONObject(it)
            asset.optString("browser_download_url") == update.url && asset.optLong("size") == update.size
        })
        update.takeIf { it.versionCode > currentCode }
    }

    private suspend fun getText(url: String, allowMissing: Boolean = false): String? {
        val request = Request.Builder().url(url).header("User-Agent", "ECHOAndroid-Updater").build()
        val call = client.newCall(request)
        call.timeout().timeout(30, TimeUnit.SECONDS)
        return execute(call).use { response ->
            if (allowMissing && response.code == 404) return@use null
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val source = requireNotNull(response.body).source()
            source.request(1_048_577)
            require(source.buffer.size <= 1_048_576) { "Metadata too large" }
            source.readUtf8()
        }
    }

    suspend fun download(update: GithubUpdate, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        require(isReleaseAssetUrl(update.url))
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        val partial = File(directory, "update.part")
        val target = File(directory, "update.apk")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val request = Request.Builder().url(update.url).build()
            execute(client.newCall(request)).use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                requireNotNull(response.body).byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var previous = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= update.size) { "APK too large" }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val percent = (total * 100 / update.size).toInt()
                            if (percent != previous) { progress(percent); previous = percent }
                        }
                        require(total == update.size) { "Incomplete APK" }
                    }
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == update.sha256) { "APK checksum mismatch" }
            check(partial.renameTo(target))
            target
        } finally { partial.delete() }
    }
}

internal fun isReleaseAssetUrl(url: String): Boolean =
    url.startsWith("https://github.com/moekotori/echoandroid/releases/download/") &&
        !url.contains("?") && !url.contains("#") && !url.contains("..")

internal fun parseUpdate(text: String, notes: String): GithubUpdate {
    val json = JSONObject(text)
    require(json.getInt("schemaVersion") == 1)
    return GithubUpdate(json.getLong("versionCode"), json.getString("versionName"), notes.take(16000),
        json.getString("apkUrl"), json.getLong("size"), json.getString("sha256").lowercase()).also {
        require(it.versionCode in 1..2_100_000_000 && it.versionName.isNotBlank())
        require(it.size in 1..536_870_912 && isReleaseAssetUrl(it.url) && it.url.endsWith(".apk"))
        require(it.sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

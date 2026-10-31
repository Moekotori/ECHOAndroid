package app.echo.android.data

import android.content.Context
import android.graphics.BitmapFactory
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** User-initiated lookups only. No polling or automatic replacement of artwork. */
class OnlineTrackArtworkRepository(
    context: Context,
    private val musicBrainzGate: MusicBrainzRequestGate,
    appVersion: String,
) {
    private val previewDirectory = File(context.cacheDir, "online-cover-preview")
    private val artworkStore = LocalArtworkStore(File(context.filesDir, "online-covers"))
    private val dao = EchoLibraryDatabase.create(context.applicationContext).trackDao()
    private val mutex = Mutex()
    private val userAgent = "ECHOAndroid/$appVersion (https://github.com/moekotori/echo)"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).build()

    suspend fun search(track: EchoTrack): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val title = track.album?.takeIf(String::isNotBlank) ?: return@withLock null
            val album = AlbumSummary("", title, track.albumArtist, track.artist, null, 0, 0, track.year)
            val url = "https://musicbrainz.org/ws/2/release/".toHttpUrl().newBuilder()
                .addQueryParameter("fmt", "json").addQueryParameter("limit", "25")
                .addQueryParameter("query", AlbumOnlineInfoParser.query(album)).build()
            val payload = musicBrainzGate.run { download(url.toString(), 512_000, false) }
                ?: return@withLock null
            val id = AlbumOnlineInfoParser.selectRelease(JSONObject(payload.toString(Charsets.UTF_8)), album)
                ?: return@withLock null
            // https://musicbrainz.org/doc/Cover_Art_Archive/API
            val image = download("https://coverartarchive.org/release/$id/front-500", MaxCoverBytes, true)
                ?: return@withLock null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
            check(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096) { "Invalid cover image" }
            check(previewDirectory.isDirectory || previewDirectory.mkdirs())
            previewDirectory.listFiles().orEmpty().sortedByDescending(File::lastModified).drop(3).forEach { it.delete() }
            File(previewDirectory, "$id.jpg").apply { writeBytes(image) }.toURI().toASCIIString()
        }
    }

    suspend fun save(previewUri: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(java.net.URI(previewUri)).canonicalFile
            check(file.parentFile == previewDirectory.canonicalFile && file.length() in 1..MaxCoverBytes.toLong())
            artworkStore.prune(dao.getLocalFileArtworkUris().toSet())
            checkNotNull(artworkStore.store(file.readBytes())) { "Unable to save cover" }
        }
    }

    private suspend fun download(url: String, limit: Int, image: Boolean): ByteArray? {
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (it.code == 404) return@use null
                            check(it.isSuccessful) { "Artwork HTTP ${it.code}" }
                            val body = checkNotNull(it.body)
                            check(body.contentLength() <= limit)
                            if (image) check(body.contentType()?.type == "image")
                            body.byteStream().use { stream ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    check(output.size() + count <= limit) { "Artwork response too large" }
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }
    }

    private companion object { const val MaxCoverBytes = 4 * 1024 * 1024 }
}

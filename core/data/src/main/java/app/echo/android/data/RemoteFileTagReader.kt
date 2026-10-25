package app.echo.android.data

import app.echo.android.smb.EchoSmbFileReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Per-scan owner; uses existing authentication and closes each range/file promptly. */
internal class RemoteFileTagReader(
    private val webDavEndpoint: WebDavEndpoint? = null,
    client: OkHttpClient? = null,
) {
    private val http = client?.newBuilder()?.callTimeout(5, TimeUnit.SECONDS)?.readTimeout(5, TimeUnit.SECONDS)?.build()

    fun read(track: LibraryTrackEntity, checkActive: () -> Unit): AudioTagFields? {
        if (track.contentUri.startsWith("smb://")) {
            EchoSmbFileReader.open(track.contentUri).use { file ->
                val input = RandomAccessTagInput(file.length, checkActive) { position, count ->
                    val bytes = ByteArray(count)
                    val read = file.read(position, bytes, 0, count)
                    if (read <= 0) throw IOException("SMB embedded tag range unavailable")
                    if (read == count) bytes else bytes.copyOf(read)
                }
                return readLocalAudioTags(input)
            }
        }
        val endpoint = webDavEndpoint ?: return null
        val client = http ?: return null
        val base = endpoint.normalizedBaseUrl.toHttpUrl()
        val address = track.contentUri.toHttpUrl()
        if (base.scheme != address.scheme || base.host != address.host || base.port != address.port) {
            throw IOException("WebDAV embedded tag URL is outside the authenticated origin")
        }
        if (track.sizeBytes <= 0) return null
        val input = RandomAccessTagInput(track.sizeBytes, checkActive) { position, count ->
            val end = position + count - 1
            val request = Request.Builder().url(track.contentUri)
                .apply { endpoint.authorizationHeader()?.let { header("Authorization", it) } }
                .header("Range", "bytes=$position-$end")
                .header("Accept-Encoding", "identity").build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: throw IOException("Empty WebDAV embedded tag response")
                if (response.code == 206) {
                    val range = response.header("Content-Range").orEmpty()
                    if (!range.startsWith("bytes $position-")) throw IOException("Unexpected WebDAV tag range")
                } else if (response.code != 200 || position != 0L) {
                    throw IOException("WebDAV server did not provide requested tag range")
                }
                // Even servers ignoring Range cannot make us download the entire file.
                val bytes = ByteArray(count)
                var read = 0
                body.byteStream().use { stream ->
                    while (read < count) {
                        checkActive()
                        val next = stream.read(bytes, read, count - read)
                        if (next < 0) break
                        if (next == 0) continue
                        read += next
                    }
                }
                if (read <= 0) throw IOException("Empty WebDAV tag range")
                if (read == count) bytes else bytes.copyOf(read)
            }
        }
        return readLocalAudioTags(input)
    }
}

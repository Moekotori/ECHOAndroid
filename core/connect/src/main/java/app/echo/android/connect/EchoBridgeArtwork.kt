package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import java.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import kotlinx.coroutines.sync.withPermit

data class EchoBridgeImage(val bytes: ByteArray, val mimeType: String)

/** Peer artwork remains a small URL in lists; only an image consumer requests its bytes. */
object EchoBridgeArtwork {
    private val transport by lazy { OkHttpEchoLinkTransport() }
    private const val Path = "/echo-link/peer/v1/thumb"
    private val PngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

    fun url(endpoint: EchoRemoteEndpoint, coverId: String?): String? = coverId?.takeIf(String::isNotBlank)?.let {
        HttpUrl.Builder().scheme(endpoint.scheme).host(endpoint.host).port(endpoint.port)
            .addPathSegments(Path.removePrefix("/")).addQueryParameter("id", it).build().toString()
    }

    fun handles(url: String): Boolean = url.toHttpUrlOrNull()?.let {
        it.encodedPath == Path && !it.queryParameter("id").isNullOrBlank() &&
            it.username.isEmpty() && it.password.isEmpty()
    } == true

    suspend fun read(url: String, pixels: Int): EchoBridgeImage? {
        require(handles(url))
        val request = url.toHttpUrlOrNull()!!.newBuilder()
            .setQueryParameter("size", if (pixels > 320) "512" else "320").build()
        return EchoBridgeTransport.ReadSlots.withPermit { decode(transport.fetchBridgeArtwork(request)) }
    }

    internal fun decode(response: JSONObject): EchoBridgeImage? {
        val image = response.optJSONObject("image") ?: return null
        val mime = image.getString("mime")
        val encoded = image.getString("bytes")
        require(encoded.length <= 350_000) { "Bridge artwork exceeds the transfer limit" }
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size <= 256 * 1024) { "Bridge artwork exceeds the image limit" }
        val raster = when (mime) {
            "image/png" -> bytes.size >= 8 && PngSignature.indices.all { bytes[it] == PngSignature[it] }
            "image/jpeg" -> bytes.size >= 3 && bytes[0] == 255.toByte() && bytes[1] == 216.toByte() && bytes[2] == 255.toByte()
            "image/webp" -> bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
            else -> false
        }
        require(raster) { "Bridge artwork must be a supported raster image" }
        return EchoBridgeImage(bytes, mime)
    }
}

package app.echo.android.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal class EmbeddedArtworkEncodingException(cause: Throwable? = null) :
    Exception("Unable to encode the requested artwork", cause)

internal object EmbeddedArtworkEncoder {
    data class Encoded(val bytes: ByteArray, val mime: String)

    fun encode(resolver: ContentResolver, uri: Uri): Encoded? {
        val original = resolver.openInputStream(uri)?.use { readArtworkSource(it, MAX_SOURCE_BYTES) } ?: return null
        if (original.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val mime = bounds.outMimeType?.takeIf { it.startsWith("image/") } ?: return null
        val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            var sample = 1
            while (maxEdge / sample > MAX_EDGE) sample *= 2
            inSampleSize = sample
        }
        var bitmap = BitmapFactory.decodeByteArray(original, 0, original.size, options) ?: return null
        return try {
            if (original.size <= MAX_BYTES && maxEdge <= MAX_EDGE) return Encoded(original, mime)
            // Each attempt is a complete JPEG. Never truncate an encoded image to fit the limit.
            repeat(6) {
                for (quality in 85 downTo 55 step 10) {
                    val out = ByteArrayOutputStream()
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
                    val bytes = out.toByteArray()
                    if (bytes.size <= MAX_BYTES) return Encoded(bytes, "image/jpeg")
                }
                val smaller = Bitmap.createScaledBitmap(bitmap,
                    (bitmap.width * 3 / 4).coerceAtLeast(1), (bitmap.height * 3 / 4).coerceAtLeast(1), true)
                if (smaller === bitmap) return null
                bitmap.recycle()
                bitmap = smaller
            }
            null
        } finally {
            bitmap.recycle()
        }
    }

    private const val MAX_EDGE = 1_200
    private const val MAX_BYTES = 512 * 1024
    private const val MAX_SOURCE_BYTES = 8 * 1024 * 1024
}

/** Read at most the budget plus one byte, even when the provider reports no size. */
internal fun readArtworkSource(input: InputStream, maxBytes: Int): ByteArray? {
    require(maxBytes > 0 && maxBytes < Int.MAX_VALUE)
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total + 1))
        if (count < 0) return output.toByteArray()
        if (count == 0) continue
        total += count
        if (total > maxBytes) return null
        output.write(buffer, 0, count)
    }
}

package app.echo.android.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

internal object EmbeddedArtworkEncoder {
    data class Encoded(
        val bytes: ByteArray,
        val mime: String,
    )

    fun encode(resolver: ContentResolver, uri: Uri): Encoded? {
        val original = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (original.isEmpty() || original.size > MAX_SOURCE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        val sniffed = bounds.outMimeType?.takeIf { it.startsWith("image/") } ?: sniffMime(original)
        val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (original.size <= MAX_BYTES && maxEdge in 1..MAX_EDGE && sniffed != null) {
            return Encoded(original, sniffed)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(maxEdge, MAX_EDGE)
        }
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size, options)
            ?: return sniffed?.let { Encoded(original.copyOf(minOf(original.size, MAX_BYTES)), it) }
        return try {
            var quality = 85
            var encoded: ByteArray
            do {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                encoded = out.toByteArray()
                quality -= 10
            } while (encoded.size > MAX_BYTES && quality >= 50)
            Encoded(encoded.take(MAX_BYTES).toByteArray(), "image/jpeg")
        } finally {
            bitmap.recycle()
        }
    }

    private fun sampleSize(longestEdge: Int, target: Int): Int {
        if (longestEdge <= 0 || longestEdge <= target) return 1
        var sample = 1
        while (longestEdge / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun sniffMime(bytes: ByteArray): String? = when {
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte() -> "image/png"
        else -> null
    }

    private const val MAX_EDGE = 1_200
    private const val MAX_BYTES = 512 * 1024
    private const val MAX_SOURCE_BYTES = 8 * 1024 * 1024
}

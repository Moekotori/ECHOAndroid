package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import java.io.InputStream
import java.io.IOException

/** Metadata only; bounded allocation and traversal, never reads PCM or opens per movement. */
internal object FlacEmbeddedCueReader {
    private const val MaxMetadataBytes = 32 * 1024 * 1024

    fun read(input: InputStream): CueSheet? = try { readChecked(input) } catch (_: IOException) { null }

    /** Only a complete, valid metadata traversal can confirm that an embedded CUE is absent. */
    fun readChecked(input: InputStream): CueSheet? {
        var consumed = 0
        val scratch = ByteArray(8192)
        fun bytes(size: Int): ByteArray? {
            if (size < 0 || size > MaxMetadataBytes - consumed) invalid()
            val result = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val count = input.read(result, offset, size - offset)
                if (count <= 0) invalid()
                offset += count
            }
            consumed += size
            return result
        }
        fun skip(size: Int): Boolean {
            if (size < 0 || size > MaxMetadataBytes - consumed) return false
            var remaining = size
            while (remaining > 0) {
                val skipped = input.skip(remaining.toLong()).coerceAtMost(remaining.toLong()).toInt()
                val count = if (skipped > 0) skipped else input.read(scratch, 0, minOf(scratch.size, remaining))
                if (count <= 0) return false
                remaining -= count
            }
            consumed += size
            return true
        }
        var marker = bytes(4) ?: invalid()
        if (marker.take(3).toByteArray().contentEquals(byteArrayOf(73, 68, 51))) {
            val tail = bytes(6) ?: invalid()
            if ((2..5).any { tail[it].toInt() and 128 != 0 }) invalid()
            val size = (2..5).fold(0) { value, i -> (value shl 7) or (tail[i].toInt() and 127) }
            if (!skip(size + if (tail[1].toInt() and 16 != 0) 10 else 0)) invalid()
            marker = bytes(4) ?: invalid()
        }
        if (!marker.contentEquals(byteArrayOf(102, 76, 97, 67))) invalid()
        var rate = 0
        var samples = 0L
        var text: String? = null
        var binary: ByteArray? = null
        repeat(256) { block ->
            val header = bytes(4) ?: invalid()
            val type = header[0].toInt() and 127
            val size = (1..3).fold(0) { value, i -> (value shl 8) or (header[i].toInt() and 255) }
            if (block == 0 && type != 0) invalid()
            when (type) {
                0 -> {
                    if (size != 34 || block != 0) invalid()
                    val info = bytes(size) ?: invalid()
                    val packed = (10..17).fold(0L) { value, i -> (value shl 8) or (info[i].toLong() and 255) }
                    rate = (packed ushr 44).toInt()
                    samples = packed and 0xfffffffffL
                    if (rate <= 0) invalid()
                }
                4 -> {
                    commentCue(size, ::bytes, ::skip)?.let { next ->
                        if (text != null && text != next) invalid()
                        text = next
                    }
                }
                5 -> {
                    if (size > CueSheetPolicy.MaxCueBytes || binary != null) invalid()
                    binary = bytes(size) ?: invalid()
                }
                else -> if (!skip(size)) invalid()
            }
            if (header[0].toInt() and 128 != 0) {
                val cueText = text
                val sheet = if (cueText != null) {
                    CueSheetParser.parseText(cueText)?.let { parsed ->
                        // FILE labels in embedded text refer to this physical FLAC, never another file.
                        parsed.copy(fileName = null, tracks = parsed.tracks.map { it.copy(fileName = null) })
                    }
                } else binary?.let { FlacBinaryCueReader.parse(it, rate, samples) }
                val duration = if (samples > 0) samples * 1000 / rate else 0L
                if (sheet == null) {
                    if (text == null && binary == null) return null
                    invalid()
                }
                return sheet.let {
                    val ended = CueSheetPolicy.withEndTimes(it.tracks, duration)
                    if (ended.isEmpty()) invalid() else it.copy(tracks = ended)
                }
            }
        }
        invalid()
    }

    private fun invalid(): Nothing = throw IOException("Incomplete, invalid or oversized FLAC CUE metadata")

    private fun commentCue(size: Int, read: (Int) -> ByteArray?, skip: (Int) -> Boolean): String? {
        var remaining = size
        fun take(count: Int): ByteArray {
            if (count < 0 || count > remaining) invalid()
            remaining -= count
            return read(count) ?: invalid()
        }
        fun discard(count: Int) {
            if (count < 0 || count > remaining || !skip(count)) invalid()
            remaining -= count
        }
        fun number(): Int {
            val bytes = take(4)
            val value = (0..3).fold(0L) { value, i -> value or ((bytes[i].toLong() and 255) shl (8 * i)) }
            if (value > Int.MAX_VALUE) invalid()
            return value.toInt()
        }
        discard(number()) // Vendor text provides no CUE facts.
        val count = number()
        if (count > 4096) invalid()
        var cue: String? = null
        repeat(count) {
            val length = number()
            if (length > remaining) invalid()
            // Inspect only the key prefix. Large artwork/comments are skipped without allocation.
            val prefix = take(minOf(length, 32))
            val keyEnd = prefix.indexOf(61.toByte())
            val key = if (keyEnd < 0) "" else String(prefix, 0, keyEnd, Charsets.US_ASCII).filter(Char::isLetterOrDigit)
            if (key.equals("CUESHEET", true)) {
                val valueSize = length - keyEnd - 1
                if (valueSize > CueSheetPolicy.MaxCueBytes) invalid()
                val payload = prefix.copyOfRange(keyEnd + 1, prefix.size) + take(length - prefix.size)
                val value = try {
                    Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(payload)).toString()
                } catch (_: java.nio.charset.CharacterCodingException) { invalid() }
                if (value.isNotBlank()) {
                    if (cue != null && cue != value) invalid()
                    cue = value
                }
            } else discard(length - prefix.size)
        }
        if (remaining != 0) invalid()
        return cue
    }
}

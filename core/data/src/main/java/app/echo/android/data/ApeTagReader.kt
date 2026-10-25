package app.echo.android.data

import java.io.InputStream
import java.io.IOException
import java.util.Locale

/** APEv2 lives at the tail. Never walk audio data on a non-seekable provider. */
internal object ApeTagReader {
    fun read(input: InputStream): AudioTagFields? = readFields(input, false, null)

    fun readCueChecked(input: InputStream): app.echo.android.model.library.CueSheet? {
        if (input !is TagTailReader && input !is java.io.FileInputStream)
            throw IOException("Embedded APEv2 CUE requires a seekable source")
        var cue: String? = null
        readFields(input, true) { cue = it }
        val text = cue?.takeIf { it.isNotBlank() } ?: return null
        val sheet = CueSheetParser.parseText(text) ?: throw IOException("Invalid embedded APEv2 CUE")
        return sheet.copy(fileName = null, tracks = sheet.tracks.map { it.copy(fileName = null) })
    }

    private fun readFields(input: InputStream, checked: Boolean, onCue: ((String) -> Unit)?): AudioTagFields? {
        fun invalid(): AudioTagFields? {
            if (checked) throw IOException("Invalid APEv2 metadata")
            return null
        }
        var suffix = 0
        var footer = tail(input, 32) ?: return null
        if (footer.decodeToString(0, 8) != "APETAGEX") {
            val end = tail(input, 160) ?: return null
            if (end.decodeToString(32, 35) != "TAG") return null
            suffix = 128
            footer = end.copyOfRange(0, 32)
        }
        if (footer.decodeToString(0, 8) != "APETAGEX") return null
        val version = le32(footer, 8)
        val size = le32(footer, 12)
        val count = le32(footer, 16)
        // Bit 29 marks a header rather than a footer.
        if (version !in listOf(1000L, 2000L) || le32(footer, 20) and 0x20000000L != 0L ||
            size !in 32L..(MaxEmbeddedTextBytes + MaxEmbeddedArtworkBytes).toLong() || count !in 0L..1024L) return invalid()
        val bytes = tail(input, size.toInt() + suffix) ?: return invalid()
        val limit = size.toInt() - 32
        val values = linkedMapOf<String, String>()
        var artwork: ByteArray? = null
        var artworkMime: String? = null
        var offset = 0
        repeat(count.toInt()) {
            if (offset > limit - 8) return invalid()
            val length = le32(bytes, offset)
            val kind = (le32(bytes, offset + 4) shr 1) and 3
            offset += 8
            val keyStart = offset
            while (offset < limit && offset - keyStart <= 255 && bytes[offset] != 0.toByte()) offset++
            if (offset >= limit || offset - keyStart !in 1..255 || length > limit - offset - 1) return invalid()
            val key = bytes.decodeToString(keyStart, offset).lowercase(Locale.ROOT)
            offset++
            val end = offset + length.toInt()
            if (kind == 0L && key == "cuesheet" && onCue != null) {
                if (length > app.echo.android.model.library.CueSheetPolicy.MaxCueBytes) return invalid()
                val text = try {
                    Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes, offset, length.toInt())).toString()
                } catch (_: java.nio.charset.CharacterCodingException) { return invalid() }
                val previous = values[key]
                if (previous != null && previous != text) return invalid()
                values[key] = text
            }
            if (kind == 0L && length <= MaxEmbeddedTextBytes) {
                values.putIfAbsent(key, bytes.decodeToString(offset, end).replace('\u0000', ';').trim())
            } else if (kind == 1L && key == "cover art (front)" && length <= MaxEmbeddedArtworkBytes) {
                var start = offset
                while (start < end && bytes[start] != 0.toByte()) start++
                if (start + 1 < end) {
                    val description = bytes.decodeToString(offset, start).lowercase(Locale.ROOT)
                    artwork = bytes.copyOfRange(start + 1, end)
                    artworkMime = if (description.endsWith("png")) "image/png" else "image/jpeg"
                }
            }
            offset = end
        }
        if (checked && offset != limit) return invalid()
        values["cuesheet"]?.let { onCue?.invoke(it) }
        fun text(vararg keys: String): String? = keys.firstNotNullOfOrNull { values[it]?.takeIf(String::isNotBlank) }
        fun number(vararg keys: String): Int? = text(*keys)?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        return AudioTagFields(
            title = text("title").orEmpty(), artist = text("artist").orEmpty(), album = text("album"),
            albumArtist = text("album artist", "albumartist"), trackNumber = number("track", "tracknumber"),
            discNumber = number("disc", "discnumber"), year = text("year", "date")?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
            genre = text("genre"), composer = text("composer"), lyrics = text("lyrics", "unsyncedlyrics"),
            artworkBytes = artwork, artworkMime = artworkMime,
            replayGainTrackGainDb = text("replaygain_track_gain")?.substringBefore(' ')?.toFloatOrNull()?.takeIf { it.isFinite() },
        ).takeUnless { it.isBlank() }
    }

    private fun tail(input: InputStream, count: Int): ByteArray? = readTagTail(input, count)

    private fun le32(bytes: ByteArray, at: Int): Long =
        (0..3).fold(0L) { value, index -> value or ((bytes[at + index].toLong() and 255) shl (index * 8)) }
}

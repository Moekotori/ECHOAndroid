package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.Locale

/** Preserve vendor, unknown comments and optional values that were not edited. */
internal object VorbisCommentRewriter {
    fun rewrite(payload: ByteArray, fields: AudioTagFields): ByteArray? {
        val original = VorbisTagReader.commentsForRewrite(payload) ?: return null
        fun old(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
            original.firstOrNull { it.first.equals(key, true) && it.second.isNotBlank() }?.second
        }
        val replaceComposer = shouldReplaceOptionalTextTag(fields.composer, fields.composerModified, old("COMPOSER"))
        val replaceGenre = shouldReplaceOptionalTextTag(fields.genre, fields.genreModified, old("GENRE"))
        val comments = original.filterNot { (key, value) ->
            val upper = key.uppercase(Locale.ROOT)
            upper in Managed || (replaceComposer && upper == "COMPOSER") || (replaceGenre && upper == "GENRE") ||
                (fields.lyrics != null && upper in Lyrics) ||
                (fields.replayGainTrackGainDb != null && upper == "REPLAYGAIN_TRACK_GAIN") ||
                (fields.artworkBytes != null && upper == "METADATA_BLOCK_PICTURE" && isFrontPicture(value))
        }.toMutableList()
        comments += "TITLE" to fields.title
        comments += "ARTIST" to fields.artist
        fields.album?.let { comments += "ALBUM" to it }
        fields.albumArtist?.let { comments += "ALBUMARTIST" to it }
        if (replaceComposer) fields.composer?.takeIf { it.isNotBlank() }?.let { comments += "COMPOSER" to it }
        if (replaceGenre) fields.genre?.takeIf { it.isNotBlank() }?.let { comments += "GENRE" to it }
        tagNumberWithTotal(fields.trackNumber, old("TRACKNUMBER", "TRACK"))?.let { comments += "TRACKNUMBER" to it }
        tagNumberWithTotal(fields.discNumber, old("DISCNUMBER", "DISC"))?.let { comments += "DISCNUMBER" to it }
        tagDateWithYear(fields.year, old("DATE", "YEAR"))?.let { comments += "DATE" to it }
        fields.lyrics?.takeIf { it.isNotBlank() }?.let { comments += "LYRICS" to it }
        fields.replayGainTrackGainDb?.let { comments += "REPLAYGAIN_TRACK_GAIN" to String.format(Locale.ROOT, "%.2f dB", it) }
        fields.artworkBytes?.takeIf { it.isNotEmpty() }?.let { image ->
            if (image.size > MaxEmbeddedArtworkBytes) return null
            val picture = ByteArrayOutputStream()
            DataOutputStream(picture).apply {
                writeInt(3)
                val mime = fields.artworkMime.orEmpty().ifBlank { "image/jpeg" }.toByteArray(Charsets.US_ASCII)
                writeInt(mime.size); write(mime); writeInt(0)
                repeat(4) { writeInt(0) } // Dimensions and palette may be unspecified.
                writeInt(image.size); write(image)
            }
            comments += "METADATA_BLOCK_PICTURE" to Base64.getEncoder().encodeToString(picture.toByteArray())
        }
        val vendorSize = (0..3).fold(0) { n, i -> n or ((payload[i].toInt() and 255) shl (i * 8)) }
        val output = ByteArrayOutputStream()
        output.write(payload, 0, vendorSize + 4)
        fun le(value: Int) { repeat(4) { output.write(value ushr (it * 8) and 255) } }
        le(comments.size)
        for ((key, value) in comments) {
            val bytes = "$key=$value".toByteArray(Charsets.UTF_8)
            if (output.size().toLong() + bytes.size + 4 > MaxBytes) return null
            le(bytes.size); output.write(bytes)
        }
        return output.toByteArray()
    }

    private fun isFrontPicture(value: String): Boolean = try {
        // Decode only the type, avoiding another copy of the entire cover.
        val prefix = Base64.getDecoder().decode(value.take(8))
        prefix.size >= 4 && prefix.tagU32(0) == 3L
    } catch (_: IllegalArgumentException) { false }

    const val MaxBytes = 12 * 1024 * 1024
    private val Lyrics = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")
    private val Managed = setOf("TITLE", "ARTIST", "ALBUM", "ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST", "TRACKNUMBER", "TRACK", "DISCNUMBER", "DISC", "DATE", "YEAR")
}

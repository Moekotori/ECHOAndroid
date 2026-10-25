package app.echo.android.data

import java.util.Base64
import java.util.Locale

/** Shared FLAC, Vorbis and Opus comment decoding; counts and payloads are bounded. */
internal object VorbisTagReader {
    fun comments(payload: ByteArray): List<Pair<String, String>> = readComments(payload, complete = false).orEmpty()

    /** Writers must distinguish a complete tag from a truncated or unsupported read. */
    fun commentsForRewrite(payload: ByteArray): List<Pair<String, String>>? = readComments(payload, complete = true)

    private fun readComments(payload: ByteArray, complete: Boolean): List<Pair<String, String>>? {
        fun number(offset: Int): Long? = if (offset < 0 || offset + 4 > payload.size) null else
            (0..3).fold(0L) { value, index -> value or ((payload[offset + index].toLong() and 255) shl (index * 8)) }
        val vendorSize = number(0) ?: return null
        if (vendorSize > payload.size - 4) return null
        var cursor = 4 + vendorSize.toInt()
        val count = number(cursor) ?: return null
        if (complete && count > 4096) return null
        cursor += 4
        val comments = ArrayList<Pair<String, String>>()
        repeat(minOf(count, 4096).toInt()) {
            val size = number(cursor) ?: return if (complete) null else comments
            cursor += 4
            if (size > payload.size - cursor) return if (complete) null else comments
            val bytes = payload.copyOfRange(cursor, cursor + size.toInt())
            val comment = TagTextDecoder.decodeDeclared(bytes, Charsets.UTF_8)
            cursor += size.toInt()
            if (comment != null && '=' in comment && comment.substringBefore('=').isNotBlank()) {
                val key = comment.substringBefore('=')
                comments += key to comment.substringAfter('=')
            } else if (complete) return null
        }
        if (complete && cursor != payload.size) return null
        return comments
    }

    fun fields(payload: ByteArray): AudioTagFields {
        val comments = comments(payload)
        val values = HashMap<String, String>()
        comments.forEach { (key, value) -> if (value.isNotBlank()) values.putIfAbsent(key.uppercase(Locale.ROOT), value) }
        fun names(vararg keys: String): String? = LibraryArtistPolicy.display(comments
            .filter { (key, _) -> key.uppercase(Locale.ROOT) in keys }.map { it.second })
        var artwork: TagArtwork? = null
        for ((key, value) in comments) {
            if (!key.equals("METADATA_BLOCK_PICTURE", ignoreCase = true) || value.length > MaxEncodedPictureChars) continue
            val picture = runCatching { EmbeddedTagArtwork.flac(Base64.getDecoder().decode(value)) }.getOrNull() ?: continue
            if (artwork == null || picture.type == 3) artwork = picture
            if (picture.type == 3) break
        }
        return AudioTagFields(
            title = values["TITLE"].orEmpty(), artist = names("ARTIST").orEmpty(), album = values["ALBUM"],
            albumArtist = names("ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST"),
            composer = values["COMPOSER"], genre = values["GENRE"],
            trackNumber = (values["TRACKNUMBER"] ?: values["TRACK"])?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 },
            discNumber = (values["DISCNUMBER"] ?: values["DISC"])?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 },
            year = (values["DATE"] ?: values["YEAR"])?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
            lyrics = values["LYRICS"] ?: values["UNSYNCEDLYRICS"] ?: values["SYNCEDLYRICS"],
            artworkBytes = artwork?.bytes, artworkMime = artwork?.mime,
            replayGainTrackGainDb = values["REPLAYGAIN_TRACK_GAIN"]?.substringBefore(' ')?.toFloatOrNull()?.takeIf { it.isFinite() },
        )
    }

    private const val MaxEncodedPictureChars = (MaxEmbeddedArtworkBytes + 4096) * 4 / 3 + 4
}

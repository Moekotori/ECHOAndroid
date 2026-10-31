package app.echo.android.data

import java.io.InputStream

/** iTunes ilst metadata. Traverse atom headers and skip mdat instead of loading audio. */
internal object Mp4TagReader {
    fun read(input: InputStream): AudioTagFields? {
        val state = State()
        state.walk(input, 0)
        return state.fields?.let { it.copy(musicRelations = musicRelationTags(state.relationFields).withFallback(it.musicRelations)) }
            ?.takeUnless { it.isBlank() }
    }

    private class State {
        var fields: AudioTagFields? = null
        val relationFields = mutableListOf<Pair<String, String>>()
        private var atoms = 0
        private var retainedBytes = 0

        fun walk(input: InputStream, depth: Int) {
            if (depth > 8) return
            while (atoms++ < 4096) {
                val header = input.tagBytes(8) ?: return
                val type = header.copyOfRange(4, 8).toString(Charsets.ISO_8859_1)
                val shortSize = header.tagU32(0)
                val size = if (shortSize == 1L) {
                    val extended = input.tagBytes(8) ?: return
                    val high = extended.tagU32(0)
                    if (high > 0x7FFFFFFF) return
                    (high shl 32) or extended.tagU32(4)
                } else shortSize
                val headerSize = if (shortSize == 1L) 16 else 8
                val bodySize = if (size == 0L && input is TagSliceInput) input.remaining else size - headerSize
                if (bodySize < 0 || (input is TagSliceInput && bodySize > input.remaining)) return
                val body = TagSliceInput(input, bodySize)
                when (type) {
                    "moov", "udta" -> walk(body, depth + 1)
                    "meta" -> if (body.skipTagBytes(4)) walk(body, depth + 1)
                    "ilst" -> readItems(body)
                }
                if (!body.skipTagBytes(body.remaining)) return
                if (size == 0L) return
            }
        }

        private fun readItems(input: TagSliceInput) {
            while (input.remaining >= 8 && atoms++ < 4096) {
                val header = input.tagBytes(8) ?: return
                val size = header.tagU32(0) - 8
                if (size < 0 || size > input.remaining) return
                val key = header.copyOfRange(4, 8).toString(Charsets.ISO_8859_1)
                val item = TagSliceInput(input, size)
                if (key in Keys || key == "----") readItem(key, item)
                if (!item.skipTagBytes(item.remaining)) return
            }
        }

        private fun readItem(key: String, input: TagSliceInput) {
            var freeName: String? = null
            var freeMean: String? = null
            var freeValue: String? = null
            while (input.remaining >= 8 && atoms++ < 4096) {
                val header = input.tagBytes(8) ?: return
                val size = header.tagU32(0) - 8
                if (size < 0 || size > input.remaining) return
                val type = header.copyOfRange(4, 8).toString(Charsets.ISO_8859_1)
                val body = TagSliceInput(input, size)
                if (type == "data" && size >= 8) {
                    val dataHeader = body.tagBytes(8) ?: return
                    val dataType = dataHeader.tagU32(0).toInt() and 0xFFFFFF
                    val cap = if (key == "covr") MaxEmbeddedArtworkBytes else MaxEmbeddedTextBytes
                    if (body.remaining <= cap && retainedBytes + body.remaining <= MaxRetainedBytes) {
                        val value = body.tagBytes(body.remaining.toInt()) ?: return
                        retainedBytes += value.size
                        if (key == "----") freeValue = decode(value, dataType) else apply(key, value, dataType)
                    }
                } else if (key == "----" && type in setOf("name", "mean") && size in 4..1024) {
                    if (!body.skipTagBytes(4)) return
                    val value = body.tagBytes(body.remaining.toInt())?.toString(Charsets.UTF_8)
                    if (type == "name") freeName = value else freeMean = value
                }
                if (!body.skipTagBytes(body.remaining)) return
            }
            if (freeMean == "com.apple.iTunes" && freeName != null && freeValue != null) {
                val name = freeName.uppercase(java.util.Locale.ROOT)
                if (isMusicRelationTag(name) && relationFields.size < 128) relationFields += name to freeValue.take(2000)
                val old = fields ?: Empty
                fields = when (name) {
                    "LYRICS", "UNSYNCEDLYRICS" -> old.copy(lyrics = old.lyrics ?: freeValue)
                    "REPLAYGAIN_TRACK_GAIN" -> old.copy(replayGainTrackGainDb = freeValue.substringBefore(' ').toFloatOrNull()?.takeIf { it.isFinite() })
                    else -> old.copy(musicRelations = musicRelationTags(listOf(name to freeValue)).withFallback(old.musicRelations))
                }
            }
        }

        private fun apply(key: String, value: ByteArray, type: Int) {
            val old = fields ?: Empty
            val text = decode(value, type)
            val relationKey = when (key) { "©ART" -> "ARTIST"; "©wrt" -> "COMPOSER"; "©wrk" -> "WORK"; "©mvn" -> "MOVEMENT"; else -> null }
            if (relationKey != null && text != null && relationFields.size < 128) relationFields += relationKey to text.take(2000)
            fields = when (key) {
                "©nam" -> old.copy(title = old.title.ifBlank { text.orEmpty() })
                "©ART" -> old.copy(artist = LibraryArtistPolicy.display(listOfNotNull(old.artist.takeIf { it.isNotBlank() }, text)).orEmpty())
                "©alb" -> old.copy(album = old.album ?: text)
                "aART" -> old.copy(albumArtist = LibraryArtistPolicy.display(listOfNotNull(old.albumArtist, text)))
                "©wrt" -> old.copy(composer = old.composer ?: text)
                "©wrk" -> old.copy(musicRelations = old.musicRelations.copy(work = old.musicRelations.work ?: text))
                "©mvn" -> old.copy(musicRelations = old.musicRelations.copy(movement = old.musicRelations.movement ?: text))
                "©gen" -> old.copy(genre = old.genre ?: text)
                "©lyr" -> old.copy(lyrics = old.lyrics ?: text)
                "©day" -> old.copy(year = old.year ?: text?.take(4)?.toIntOrNull()?.takeIf { it > 0 })
                "trkn" -> old.copy(trackNumber = old.trackNumber ?: number(value))
                "disk" -> old.copy(discNumber = old.discNumber ?: number(value))
                "covr" -> if (value.isNotEmpty() && type in setOf(13, 14) && old.artworkBytes == null)
                    old.copy(artworkBytes = value, artworkMime = if (type == 14) "image/png" else "image/jpeg") else old
                else -> old
            }
        }
    }

    private fun number(bytes: ByteArray): Int? = if (bytes.size < 4) null else
        (((bytes[2].toInt() and 255) shl 8) or (bytes[3].toInt() and 255)).takeIf { it > 0 }
    private fun decode(bytes: ByteArray, type: Int): String? = when (type) {
        1 -> TagTextDecoder.decodeDeclared(bytes, Charsets.UTF_8)
        2 -> TagTextDecoder.decodeDeclared(bytes, Charsets.UTF_16BE)
        else -> null
    }?.takeIf { it.isNotBlank() }
    private val Empty = AudioTagFields("", "", null, null, null, null, null)
    private val Keys = setOf("©nam", "©ART", "©alb", "aART", "©wrt", "©wrk", "©mvn", "©gen", "©lyr", "©day", "trkn", "disk", "covr")
    private const val MaxRetainedBytes = MaxEmbeddedArtworkBytes + MaxEmbeddedTextBytes
}

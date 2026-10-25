package app.echo.android.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal enum class AudioTagRewriteStatus {
    Written,
    UnsupportedFormat,
    InvalidSource,
}

internal object AudioFileTagRewriter {
    fun rewriteFile(
        source: File,
        destination: File,
        fields: AudioTagFields,
        mimeType: String? = null,
    ): AudioTagRewriteStatus {
        if (fields.title.isBlank() || fields.artist.isBlank()) return AudioTagRewriteStatus.InvalidSource
        ContainerTagRewrite.rewriteFile(source, destination, fields)?.let { return it }
        if (WavTagRewriter.isWavFile(source, mimeType)) {
            return WavTagRewriter.rewriteFile(source, destination, fields)
        }
        return FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                rewrite(input, output, fields, mimeType)
            }
        }
    }

    fun rewrite(
        input: InputStream,
        output: OutputStream,
        fields: AudioTagFields,
        mimeType: String? = null,
    ): AudioTagRewriteStatus {
        if (fields.title.isBlank() || fields.artist.isBlank()) return AudioTagRewriteStatus.InvalidSource
        val peek = ByteArray(10)
        val peeked = readFully(input, peek)
        if (peeked <= 0) return AudioTagRewriteStatus.InvalidSource
        return when {
            isId3(peek, peeked) -> rewriteId3(peek, input, output, fields)
            isFlac(peek, peeked) -> rewriteFlac(peek, peeked, input, output, fields)
            looksLikeRiff(peek, peeked) || LibraryWavTagPolicy.isWavContainer(mimeType) ->
                WavTagRewriter.rewrite(PrefixInputStream(peek.copyOf(peeked), input), output, fields)
            looksLikeMp4(peek, peeked) || looksLikeOgg(peek, peeked) ->
                ContainerTagRewrite.rewrite(PrefixInputStream(peek.copyOf(peeked), input), output, fields)
            looksLikeMp3Frame(peek, peeked) || mimeLooksLikeMp3(mimeType) -> {
                output.write(buildId3Tag(majorVersion = 4, frames = emptyList(), fields = fields))
                copyMp3WithUpdatedLegacyTags(PrefixInputStream(peek.copyOf(peeked), input), output, fields)
                AudioTagRewriteStatus.Written
            }
            else -> AudioTagRewriteStatus.UnsupportedFormat
        }
    }

    fun readFields(input: InputStream): AudioTagFields? {
        val peek = ByteArray(10)
        val peeked = readFully(input, peek)
        if (peeked <= 0) return null
        if (peeked >= 4 && peek.decodeToString(0, 4) in setOf("MAC ", "wvpk", "TTA1", "tBaK")) {
            val ape = try { ApeTagReader.read(input) } catch (_: java.io.IOException) { null }
            return mergeAudioTags(ape, readId3v1(input, peek.copyOf(peeked)))
        }
        if (peeked >= 4 && peek.copyOfRange(0, 4).contentEquals(LocalAudioContainerMetadata.AsfHeader.copyOfRange(0, 4))) {
            return AsfMetadataReader.read(PrefixInputStream(peek.copyOf(peeked), input))?.tags
        }
        if (isFlac(peek, peeked)) return readFlacFields(peek, peeked, input)
        if (looksLikeWaveContainer(peek, peeked)) return readWavFields(PrefixInputStream(peek.copyOf(peeked), input))
        if (looksLikeMp4(peek, peeked)) return Mp4TagReader.read(PrefixInputStream(peek.copyOf(peeked), input))
        if (looksLikeOgg(peek, peeked)) return OggTagReader.read(PrefixInputStream(peek.copyOf(peeked), input))
        if (peeked >= 4 && peek.decodeToString(0, 4) in setOf("DSD ", "FRM8")) {
            return EchoDsdMetadata.read(PrefixInputStream(peek.copyOf(peeked), input))?.tags
        }
        val id3v1 = readId3v1(input, peek.copyOf(peeked))
        val id3 = if (isId3(peek, peeked)) readId3ThenMaybeWav(peek, input) else null
        return mergeAudioTags(id3, id3v1)
    }

    /**
     * ID3v1 is the last 128 bytes and has no charset flag.
     * FLAC and WAV return before this. File streams read the tail in place.
     */
    private fun readId3v1(input: InputStream, peek: ByteArray): AudioTagFields? {
        val tail = when (input) {
            is FileInputStream -> fileTail(input)
            is ByteArrayInputStream -> inMemoryTail(input, peek)
            is TagTailReader -> try { input.tail(ID3V1_BYTES) } catch (_: java.io.IOException) { null }
            else -> null
        } ?: return null
        return parseId3v1(tail)
    }

    private fun inMemoryTail(input: ByteArrayInputStream, peek: ByteArray): ByteArray? {
        val remaining = input.available()
        if (remaining < 0 || remaining > MAX_IN_MEMORY_ID3V1_BYTES || !input.markSupported()) return null
        input.mark(remaining + 1)
        val rest = input.readBytes()
        input.reset()
        val all = peek + rest
        if (all.size < ID3V1_BYTES) return null
        return all.copyOfRange(all.size - ID3V1_BYTES, all.size)
    }

    private fun fileTail(input: FileInputStream): ByteArray? {
        val channel = input.channel
        val size = runCatching { channel.size() }.getOrDefault(0L)
        if (size < ID3V1_BYTES) return null
        val buffer = ByteBuffer.allocate(ID3V1_BYTES)
        val position = runCatching { channel.position() }.getOrNull()
        val read = runCatching { channel.read(buffer, size - ID3V1_BYTES) }.getOrDefault(0)
        if (position != null) runCatching { channel.position(position) }
        if (read < ID3V1_BYTES) return null
        return buffer.array()
    }

    private fun parseId3v1(tag: ByteArray): AudioTagFields? {
        if (tag.size < ID3V1_BYTES) return null
        if (tag[0] != 'T'.code.toByte() || tag[1] != 'A'.code.toByte() || tag[2] != 'G'.code.toByte()) return null
        val title = decodeId3v1Field(tag.copyOfRange(3, 33))
        val artist = decodeId3v1Field(tag.copyOfRange(33, 63))
        val album = decodeId3v1Field(tag.copyOfRange(63, 93))
        val year = decodeId3v1Field(tag.copyOfRange(93, 97))?.toIntOrNull()?.takeIf { it > 0 }
        val track = if (tag[125] == 0.toByte()) (tag[126].toInt() and 0xFF).takeIf { it > 0 } else null
        if (title.isNullOrBlank() && artist.isNullOrBlank() && album.isNullOrBlank() && year == null && track == null) {
            return null
        }
        return AudioTagFields(
            title = title.orEmpty(),
            artist = artist.orEmpty(),
            album = album,
            albumArtist = null,
            trackNumber = track,
            discNumber = null,
            year = year,
        )
    }

    private fun decodeId3v1Field(raw: ByteArray): String? {
        val slice = raw.trimId3v1Padding()
        if (slice.isEmpty()) return null
        val full = TagTextDecoder.decode(slice)?.takeIf { it.isNotBlank() }
        val last = slice.last().toInt() and 0xFF
        if (last < 0x80) return full
        val shortened = TagTextDecoder.decode(slice.copyOf(slice.size - 1))?.takeIf { it.isNotBlank() }
        if (shortened != null && shortened.hanCount() > (full?.hanCount() ?: 0)) return shortened
        return full
    }

    private fun ByteArray.trimId3v1Padding(): ByteArray {
        var end = size
        while (end > 0) {
            val value = this[end - 1].toInt() and 0xFF
            if (value != 0 && value != 0x20) break
            end -= 1
        }
        return if (end == size) this else copyOf(end)
    }

    private fun String.hanCount(): Int = count { it in '\u3400'..'\u4DBF' || it in '\u4E00'..'\u9FFF' }

    private fun rewriteId3(
        header: ByteArray,
        input: InputStream,
        output: OutputStream,
        fields: AudioTagFields,
    ): AudioTagRewriteStatus {
        val parsed = parseId3(header, input) ?: return AudioTagRewriteStatus.InvalidSource
        output.write(buildId3Tag(parsed.majorVersion, parsed.frames, fields))
        copyMp3WithUpdatedLegacyTags(input, output, fields)
        return AudioTagRewriteStatus.Written
    }

    private fun rewriteFlac(
        peek: ByteArray,
        peeked: Int,
        input: InputStream,
        output: OutputStream,
        fields: AudioTagFields,
    ): AudioTagRewriteStatus {
        val rest = PrefixInputStream(peek.copyOfRange(4, peeked), input)
        val blocks = readFlacBlocks(rest) ?: return AudioTagRewriteStatus.InvalidSource
        if (blocks.none { it.type == FLAC_STREAMINFO }) return AudioTagRewriteStatus.InvalidSource
        val rewritten = replaceFlacVorbis(blocks, fields) ?: return AudioTagRewriteStatus.InvalidSource
        output.write(peek, 0, 4)
        writeFlacBlocks(output, rewritten)
        rest.copyTo(output, COPY_BUFFER)
        return AudioTagRewriteStatus.Written
    }

    private fun readId3Fields(header: ByteArray, input: InputStream): AudioTagFields? {
        val parsed = Id3TagReader.read(header, input) ?: return null
        return fieldsFromId3Frames(parsed.frames.map { Id3Frame(it.id, it.flags, it.payload) }, parsed.version)
    }

    private fun readId3ThenMaybeWav(header: ByteArray, input: InputStream): AudioTagFields? {
        val id3 = readId3Fields(header, input)
        val next = ByteArray(12)
        val read = try { readFully(input, next) } catch (_: java.io.IOException) { return id3?.takeUnless { it.isBlank() } }
        if (isFlac(next, read)) return mergeAudioTags(id3, readFlacFields(next, read, input))
        if (read >= 4 && next.decodeToString(0, 4) in setOf("MAC ", "wvpk", "TTA1", "tBaK")) {
            val ape = try { ApeTagReader.read(input) } catch (_: java.io.IOException) { null }
            return mergeAudioTags(id3, ape)
        }
        if (!looksLikeWaveContainer(next, read)) return id3?.takeUnless { it.isBlank() }
        val wav = readWavFields(PrefixInputStream(next.copyOf(read), input))
        return mergeAudioTags(preferred = id3, fallback = wav)
    }

    private fun readWavFields(input: InputStream): AudioTagFields? {
        val header = input.readExact(12) ?: return null
        if (!looksLikeWaveContainer(header, 12) || header.decodeToString(8, 12) != "WAVE") return null
        val rf64 = header.decodeToString(0, 4) == "RF64"
        var remaining = if (rf64) Long.MAX_VALUE else (u32le(header, 4) - 4L).coerceAtLeast(0L)
        var consumed = 4L
        var info: AudioTagFields? = null
        var embeddedId3: AudioTagFields? = null
        var dataSize64: Long? = null
        var chunks = 0
        while (remaining >= 8L && chunks < MAX_WAV_CHUNKS) {
            val chunkHeader = input.readExact(8) ?: break
            remaining -= 8L
            consumed += 8L
            val chunkId = chunkHeader.decodeToString(0, 4)
            val chunkSize = u32le(chunkHeader, 4)
            val padded = chunkSize + (chunkSize and 1L)
            if (padded > remaining && chunkId != "data") break
            when (chunkId) {
                "ds64" -> {
                    if (chunkSize !in 1L..MAX_INFO_BYTES) break
                    val payload = input.readExact(chunkSize.toInt()) ?: break
                    if (!skipExact(input, padded - chunkSize)) break
                    if (payload.size >= 16) dataSize64 = u64le(payload, 8)
                    if (rf64) {
                        if (payload.size < 28) break
                        val riffSize64 = u64le(payload, 0)
                        if (riffSize64 < consumed || riffSize64 - consumed < padded) break
                        remaining = riffSize64 - consumed
                    }
                }
                "LIST" -> {
                    if (chunkSize in 1L..MAX_INFO_BYTES) {
                        val payload = input.readExact(chunkSize.toInt()) ?: break
                        if (!skipExact(input, padded - chunkSize)) break
                        parseInfoList(payload)?.let { parsed ->
                            info = mergeAudioTags(preferred = info, fallback = parsed)
                        }
                    } else if (!skipExact(input, padded)) {
                        break
                    }
                }
                "id3 ", "ID3 " -> {
                    if (chunkSize in 10L..64L * 1024 * 1024) {
                        val slice = TagSliceInput(input, chunkSize)
                        val id3Header = slice.tagBytes(10) ?: break
                        if (isId3(id3Header, 10)) {
                            embeddedId3 = mergeAudioTags(embeddedId3, readId3Fields(id3Header, slice))
                        }
                        if (!slice.skipTagBytes(slice.remaining)) break
                        if (!skipExact(input, padded - chunkSize)) break
                    } else if (!skipExact(input, padded)) {
                        break
                    }
                }
                "data" -> {
                    val skip = when {
                        chunkSize == 0xFFFFFFFFL -> dataSize64 ?: break
                        padded <= remaining -> padded
                        else -> break
                    }
                    if (skip < 0 || skip > remaining) break
                    if (!skipExact(input, skip)) break
                    if (chunkSize == 0xFFFFFFFFL && (skip and 1L) == 1L && !skipExact(input, 1L)) break
                }
                else -> if (!skipExact(input, padded)) break
            }
            val consumedPayload = if (chunkId == "data" && chunkSize == 0xFFFFFFFFL) {
                val skipped = dataSize64 ?: 0L
                skipped + (skipped and 1L)
            } else {
                padded
            }
            remaining -= consumedPayload
            consumed += consumedPayload
            chunks += 1
        }
        return mergeAudioTags(preferred = embeddedId3, fallback = info)
    }

    private fun parseInfoList(payload: ByteArray): AudioTagFields? {
        if (payload.size < 4 || payload.decodeToString(0, 4) != "INFO") return null
        val values = HashMap<String, String>()
        var offset = 4
        while (offset + 8 <= payload.size) {
            val id = payload.decodeToString(offset, offset + 4)
            val size = u32le(payload, offset + 4).toInt()
            offset += 8
            if (size < 0 || offset + size > payload.size) break
            val text = TagTextDecoder.decode(payload.copyOfRange(offset, offset + size))
                ?.takeIf { it.isNotBlank() }
            if (text != null) values[id] = text
            offset += size + (size and 1)
        }
        val title = values["INAM"]
        val artist = values["IART"]
        val album = values["IPRD"]
        val track = (values["ITRK"] ?: values["IPRT"] ?: values["TRCK"])
            ?.substringBefore('/')
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
        val year = values["ICRD"]?.take(4)?.toIntOrNull()?.takeIf { it > 0 }
        if (title.isNullOrBlank() && artist.isNullOrBlank() && album.isNullOrBlank() && track == null && year == null) {
            return null
        }
        return AudioTagFields(
            title = title.orEmpty(),
            artist = artist.orEmpty(),
            album = album,
            albumArtist = null,
            trackNumber = track,
            discNumber = null,
            year = year,
            genre = values["IGNR"],
        )
    }

    private fun readId3FromBytes(data: ByteArray): AudioTagFields? {
        if (data.size < 10 || !isId3(data, 10)) return null
        return readId3Fields(data.copyOfRange(0, 10), data.inputStream(offset = 10, length = data.size - 10))
    }

    private fun readFlacFields(peek: ByteArray, peeked: Int, input: InputStream): AudioTagFields? {
        val rest = PrefixInputStream(peek.copyOfRange(4, peeked), input)
        var total = 0
        var fields: AudioTagFields? = null
        var artwork: TagArtwork? = null
        fun result(): AudioTagFields? = artwork?.let { picture ->
            (fields ?: AudioTagFields("", "", null, null, null, null, null)).copy(
                artworkBytes = picture.bytes, artworkMime = picture.mime,
            )
        } ?: fields
        repeat(MAX_FLAC_BLOCKS) {
            val header = rest.readExact(4) ?: return result()
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            total += length
            if (total > MAX_METADATA_BYTES) return result()
            if (type == FLAC_VORBIS_COMMENT) {
                if (length <= MAX_TAG_BYTES) {
                    fields = mergeAudioTags(fields, fieldsFromVorbis(rest.readExact(length) ?: return result()))
                } else if (!skipExact(rest, length.toLong())) return result()
            } else if (type == FLAC_PICTURE && artwork?.type != 3) {
                val picture = EmbeddedTagArtwork.readFlac(rest, length)
                if (picture != null && (artwork == null || picture.type == 3)) artwork = picture
            } else if (!skipExact(rest, length.toLong())) return result()
            if (header[0].toInt() and 0x80 != 0) return result()
        }
        return result()
    }

    private fun parseId3(header: ByteArray, input: InputStream): ParsedId3? {
        val majorVersion = header[3].toInt() and 0xFF
        if (majorVersion !in 2..4) return null
        val flags = header[5].toInt() and 0xFF
        if (majorVersion == 2 && flags and 0x40 != 0) return null
        val tagSize = syncSafeInt(header, 6).takeIf { it in 1..MAX_TAG_BYTES } ?: return null
        val rawBody = input.readExact(tagSize) ?: return null
        val unsynced = flags and 0x80 != 0
        // v2.4 frame sizes include unsynchronisation bytes; parse boundaries first.
        val body = if (unsynced && majorVersion < 4) decodeUnsync(rawBody) else rawBody
        var offset = 0
        if (flags and 0x40 != 0) {
            if (body.size < 4) return null
            val extendedSize = if (majorVersion == 4) {
                syncSafeInt(body, 0)
            } else {
                int32be(body, 0) + 4
            }
            if (extendedSize !in 4..body.size) return null
            offset = extendedSize
        }
        val frames = if (majorVersion == 2) {
            parseId3v2Frames(body, offset)
        } else {
            parseId3Frames(body, offset, majorVersion)
        } ?: return null
        val outputVersion = if (majorVersion == 2) 4 else majorVersion
        val storedFrames = if (unsynced && majorVersion == 4) {
            frames.map { frame ->
                frame.copy(flags = byteArrayOf(frame.flags[0], (frame.flags[1].toInt() or 0x02).toByte()))
            }
        } else frames
        return ParsedId3(outputVersion, storedFrames)
    }

    private fun parseId3Frames(body: ByteArray, start: Int, majorVersion: Int): List<Id3Frame>? {
        val frames = ArrayList<Id3Frame>()
        var offset = start
        while (offset + 10 <= body.size) {
            if (body[offset] == 0.toByte()) break
            val id = body.decodeToString(offset, offset + 4)
            if (id.any { it.code < 32 || it.code > 126 }) return null
            val size = if (majorVersion == 4) {
                syncSafeInt(body, offset + 4)
            } else {
                int32be(body, offset + 4)
            }
            if (size < 0 || offset + 10 + size > body.size) return null
            if (size == 0) {
                offset += 10
                continue
            }
            val flags = body.copyOfRange(offset + 8, offset + 10)
            val payload = body.copyOfRange(offset + 10, offset + 10 + size)
            frames += Id3Frame(id, flags, payload)
            offset += 10 + size
            if (frames.size > MAX_FRAMES) return null
        }
        return frames
    }

    private fun parseId3v2Frames(body: ByteArray, start: Int): List<Id3Frame>? {
        val frames = ArrayList<Id3Frame>()
        var offset = start
        while (offset + 6 <= body.size) {
            if (body[offset] == 0.toByte()) break
            val shortId = body.decodeToString(offset, offset + 3)
            val size = ((body[offset + 3].toInt() and 0xFF) shl 16) or
                ((body[offset + 4].toInt() and 0xFF) shl 8) or
                (body[offset + 5].toInt() and 0xFF)
            if (size < 0 || offset + 6 + size > body.size) return null
            // Reject a conversion we cannot preserve instead of silently deleting a frame.
            val mapped = V22_FRAME_MAP[shortId] ?: return null
            if (size > 0) {
                val payload = body.copyOfRange(offset + 6, offset + 6 + size)
                val converted = if (shortId == "PIC") {
                    if (payload.size < 5) return null
                    val format = payload.copyOfRange(1, 4).toString(StandardCharsets.ISO_8859_1)
                    val mime = when (format.uppercase(java.util.Locale.ROOT)) {
                        "PNG" -> "image/png"
                        "JPG" -> "image/jpeg"
                        "-->" -> "-->"
                        else -> return null
                    }
                    byteArrayOf(payload[0]) + mime.toByteArray(StandardCharsets.ISO_8859_1) +
                        byteArrayOf(0) + payload.copyOfRange(4, payload.size)
                } else payload
                frames += Id3Frame(mapped, byteArrayOf(0, 0), converted)
            }
            offset += 6 + size
            if (frames.size > MAX_FRAMES) return null
        }
        return frames
    }

    private fun buildId3Tag(majorVersion: Int, frames: List<Id3Frame>, fields: AudioTagFields): ByteArray {
        val version = if (majorVersion == 3) 3 else 4
        val nextFrames = replaceId3TextFrames(version, frames, fields)
        val body = ByteArrayOutputStream()
        nextFrames.forEach { frame ->
            body.write(frame.id.toByteArray(StandardCharsets.ISO_8859_1))
            val sizeBytes = if (version == 4) synchsafeBytes(frame.payload.size) else int32beBytes(frame.payload.size)
            body.write(sizeBytes)
            body.write(frame.flags.copyOf(2))
            body.write(frame.payload)
        }
        repeat(ID3_PADDING) { body.write(0) }
        val bodyBytes = body.toByteArray()
        val header = ByteArrayOutputStream(10)
        header.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), version.toByte(), 0, 0))
        header.write(synchsafeBytes(bodyBytes.size))
        return header.toByteArray() + bodyBytes
    }

    internal fun encodeId3Tag(existingTag: ByteArray?, fields: AudioTagFields): ByteArray {
        val parsed = if (existingTag != null && existingTag.size >= 10 && isId3(existingTag, existingTag.size)) {
            parseId3(
                existingTag.copyOfRange(0, 10),
                existingTag.inputStream(offset = 10, length = existingTag.size - 10),
            ) ?: error("Cannot safely preserve existing ID3 frames")
        } else {
            null
        }
        // Retained v2.3 frames keep their version-specific flags and payload prefixes.
        return buildId3Tag(parsed?.majorVersion ?: 4, parsed?.frames.orEmpty(), fields)
    }

    private fun replaceId3TextFrames(
        version: Int,
        frames: List<Id3Frame>,
        fields: AudioTagFields,
    ): List<Id3Frame> {
        fun originalText(id: String): String? = frames.asSequence().filter { it.id == id }
            .mapNotNull { readableId3Payload(it, version)?.let(::decodeId3Text) }.firstOrNull()
        fun originalNames(id: String): String? = LibraryArtistPolicy.display(frames.filter { it.id == id }
            .flatMap { readableId3Payload(it, version)?.let(::decodeId3Values).orEmpty() })
        val preserveArtist = preserveTagNames(fields.artist, originalNames("TPE1"))
        val preserveAlbumArtist = preserveTagNames(fields.albumArtist, originalNames("TPE2"))
        val originalDate = originalText("TDRC") ?: originalText("TYER")
        val preserveDate = fields.year != null && originalDate?.take(4)?.toIntOrNull() == fields.year
        val managed = HashSet(if (version == 4) ManagedId3v24 else ManagedId3v23)
        if (preserveDate) managed.removeAll(setOf("TDRC", "TYER"))
        if (preserveArtist) managed.remove("TPE1")
        if (preserveAlbumArtist) managed.remove("TPE2")
        val replaceComposer = shouldReplaceOptionalTextTag(fields.composer, fields.composerModified, originalText("TCOM"))
        val replaceGenre = shouldReplaceOptionalTextTag(fields.genre, fields.genreModified, originalText("TCON"))
        if (replaceComposer) managed += "TCOM"
        if (replaceGenre) managed += "TCON"
        if (fields.lyrics != null) managed += setOf("USLT", "SYLT")
        val kept = frames.filterNot { frame ->
            frame.id in managed ||
                (fields.artworkBytes != null && frame.id == "APIC" && readableId3Payload(frame, version)
                    ?.let { EmbeddedTagArtwork.id3PictureType(it) } == 3) ||
                (fields.lyrics != null && frame.id == "TXXX" && readableId3Payload(frame, version)
                    ?.let(::decodeReadTxxx)?.first?.uppercase(java.util.Locale.ROOT) in Id3LyricsKeys) ||
                (fields.replayGainTrackGainDb != null && isReplayGainTrackTxxx(frame, version))
        }
        val next = ArrayList<Id3Frame>(kept.size + 10)
        next += textFrame(version, "TIT2", fields.title)
        if (!preserveArtist) next += textFrame(version, "TPE1", fields.artist)
        fields.album?.let { next += textFrame(version, "TALB", it) }
        if (!preserveAlbumArtist) fields.albumArtist?.let { next += textFrame(version, "TPE2", it) }
        if (replaceComposer) fields.composer?.takeIf { it.isNotBlank() }?.let { next += textFrame(version, "TCOM", it) }
        if (replaceGenre) fields.genre?.takeIf { it.isNotBlank() }?.let { next += textFrame(version, "TCON", it) }
        tagNumberWithTotal(fields.trackNumber, originalText("TRCK"))?.let { next += textFrame(version, "TRCK", it) }
        tagNumberWithTotal(fields.discNumber, originalText("TPOS"))?.let { next += textFrame(version, "TPOS", it) }
        if (!preserveDate) fields.year?.let { year ->
            if (version == 4) {
                next += textFrame(version, "TDRC", year.toString())
            } else {
                next += textFrame(version, "TYER", year.toString())
            }
        }
        fields.lyrics?.takeIf { it.isNotBlank() }?.let { next += usltFrame(version, it) }
        fields.artworkBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            next += apicFrame(version, bytes, fields.artworkMime ?: "image/jpeg")
        }
        fields.replayGainTrackGainDb?.let { gain ->
            next += txxxFrame(version, "REPLAYGAIN_TRACK_GAIN", formatReplayGainTag(gain))
        }
        next += kept
        return next
    }

    private fun usltFrame(version: Int, lyrics: String): Id3Frame {
        val payload = if (version == 4) {
            byteArrayOf(3) +
                "xxx".toByteArray(StandardCharsets.ISO_8859_1) +
                0 +
                lyrics.toByteArray(StandardCharsets.UTF_8)
        } else {
            byteArrayOf(1) +
                "xxx".toByteArray(StandardCharsets.ISO_8859_1) +
                byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0) +
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                lyrics.toByteArray(Charsets.UTF_16LE) +
                byteArrayOf(0, 0)
        }
        return Id3Frame("USLT", byteArrayOf(0, 0), payload)
    }

    private fun apicFrame(version: Int, image: ByteArray, mime: String): Id3Frame {
        val mimeBytes = mime.ifBlank { "image/jpeg" }.toByteArray(StandardCharsets.ISO_8859_1)
        val payload = byteArrayOf(0) + mimeBytes + 0 + 3 + 0 + image
        return Id3Frame("APIC", byteArrayOf(0, 0), payload)
    }

    private fun txxxFrame(version: Int, description: String, value: String): Id3Frame {
        val payload = if (version == 4) {
            byteArrayOf(3) +
                description.toByteArray(StandardCharsets.UTF_8) + 0 +
                value.toByteArray(StandardCharsets.UTF_8)
        } else {
            byteArrayOf(1) +
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                description.toByteArray(Charsets.UTF_16LE) +
                byteArrayOf(0, 0) +
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                value.toByteArray(Charsets.UTF_16LE) +
                byteArrayOf(0, 0)
        }
        return Id3Frame("TXXX", byteArrayOf(0, 0), payload)
    }

    private fun isReplayGainTrackTxxx(frame: Id3Frame, version: Int): Boolean =
        frame.id == "TXXX" && readableId3Payload(frame, version)?.let(::decodeReadTxxx)
            ?.first.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)

    private fun formatReplayGainTag(gainDb: Float): String {
        val hundredths = (gainDb * 100f).toInt() / 100.0
        return "%+.2f dB".format(java.util.Locale.US, hundredths)
    }

    private fun textFrame(version: Int, id: String, value: String): Id3Frame {
        val payload = if (version == 4) {
            byteArrayOf(3) + value.toByteArray(StandardCharsets.UTF_8) + 0
        } else {
            byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) +
                value.toByteArray(Charsets.UTF_16LE) +
                byteArrayOf(0, 0)
        }
        return Id3Frame(id, byteArrayOf(0, 0), payload)
    }

    private fun fieldsFromId3Frames(frames: List<Id3Frame>, majorVersion: Int): AudioTagFields {
        fun text(id: String): String? = frames.asSequence().filter { it.id == id }
            .mapNotNull { readableId3Payload(it, majorVersion)?.let(::decodeId3Text) }.firstOrNull()
        fun names(id: String): String? = LibraryArtistPolicy.display(frames.filter { it.id == id }
            .flatMap { readableId3Payload(it, majorVersion)?.let(::decodeId3Values).orEmpty() })
        val yearText = text("TDRC") ?: text("TYER")
        val custom = frames.asSequence().filter { it.id == "TXXX" }
            .mapNotNull { readableId3Payload(it, majorVersion)?.let(::decodeReadTxxx) }.toList()
        val artwork = frames.asSequence().filter { it.id == "APIC" || it.id == "PIC" }
            .mapNotNull { frame -> readableId3Payload(frame, majorVersion)?.let { EmbeddedTagArtwork.id3(it, frame.id == "PIC") } }
            .let { pictures ->
                var fallback: TagArtwork? = null
                pictures.firstOrNull { picture ->
                    if (fallback == null) fallback = picture
                    picture.type == 3
                } ?: fallback
            }
        return AudioTagFields(
            title = text("TIT2").orEmpty(),
            artist = names("TPE1").orEmpty(),
            album = text("TALB"),
            albumArtist = names("TPE2"),
            trackNumber = text("TRCK")?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 },
            discNumber = text("TPOS")?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 },
            year = yearText?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
            composer = text("TCOM")?.takeIf { it.isNotBlank() },
            genre = text("TCON")?.takeIf { it.isNotBlank() },
            lyrics = frames.asSequence().filter { it.id == "USLT" }
                .mapNotNull { readableId3Payload(it, majorVersion)?.let(::decodeUslt) }.firstOrNull()
                ?: custom.firstOrNull { it.first.uppercase(java.util.Locale.ROOT) in Id3LyricsKeys }?.second
                ?: frames.asSequence().filter { it.id == "SYLT" }
                    .mapNotNull { readableId3Payload(it, majorVersion)?.let(::decodeSyncedId3Lyrics) }.firstOrNull(),
            artworkBytes = artwork?.bytes,
            artworkMime = artwork?.mime,
            replayGainTrackGainDb = custom.firstOrNull { it.first.equals("REPLAYGAIN_TRACK_GAIN", true) }
                ?.second?.substringBefore(' ')?.toFloatOrNull()?.takeIf { it.isFinite() },
        )
    }

    private fun decodeReadTxxx(payload: ByteArray): Pair<String, String>? {
        if (payload.size < 3) return null
        val encoding = payload[0]
        val width = if (encoding == 1.toByte() || encoding == 2.toByte()) 2 else 1
        var end = 1
        while (end + width <= payload.size) {
            if (payload[end] == 0.toByte() && (width == 1 || payload[end + 1] == 0.toByte())) {
                val name = decodeId3Text(byteArrayOf(encoding) + payload.copyOfRange(1, end)) ?: return null
                val value = decodeId3Text(byteArrayOf(encoding) + payload.copyOfRange(end + width, payload.size)) ?: return null
                return name to value
            }
            end += width
        }
        return null
    }

    private fun readableId3Payload(frame: Id3Frame, majorVersion: Int): ByteArray? {
        val flags = frame.flags[1].toInt() and 0xFF
        // Compressed/encrypted frames need a codec/key; leave those to the platform fallback.
        if (flags and (if (majorVersion == 3) 0xC0 else 0x0C) != 0) return null
        val payload = if (majorVersion == 4 && flags and 0x02 != 0) decodeUnsync(frame.payload) else frame.payload
        var offset = if (flags and (if (majorVersion == 3) 0x20 else 0x40) != 0) 1 else 0
        if (majorVersion == 4 && flags and 0x01 != 0) offset += 4
        if (offset >= payload.size) return null
        return if (offset == 0) payload else payload.copyOfRange(offset, payload.size)
    }

    private fun decodeId3Text(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val encoding = payload[0].toInt() and 0xFF
        if (payload.size <= 1) return null
        val raw = payload.copyOfRange(1, payload.size)
        val text = when (encoding) {
            0 -> TagTextDecoder.decode(raw.copyOfRange(0, raw.indexOf(0).takeIf { it >= 0 } ?: raw.size))
            1 -> TagTextDecoder.decodeDeclared(raw, Charsets.UTF_16)
            2 -> TagTextDecoder.decodeDeclared(raw, Charsets.UTF_16BE)
            3 -> TagTextDecoder.decodeDeclared(raw, StandardCharsets.UTF_8)
            else -> null
        }
        return text?.trimEnd('\u0000')?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun decodeId3Values(payload: ByteArray): List<String> {
        if (payload.size <= 1) return emptyList()
        val raw = payload.copyOfRange(1, payload.size)
        if (payload[0].toInt() == 0) {
            val values = ArrayList<String>()
            var start = 0
            for (end in 0..raw.size) {
                if (end == raw.size || raw[end] == 0.toByte()) {
                    TagTextDecoder.decode(raw.copyOfRange(start, end))?.takeIf { it.isNotBlank() }?.let(values::add)
                    start = end + 1
                }
            }
            return values
        }
        val charset = when (payload[0].toInt()) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return emptyList()
        }
        val text = runCatching { charset.newDecoder().decode(ByteBuffer.wrap(raw)).toString() }.getOrNull()
            ?: return emptyList()
        return text.split('\u0000').map { it.trimStart('\uFEFF').trim() }
            .filter { it.isNotBlank() && it.none { char -> char.isISOControl() } }
    }

    private fun decodeUslt(payload: ByteArray): String? {
        if (payload.size <= 5) return null
        val encoding = payload[0].toInt() and 0xFF
        val terminator = if (encoding == 1 || encoding == 2) 2 else 1
        var index = 4
        while (index + terminator <= payload.size) {
            val ended = if (terminator == 1) {
                payload[index] == 0.toByte()
            } else {
                payload[index] == 0.toByte() && payload[index + 1] == 0.toByte()
            }
            if (ended) {
                index += terminator
                break
            }
            index += terminator
        }
        if (index >= payload.size) return null
        val raw = payload.copyOfRange(index, payload.size)
        val text = when (encoding) {
            0 -> TagTextDecoder.decode(raw)
            1 -> TagTextDecoder.decodeDeclared(raw, Charsets.UTF_16)
            2 -> TagTextDecoder.decodeDeclared(raw, Charsets.UTF_16BE)
            3 -> TagTextDecoder.decodeDeclared(raw, StandardCharsets.UTF_8)
            else -> null
        } ?: return null
        return text.trimEnd('\u0000').trim().takeIf { it.isNotEmpty() }
    }

    private fun readFlacBlocks(input: InputStream): List<FlacBlock>? {
        val blocks = ArrayList<FlacBlock>()
        var total = 0
        repeat(MAX_FLAC_BLOCKS) {
            val header = input.readExact(4) ?: return null
            val isLast = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            total += length
            if (total > MAX_METADATA_BYTES) return null
            val payload = input.readExact(length) ?: return null
            blocks += FlacBlock(type, payload)
            if (isLast) return blocks
        }
        return null
    }

    private fun replaceFlacVorbis(blocks: List<FlacBlock>, fields: AudioTagFields): List<FlacBlock>? {
        val commentBlocks = blocks.filter { it.type == FLAC_VORBIS_COMMENT }
        if (commentBlocks.size > 1) return null
        val existing = commentBlocks.singleOrNull()?.payload
        val vendor = existing?.let(::vorbisVendor).orEmpty().ifBlank { DEFAULT_VENDOR }
        val originalComments = existing?.let { VorbisTagReader.commentsForRewrite(it) ?: return null }.orEmpty()
        fun original(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            originalComments.firstOrNull { it.first.equals(key, ignoreCase = true) && it.second.isNotBlank() }?.second
        }
        val replaceComposer = shouldReplaceOptionalTextTag(fields.composer, fields.composerModified, original("COMPOSER"))
        val replaceGenre = shouldReplaceOptionalTextTag(fields.genre, fields.genreModified, original("GENRE"))
        val preserveArtist = preserveTagNames(fields.artist, LibraryArtistPolicy.display(originalComments
            .filter { it.first.equals("ARTIST", ignoreCase = true) }.map { it.second }))
        val preserveAlbumArtist = preserveTagNames(fields.albumArtist, LibraryArtistPolicy.display(originalComments
            .filter { it.first.uppercase(java.util.Locale.ROOT) in AlbumArtistVorbisKeys }.map { it.second }))
        val comments = mutableListOf<Pair<String, String>>()
        originalComments.forEach { (key, value) ->
            val upper = key.uppercase(java.util.Locale.ROOT)
            if ((preserveArtist && upper == "ARTIST") || (preserveAlbumArtist && upper in AlbumArtistVorbisKeys)) {
                comments += key to value
                return@forEach
            }
            if (upper in ManagedVorbisKeys || (upper == "GENRE" && replaceGenre) || (upper == "COMPOSER" && replaceComposer)) return@forEach
            if (fields.lyrics != null && upper in VorbisLyricsKeys) return@forEach
            if (fields.replayGainTrackGainDb != null && upper == "REPLAYGAIN_TRACK_GAIN") return@forEach
            comments += key to value
        }
        comments += "TITLE" to fields.title
        if (!preserveArtist) comments += "ARTIST" to fields.artist
        fields.album?.let { comments += "ALBUM" to it }
        if (!preserveAlbumArtist) fields.albumArtist?.let { comments += "ALBUMARTIST" to it }
        if (replaceComposer) fields.composer?.takeIf { it.isNotBlank() }?.let { comments += "COMPOSER" to it }
        if (replaceGenre) fields.genre?.takeIf { it.isNotBlank() }?.let { comments += "GENRE" to it }
        tagNumberWithTotal(fields.trackNumber, original("TRACKNUMBER", "TRACK"))?.let { comments += "TRACKNUMBER" to it }
        tagNumberWithTotal(fields.discNumber, original("DISCNUMBER", "DISC"))?.let { comments += "DISCNUMBER" to it }
        tagDateWithYear(fields.year, original("DATE", "YEAR"))?.let { comments += "DATE" to it }
        fields.lyrics?.takeIf { it.isNotBlank() }?.let { comments += "LYRICS" to it }
        fields.replayGainTrackGainDb?.let { comments += "REPLAYGAIN_TRACK_GAIN" to formatReplayGainTag(it) }
        val vorbis = encodeVorbis(vendor, comments)
        val dropPicture = fields.artworkBytes != null
        val kept = blocks.filterNot { block ->
            block.type == FLAC_VORBIS_COMMENT ||
                block.type == FLAC_PADDING ||
                (dropPicture && block.type == FLAC_PICTURE && block.payload.size >= 4 && int32be(block.payload, 0) == 3)
        }
        val streamInfo = kept.filter { it.type == FLAC_STREAMINFO }
        val others = kept.filterNot { it.type == FLAC_STREAMINFO }.toMutableList()
        if (fields.artworkBytes?.isNotEmpty() == true) {
            others += FlacBlock(
                FLAC_PICTURE,
                encodeFlacPicture(fields.artworkBytes, fields.artworkMime ?: "image/jpeg"),
            )
        }
        return streamInfo + FlacBlock(FLAC_VORBIS_COMMENT, vorbis) + others +
            FlacBlock(FLAC_PADDING, ByteArray(FLAC_PADDING_SIZE))
    }

    private fun encodeFlacPicture(image: ByteArray, mime: String): ByteArray {
        val mimeBytes = mime.ifBlank { "image/jpeg" }.toByteArray(StandardCharsets.US_ASCII)
        val (width, height) = imageDimensions(image, mime)
        val out = ByteArrayOutputStream(32 + mimeBytes.size + image.size)
        out.write(int32beBytes(3))
        out.write(int32beBytes(mimeBytes.size))
        out.write(mimeBytes)
        out.write(int32beBytes(0))
        out.write(int32beBytes(width))
        out.write(int32beBytes(height))
        out.write(int32beBytes(24))
        out.write(int32beBytes(0))
        out.write(int32beBytes(image.size))
        out.write(image)
        return out.toByteArray()
    }

    private fun imageDimensions(image: ByteArray, mime: String): Pair<Int, Int> {
        if (mime.contains("png", ignoreCase = true) || image.startsWithPng()) {
            if (image.size >= 24) {
                val width = int32be(image, 16).coerceAtLeast(0)
                val height = int32be(image, 20).coerceAtLeast(0)
                return width to height
            }
        }
        return 0 to 0
    }

    private fun ByteArray.startsWithPng(): Boolean =
        size >= 8 &&
            this[0] == 0x89.toByte() &&
            this[1] == 'P'.code.toByte() &&
            this[2] == 'N'.code.toByte() &&
            this[3] == 'G'.code.toByte()

    private fun writeFlacBlocks(output: OutputStream, blocks: List<FlacBlock>) {
        blocks.forEachIndexed { index, block ->
            val last = index == blocks.lastIndex
            val header0 = block.type or if (last) 0x80 else 0
            val length = block.payload.size
            output.write(
                byteArrayOf(
                    header0.toByte(),
                    ((length shr 16) and 0xFF).toByte(),
                    ((length shr 8) and 0xFF).toByte(),
                    (length and 0xFF).toByte(),
                ),
            )
            output.write(block.payload)
        }
    }

    private fun vorbisVendor(payload: ByteArray): String? {
        val vendorLength = littleEndianInt32(payload, 0) ?: return null
        if (vendorLength < 0 || vendorLength > payload.size - 4) return null
        return payload.copyOfRange(4, 4 + vendorLength).toString(StandardCharsets.UTF_8)
    }

    private fun fieldsFromVorbis(payload: ByteArray): AudioTagFields {
        return VorbisTagReader.fields(payload)
    }

    private fun encodeVorbis(vendor: String, comments: List<Pair<String, String>>): ByteArray {
        val vendorBytes = vendor.toByteArray(StandardCharsets.UTF_8)
        val commentBytes = comments.map { (key, value) ->
            "$key=$value".toByteArray(StandardCharsets.UTF_8)
        }
        val out = ByteArrayOutputStream()
        out.write(littleEndianInt32Bytes(vendorBytes.size))
        out.write(vendorBytes)
        out.write(littleEndianInt32Bytes(commentBytes.size))
        commentBytes.forEach { bytes ->
            out.write(littleEndianInt32Bytes(bytes.size))
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun isId3(bytes: ByteArray, n: Int): Boolean =
        n >= 10 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()

    private fun isFlac(bytes: ByteArray, n: Int): Boolean =
        n >= 4 &&
            bytes[0] == 'f'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() &&
            bytes[3] == 'C'.code.toByte()

    private fun looksLikeMp3Frame(bytes: ByteArray, n: Int): Boolean {
        if (n < 2) return false
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 0xFF && (second and 0xE0) == 0xE0
    }

    private fun looksLikeMp4(bytes: ByteArray, n: Int): Boolean =
        n >= 8 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()

    private fun looksLikeOgg(bytes: ByteArray, n: Int): Boolean =
        n >= 4 &&
            bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() &&
            bytes[3] == 'S'.code.toByte()

    private fun looksLikeWaveContainer(bytes: ByteArray, n: Int): Boolean =
        looksLikeRiff(bytes, n) || (n >= 4 && bytes.decodeToString(0, 4) == "RF64")

    private fun looksLikeRiff(bytes: ByteArray, n: Int): Boolean =
        n >= 4 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()

    private fun mimeLooksLikeMp3(mimeType: String?): Boolean {
        val mime = mimeType.orEmpty().lowercase()
        return mime.contains("mpeg") || mime == "audio/mp3" || mime == "audio/x-mpeg"
    }

    private fun decodeUnsync(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        var index = 0
        while (index < data.size) {
            out.write(data[index].toInt() and 0xFF)
            if (data[index] == 0xFF.toByte() && index + 1 < data.size && data[index + 1] == 0.toByte()) {
                index += 2
            } else {
                index += 1
            }
        }
        return out.toByteArray()
    }

    private fun syncSafeInt(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0x7F) shl 21) or
            ((bytes[start + 1].toInt() and 0x7F) shl 14) or
            ((bytes[start + 2].toInt() and 0x7F) shl 7) or
            (bytes[start + 3].toInt() and 0x7F)

    private fun synchsafeBytes(value: Int): ByteArray {
        val safe = value.coerceIn(0, 0x0FFFFFFF)
        return byteArrayOf(
            ((safe shr 21) and 0x7F).toByte(),
            ((safe shr 14) and 0x7F).toByte(),
            ((safe shr 7) and 0x7F).toByte(),
            (safe and 0x7F).toByte(),
        )
    }

    private fun int32be(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0xFF) shl 24) or
            ((bytes[start + 1].toInt() and 0xFF) shl 16) or
            ((bytes[start + 2].toInt() and 0xFF) shl 8) or
            (bytes[start + 3].toInt() and 0xFF)

    private fun int32beBytes(value: Int): ByteArray =
        byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )

    private fun littleEndianInt32(bytes: ByteArray, start: Int): Int? {
        if (start + 4 > bytes.size) return null
        return (bytes[start].toInt() and 0xFF) or
            ((bytes[start + 1].toInt() and 0xFF) shl 8) or
            ((bytes[start + 2].toInt() and 0xFF) shl 16) or
            ((bytes[start + 3].toInt() and 0xFF) shl 24)
    }

    private fun littleEndianInt32Bytes(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    private fun u32le(bytes: ByteArray, start: Int): Long =
        (bytes[start].toInt() and 0xFF).toLong() or
            ((bytes[start + 1].toInt() and 0xFF).toLong() shl 8) or
            ((bytes[start + 2].toInt() and 0xFF).toLong() shl 16) or
            ((bytes[start + 3].toInt() and 0xFF).toLong() shl 24)

    private fun u64le(bytes: ByteArray, start: Int): Long =
        u32le(bytes, start) or (u32le(bytes, start + 4) shl 32)

    private fun skipExact(input: InputStream, count: Long): Boolean {
        if (count <= 0L) return true
        var left = count
        val buffer = ByteArray(SKIP_BUFFER)
        while (left > 0L) {
            var skipped = input.skip(left)
            if (skipped <= 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read < 0) return false
                skipped = read.toLong()
            }
            left -= skipped
        }
        return true
    }

    private fun InputStream.readExact(size: Int): ByteArray? {
        if (size < 0) return null
        if (size == 0) return ByteArray(0)
        val out = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(out, offset, size - offset)
            if (read < 0) return null
            offset += read
        }
        return out
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private data class ParsedId3(
        val majorVersion: Int,
        val frames: List<Id3Frame>,
    )

    private data class Id3Frame(
        val id: String,
        val flags: ByteArray,
        val payload: ByteArray,
    )

    private data class FlacBlock(
        val type: Int,
        val payload: ByteArray,
    )

    private const val ID3V1_BYTES = 128
    private const val MAX_IN_MEMORY_ID3V1_BYTES = 8 * 1024 * 1024
    private const val COPY_BUFFER = 64 * 1024
    private const val SKIP_BUFFER = 8 * 1024
    private const val MAX_WAV_CHUNKS = 256
    private const val MAX_INFO_BYTES = 64 * 1024
    private const val MAX_TAG_BYTES = 2 * 1024 * 1024
    private const val MAX_METADATA_BYTES = 32 * 1024 * 1024
    private const val MAX_FRAMES = 512
    private const val MAX_FLAC_BLOCKS = 64
    private const val MAX_VORBIS_COMMENTS = 256
    private const val ID3_PADDING = 512
    private const val FLAC_PADDING_SIZE = 4096
    private const val FLAC_STREAMINFO = 0
    private const val FLAC_PADDING = 1
    private const val FLAC_VORBIS_COMMENT = 4
    private const val FLAC_PICTURE = 6
    private const val DEFAULT_VENDOR = "ECHOAndroid"
    private val ManagedId3v23 = setOf("TIT2", "TPE1", "TALB", "TPE2", "TRCK", "TPOS", "TYER", "TDRC")
    private val ManagedId3v24 = setOf("TIT2", "TPE1", "TALB", "TPE2", "TRCK", "TPOS", "TYER", "TDRC")
    private val ManagedVorbisKeys = setOf(
        "TITLE",
        "ARTIST",
        "ALBUM",
        "ALBUMARTIST",
        "ALBUM ARTIST",
        "ALBUM_ARTIST",
        "TRACKNUMBER",
        "TRACK",
        "DISCNUMBER",
        "DISC",
        "DATE",
        "YEAR",
    )
    private val VorbisLyricsKeys = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")
    private val AlbumArtistVorbisKeys = setOf("ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST")
    private val Id3LyricsKeys = VorbisLyricsKeys + "USLT"
    private val V22_FRAME_MAP = mapOf(
        "TT2" to "TIT2",
        "TP1" to "TPE1",
        "TAL" to "TALB",
        "TP2" to "TPE2",
        "TRK" to "TRCK",
        "TPA" to "TPOS",
        "TYE" to "TYER",
        "ULT" to "USLT",
        "SLT" to "SYLT",
        "TXX" to "TXXX",
        "PIC" to "APIC",
        "COM" to "COMM",
        "TCM" to "TCOM",
        "TCO" to "TCON",
        "TEN" to "TENC",
        "TCR" to "TCOP",
        "TP3" to "TPE3",
        "TP4" to "TPE4",
        "TPB" to "TPUB",
        "TLE" to "TLEN",
        "TLA" to "TLAN",
        "TBP" to "TBPM",
        "TKE" to "TKEY",
        "TSS" to "TSSE",
        "TT1" to "TIT1",
        "TT3" to "TIT3",
        "TXT" to "TEXT",
        "TOL" to "TOLY",
        "TOA" to "TOPE",
        "TOT" to "TOAL",
        "TOR" to "TORY",
        "UFI" to "UFID",
        "POP" to "POPM",
        "CNT" to "PCNT",
        "WXX" to "WXXX",
        "WAF" to "WOAF",
        "WAR" to "WOAR",
        "WAS" to "WOAS",
        "WCM" to "WCOM",
        "WCP" to "WCOP",
        "WPB" to "WPUB",
    )
}

private class PrefixInputStream(
    private val prefix: ByteArray,
    private val rest: InputStream,
) : InputStream() {
    private var offset = 0

    override fun read(): Int {
        if (offset < prefix.size) {
            val value = prefix[offset].toInt() and 0xFF
            offset += 1
            return value
        }
        return rest.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len <= 0) return 0
        var written = 0
        while (offset < prefix.size && written < len) {
            b[off + written] = prefix[offset]
            offset += 1
            written += 1
        }
        if (written == len) return written
        val fromRest = rest.read(b, off + written, len - written)
        return when {
            fromRest < 0 && written == 0 -> -1
            fromRest < 0 -> written
            else -> written + fromRest
        }
    }

    override fun skip(n: Long): Long {
        if (n <= 0L) return 0L
        val prefixLeft = (prefix.size - offset).toLong()
        if (prefixLeft <= 0L) return rest.skip(n)
        val fromPrefix = minOf(n, prefixLeft)
        offset += fromPrefix.toInt()
        if (fromPrefix == n) return n
        return fromPrefix + rest.skip(n - fromPrefix)
    }
}

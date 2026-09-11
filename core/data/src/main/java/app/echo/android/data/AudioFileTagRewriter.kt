package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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
                AudioTagRewriteStatus.UnsupportedFormat
            looksLikeMp3Frame(peek, peeked) || mimeLooksLikeMp3(mimeType) -> {
                output.write(buildId3Tag(majorVersion = 4, frames = emptyList(), fields = fields))
                output.write(peek, 0, peeked)
                input.copyTo(output, COPY_BUFFER)
                AudioTagRewriteStatus.Written
            }
            else -> AudioTagRewriteStatus.UnsupportedFormat
        }
    }

    fun readFields(input: InputStream): AudioTagFields? {
        val peek = ByteArray(10)
        val peeked = readFully(input, peek)
        if (peeked <= 0) return null
        return when {
            isId3(peek, peeked) -> readId3ThenMaybeWav(peek, input)
            isFlac(peek, peeked) -> readFlacFields(peek, peeked, input)
            looksLikeRiff(peek, peeked) -> readWavFields(PrefixInputStream(peek.copyOf(peeked), input))
            else -> null
        }
    }

    private fun rewriteId3(
        header: ByteArray,
        input: InputStream,
        output: OutputStream,
        fields: AudioTagFields,
    ): AudioTagRewriteStatus {
        val parsed = parseId3(header, input) ?: return AudioTagRewriteStatus.InvalidSource
        output.write(buildId3Tag(parsed.majorVersion, parsed.frames, fields))
        input.copyTo(output, COPY_BUFFER)
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
        output.write(peek, 0, 4)
        writeFlacBlocks(output, replaceFlacVorbis(blocks, fields))
        rest.copyTo(output, COPY_BUFFER)
        return AudioTagRewriteStatus.Written
    }

    private fun readId3Fields(header: ByteArray, input: InputStream): AudioTagFields? {
        val parsed = parseId3(header, input) ?: return null
        return fieldsFromId3Frames(parsed.frames)
    }

    private fun readId3ThenMaybeWav(header: ByteArray, input: InputStream): AudioTagFields? {
        val id3 = readId3Fields(header, input)
        val next = ByteArray(12)
        val read = readFully(input, next)
        if (!looksLikeRiff(next, read)) return id3?.takeUnless { it.isBlank() }
        val wav = readWavFields(PrefixInputStream(next.copyOf(read), input))
        return mergeAudioTags(preferred = id3, fallback = wav)
    }

    private fun readWavFields(input: InputStream): AudioTagFields? {
        val header = input.readExact(12) ?: return null
        if (!looksLikeRiff(header, 12) || header.decodeToString(8, 12) != "WAVE") return null
        var remaining = (u32le(header, 4) - 4L).coerceAtLeast(0L)
        var info: AudioTagFields? = null
        var embeddedId3: AudioTagFields? = null
        var dataSize64: Long? = null
        var chunks = 0
        while (remaining >= 8L && chunks < MAX_WAV_CHUNKS) {
            val chunkHeader = input.readExact(8) ?: break
            remaining -= 8L
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
                    if (chunkSize in 10L..MAX_TAG_BYTES) {
                        val payload = input.readExact(chunkSize.toInt()) ?: break
                        if (!skipExact(input, padded - chunkSize)) break
                        embeddedId3 = readId3FromBytes(payload)
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
                    if (!skipExact(input, skip)) break
                    if (chunkSize == 0xFFFFFFFFL && (skip and 1L) == 1L && !skipExact(input, 1L)) break
                }
                else -> if (!skipExact(input, padded)) break
            }
            remaining -= if (chunkId == "data" && chunkSize == 0xFFFFFFFFL) {
                val skipped = dataSize64 ?: 0L
                skipped + (skipped and 1L)
            } else {
                padded
            }
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
        )
    }

    private fun readId3FromBytes(data: ByteArray): AudioTagFields? {
        if (data.size < 10 || !isId3(data, 10)) return null
        return readId3Fields(data.copyOfRange(0, 10), data.inputStream(offset = 10, length = data.size - 10))
    }

    private fun readFlacFields(peek: ByteArray, peeked: Int, input: InputStream): AudioTagFields? {
        val rest = PrefixInputStream(peek.copyOfRange(4, peeked), input)
        val blocks = readFlacBlocks(rest) ?: return null
        val vorbis = blocks.firstOrNull { it.type == FLAC_VORBIS_COMMENT }?.payload ?: return null
        return fieldsFromVorbis(vorbis)
    }

    private fun parseId3(header: ByteArray, input: InputStream): ParsedId3? {
        val majorVersion = header[3].toInt() and 0xFF
        if (majorVersion !in 2..4) return null
        val flags = header[5].toInt() and 0xFF
        val tagSize = syncSafeInt(header, 6).takeIf { it in 1..MAX_TAG_BYTES } ?: return null
        val rawBody = input.readExact(tagSize) ?: return null
        val unsynced = flags and 0x80 != 0
        val body = if (unsynced) decodeUnsync(rawBody) else rawBody
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
        return ParsedId3(outputVersion, frames)
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
            val mapped = V22_FRAME_MAP[shortId]
            if (mapped != null && size > 0) {
                frames += Id3Frame(mapped, byteArrayOf(0, 0), body.copyOfRange(offset + 6, offset + 6 + size))
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
        val frames = if (existingTag != null && existingTag.size >= 10 && isId3(existingTag, existingTag.size)) {
            parseId3(
                existingTag.copyOfRange(0, 10),
                existingTag.inputStream(offset = 10, length = existingTag.size - 10),
            )?.frames
        } else {
            emptyList()
        }
        return buildId3Tag(4, frames.orEmpty(), fields)
    }

    private fun replaceId3TextFrames(
        version: Int,
        frames: List<Id3Frame>,
        fields: AudioTagFields,
    ): List<Id3Frame> {
        val managed = HashSet(if (version == 4) ManagedId3v24 else ManagedId3v23)
        if (fields.lyrics != null) managed += "USLT"
        if (fields.artworkBytes != null) managed += "APIC"
        val kept = frames.filterNot { frame ->
            frame.id in managed ||
                (fields.replayGainTrackGainDb != null && isReplayGainTrackTxxx(frame))
        }
        val next = ArrayList<Id3Frame>(kept.size + 10)
        next += textFrame(version, "TIT2", fields.title)
        next += textFrame(version, "TPE1", fields.artist)
        fields.album?.let { next += textFrame(version, "TALB", it) }
        fields.albumArtist?.let { next += textFrame(version, "TPE2", it) }
        fields.trackNumber?.let { next += textFrame(version, "TRCK", it.toString()) }
        fields.discNumber?.let { next += textFrame(version, "TPOS", it.toString()) }
        fields.year?.let { year ->
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

    private fun apicFrame(@Suppress("UNUSED_PARAMETER") version: Int, image: ByteArray, mime: String): Id3Frame {
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

    private fun isReplayGainTrackTxxx(frame: Id3Frame): Boolean {
        if (frame.id != "TXXX") return false
        return txxxDescription(frame.payload).equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)
    }

    private fun txxxDescription(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val encoding = when (payload[0].toInt() and 0xFF) {
            1, 2 -> Charsets.UTF_16
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.ISO_8859_1
        }
        val terminator = if (encoding == Charsets.UTF_16 || encoding == Charsets.UTF_16BE) 2 else 1
        var end = 1
        while (end + terminator <= payload.size) {
            val zero = if (terminator == 2) {
                payload[end] == 0.toByte() && payload[end + 1] == 0.toByte()
            } else {
                payload[end] == 0.toByte()
            }
            if (zero) break
            end++
        }
        return if (end > 1) {
            String(payload, 1, end - 1, encoding).trim('\u0000', ' ')
        } else {
            ""
        }
    }

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

    private fun fieldsFromId3Frames(frames: List<Id3Frame>): AudioTagFields {
        fun text(id: String): String? = frames.firstOrNull { it.id == id }?.payload?.let(::decodeId3Text)
        val yearText = text("TDRC") ?: text("TYER")
        return AudioTagFields(
            title = text("TIT2").orEmpty(),
            artist = text("TPE1").orEmpty(),
            album = text("TALB"),
            albumArtist = text("TPE2"),
            trackNumber = text("TRCK")?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 },
            discNumber = text("TPOS")?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 },
            year = yearText?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
            lyrics = frames.firstOrNull { it.id == "USLT" }?.payload?.let(::decodeUslt),
        )
    }

    private fun decodeId3Text(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val encoding = payload[0].toInt() and 0xFF
        if (payload.size <= 1) return null
        val raw = payload.copyOfRange(1, payload.size)
        val text = when (encoding) {
            1 -> raw.toString(Charsets.UTF_16)
            2 -> raw.toString(Charsets.UTF_16BE)
            3 -> TagTextDecoder.decode(raw) ?: raw.toString(StandardCharsets.UTF_8)
            else -> TagTextDecoder.decode(raw)
        }
        return text?.trimEnd('\u0000')?.trim()?.takeIf { it.isNotEmpty() }
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
            1 -> raw.toString(Charsets.UTF_16)
            2 -> raw.toString(Charsets.UTF_16BE)
            3 -> raw.toString(StandardCharsets.UTF_8)
            else -> TagTextDecoder.decode(raw) ?: raw.toString(StandardCharsets.ISO_8859_1)
        }
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
            if (length !in 0..MAX_BLOCK_BYTES) return null
            total += length
            if (total > MAX_METADATA_BYTES) return null
            val payload = input.readExact(length) ?: return null
            blocks += FlacBlock(type, payload)
            if (isLast) return blocks
        }
        return null
    }

    private fun replaceFlacVorbis(blocks: List<FlacBlock>, fields: AudioTagFields): List<FlacBlock> {
        val existing = blocks.firstOrNull { it.type == FLAC_VORBIS_COMMENT }?.payload
        val vendor = existing?.let(::vorbisVendor).orEmpty().ifBlank { DEFAULT_VENDOR }
        val comments = linkedMapOf<String, String>()
        existing?.let { payload ->
            parseVorbisComments(payload).forEach { (key, value) ->
                val upper = key.uppercase()
                if (upper in ManagedVorbisKeys) return@forEach
                if (fields.lyrics != null && upper in VorbisLyricsKeys) return@forEach
                if (fields.replayGainTrackGainDb != null && upper == "REPLAYGAIN_TRACK_GAIN") return@forEach
                comments[key] = value
            }
        }
        comments["TITLE"] = fields.title
        comments["ARTIST"] = fields.artist
        fields.album?.let { comments["ALBUM"] = it }
        fields.albumArtist?.let { comments["ALBUMARTIST"] = it }
        fields.trackNumber?.let { comments["TRACKNUMBER"] = it.toString() }
        fields.discNumber?.let { comments["DISCNUMBER"] = it.toString() }
        fields.year?.let { comments["DATE"] = it.toString() }
        fields.lyrics?.takeIf { it.isNotBlank() }?.let { comments["LYRICS"] = it }
        fields.replayGainTrackGainDb?.let { comments["REPLAYGAIN_TRACK_GAIN"] = formatReplayGainTag(it) }
        val vorbis = encodeVorbis(vendor, comments)
        val dropPicture = fields.artworkBytes != null
        val kept = blocks.filterNot { block ->
            block.type == FLAC_VORBIS_COMMENT ||
                block.type == FLAC_PADDING ||
                (dropPicture && block.type == FLAC_PICTURE)
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
        if (vendorLength < 0 || 4 + vendorLength > payload.size) return null
        return payload.copyOfRange(4, 4 + vendorLength).toString(StandardCharsets.UTF_8)
    }

    private fun parseVorbisComments(payload: ByteArray): List<Pair<String, String>> {
        val vendorLength = littleEndianInt32(payload, 0) ?: return emptyList()
        if (vendorLength < 0 || 4 + vendorLength > payload.size) return emptyList()
        var cursor = 4 + vendorLength
        val count = littleEndianInt32(payload, cursor) ?: return emptyList()
        cursor += 4
        val comments = ArrayList<Pair<String, String>>(count.coerceAtLeast(0))
        repeat(count.coerceAtMost(MAX_VORBIS_COMMENTS)) {
            val length = littleEndianInt32(payload, cursor) ?: return comments
            cursor += 4
            if (length < 0 || cursor + length > payload.size) return comments
            val comment = payload.copyOfRange(cursor, cursor + length).toString(StandardCharsets.UTF_8)
            cursor += length
            val key = comment.substringBefore('=', missingDelimiterValue = "")
            val value = comment.substringAfter('=', missingDelimiterValue = "")
            if (key.isNotBlank()) comments += key to value
        }
        return comments
    }

    private fun fieldsFromVorbis(payload: ByteArray): AudioTagFields {
        val values = HashMap<String, String>()
        parseVorbisComments(payload).forEach { (key, value) ->
            values[key.uppercase()] = value
        }
        return AudioTagFields(
            title = values["TITLE"].orEmpty(),
            artist = values["ARTIST"].orEmpty(),
            album = values["ALBUM"],
            albumArtist = values["ALBUMARTIST"] ?: values["ALBUM ARTIST"],
            trackNumber = values["TRACKNUMBER"]?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 },
            discNumber = values["DISCNUMBER"]?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 },
            year = (values["DATE"] ?: values["YEAR"])?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
            lyrics = values["LYRICS"] ?: values["UNSYNCEDLYRICS"] ?: values["SYNCEDLYRICS"],
        )
    }

    private fun encodeVorbis(vendor: String, comments: Map<String, String>): ByteArray {
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

    private const val COPY_BUFFER = 64 * 1024
    private const val SKIP_BUFFER = 8 * 1024
    private const val MAX_WAV_CHUNKS = 256
    private const val MAX_INFO_BYTES = 64 * 1024
    private const val MAX_TAG_BYTES = 2 * 1024 * 1024
    private const val MAX_BLOCK_BYTES = 16 * 1024 * 1024
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
        "TRACKNUMBER",
        "DISCNUMBER",
        "DATE",
        "YEAR",
    )
    private val VorbisLyricsKeys = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")
    private val V22_FRAME_MAP = mapOf(
        "TT2" to "TIT2",
        "TP1" to "TPE1",
        "TAL" to "TALB",
        "TP2" to "TPE2",
        "TRK" to "TRCK",
        "TPA" to "TPOS",
        "TYE" to "TYER",
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

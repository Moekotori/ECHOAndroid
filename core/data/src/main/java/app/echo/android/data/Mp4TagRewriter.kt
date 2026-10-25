package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** Preserve every media byte offset. Grow moov at EOF; later edits replace that trailing moov. */
internal object Mp4TagRewriter {
    private data class Atom(val type: String, val offset: Int, val size: Int, val header: Int) {
        fun body(bytes: ByteArray) = bytes.copyOfRange(offset + header, offset + size)
        fun bytes(bytes: ByteArray) = bytes.copyOfRange(offset, offset + size)
    }

    fun rewrite(source: File, destination: File, fields: AudioTagFields): AudioTagRewriteStatus {
        if (fields.title.isBlank() || fields.artist.isBlank()) return AudioTagRewriteStatus.InvalidSource
        RandomAccessFile(source, "r").use { file ->
            var moovOffset = -1L
            var moovSize = 0L
            var moovHeader = 0
            var atoms = 0
            var terminalSizeZero = -1L
            while (file.filePointer < file.length()) {
                if (++atoms > 4096 || file.length() - file.filePointer < 8) return AudioTagRewriteStatus.InvalidSource
                val position = file.filePointer
                val short = file.readInt().toLong() and 0xffffffffL
                val type = ByteArray(4).also(file::readFully).toString(Charsets.ISO_8859_1)
                if (short == 1L && file.length() - position < 16) return AudioTagRewriteStatus.InvalidSource
                val size = when (short) { 0L -> file.length() - position; 1L -> file.readLong(); else -> short }
                val header = if (short == 1L) 16 else 8
                if (size < header || size > file.length() - position) return AudioTagRewriteStatus.InvalidSource
                if (type in setOf("moof", "sidx", "mfra")) return AudioTagRewriteStatus.UnsupportedFormat
                if (short == 0L) terminalSizeZero = position
                if (type == "moov") {
                    if (moovOffset >= 0 || size > MaxMoovBytes) return AudioTagRewriteStatus.UnsupportedFormat
                    moovOffset = position; moovSize = size; moovHeader = header
                }
                file.seek(position + size)
            }
            if (moovOffset < 0) return AudioTagRewriteStatus.UnsupportedFormat
            file.seek(moovOffset)
            val original = ByteArray(moovSize.toInt()).also(file::readFully)
            val body = original.copyOfRange(moovHeader, original.size)
            val children = parse(body) ?: return AudioTagRewriteStatus.InvalidSource
            if (children.any { it.type == "mvex" || it.type == "cmov" }) return AudioTagRewriteStatus.UnsupportedFormat
            val oldFields = Mp4TagReader.read(original.inputStream())
            val metas = mutableListOf<Pair<Atom?, Atom>>()
            for (child in children) {
                if (child.type == "meta") metas += null to child
                if (child.type == "udta") {
                    val udta = child.body(body)
                    val nested = parse(udta) ?: return AudioTagRewriteStatus.InvalidSource
                    nested.filter { it.type == "meta" }.forEach { metas += child to it }
                }
            }
            if (metas.size > 1) return AudioTagRewriteStatus.UnsupportedFormat
            val nextBody = ByteArrayOutputStream()
            var inserted = false
            for (child in children) {
                val selected = metas.singleOrNull()
                when {
                    selected?.first == null && selected?.second == child -> {
                        val meta = rewriteMeta(child.body(body), fields, oldFields) ?: return AudioTagRewriteStatus.UnsupportedFormat
                        nextBody.write(atom("meta", meta)); inserted = true
                    }
                    selected != null && selected.first == child -> {
                        val udta = child.body(body)
                        val nested = parse(udta) ?: return AudioTagRewriteStatus.InvalidSource
                        val updated = ByteArrayOutputStream()
                        for (item in nested) {
                            if (item == selected.second) {
                                val meta = rewriteMeta(item.body(udta), fields, oldFields) ?: return AudioTagRewriteStatus.UnsupportedFormat
                                updated.write(atom("meta", meta))
                            } else updated.write(item.bytes(udta))
                        }
                        nextBody.write(atom("udta", updated.toByteArray())); inserted = true
                    }
                    else -> nextBody.write(child.bytes(body))
                }
            }
            if (!inserted) {
                val meta = rewriteMeta(ByteArray(4), fields, oldFields) ?: return AudioTagRewriteStatus.UnsupportedFormat
                nextBody.write(atom("udta", atom("meta", meta)))
            }
            val updated = atom("moov", nextBody.toByteArray())
            if (updated.size > MaxMoovBytes) return AudioTagRewriteStatus.UnsupportedFormat
            val trailing = moovOffset + moovSize == file.length()
            val leftover = moovSize - updated.size
            val fits = leftover == 0L || leftover >= 8L
            // A size-zero mdat would consume a newly appended moov. Decline before output.
            if (!trailing && !fits && terminalSizeZero >= 0) return AudioTagRewriteStatus.UnsupportedFormat
            destination.outputStream().buffered().use { output ->
                file.seek(0)
                copy(file, output, moovOffset)
                if (trailing || fits) {
                    output.write(updated)
                    if (!trailing && leftover > 0) output.write(atom("free", ByteArray((leftover - 8).toInt())))
                } else {
                    // Keep the old moov's size/header and free its payload, preserving stco/co64.
                    val freed = original.copyOf()
                    "free".toByteArray().copyInto(freed, 4)
                    output.write(freed)
                }
                file.seek(moovOffset + moovSize)
                copy(file, output, file.length() - file.filePointer)
                if (!trailing && !fits) output.write(updated)
            }
            return AudioTagRewriteStatus.Written
        }
    }

    private fun rewriteMeta(body: ByteArray, fields: AudioTagFields, old: AudioTagFields?): ByteArray? {
        if (body.size < 4 || body.take(4).any { it != 0.toByte() }) return null
        val childrenBytes = body.copyOfRange(4, body.size)
        val children = parse(childrenBytes) ?: return null
        if (children.count { it.type == "ilst" } > 1 || children.any { it.type == "keys" }) return null
        val handler = children.firstOrNull { it.type == "hdlr" }?.body(childrenBytes)
        if (handler != null && (handler.size < 12 || handler.copyOfRange(8, 12).toString(Charsets.US_ASCII) != "mdir")) return null
        val existing = children.firstOrNull { it.type == "ilst" }?.body(childrenBytes) ?: byteArrayOf()
        val items = rewriteItems(existing, fields, old) ?: return null
        val output = ByteArrayOutputStream()
        output.write(ByteArray(4))
        if (handler == null) output.write(atom("hdlr", ByteArray(8) + "mdirappl".toByteArray() + ByteArray(9)))
        children.filterNot { it.type == "ilst" }.forEach { output.write(it.bytes(childrenBytes)) }
        output.write(atom("ilst", items))
        return output.toByteArray()
    }

    private fun rewriteItems(bytes: ByteArray, fields: AudioTagFields, old: AudioTagFields?): ByteArray? {
        val items = parse(bytes) ?: return null
        val replace = mutableSetOf("©nam", "©ART", "©alb", "aART", "trkn", "disk", "©day")
        if (preserveTagNames(fields.artist, old?.artist)) replace -= "©ART"
        if (preserveTagNames(fields.albumArtist, old?.albumArtist)) replace -= "aART"
        if (shouldReplaceOptionalTextTag(fields.composer, fields.composerModified, old?.composer)) replace += "©wrt"
        if (shouldReplaceOptionalTextTag(fields.genre, fields.genreModified, old?.genre)) replace += setOf("©gen", "gnre")
        if (fields.lyrics != null) replace += "©lyr"
        if (fields.artworkBytes != null) replace += "covr"
        val output = ByteArrayOutputStream()
        for (item in items) {
            val freeKey = if (item.type == "----") freeKey(item.body(bytes)) else null
            val dropFree = (fields.lyrics != null && freeKey in setOf("LYRICS", "UNSYNCEDLYRICS")) ||
                (fields.replayGainTrackGainDb != null && freeKey == "REPLAYGAIN_TRACK_GAIN")
            if (item.type !in replace && !dropFree) output.write(item.bytes(bytes))
        }
        fun value(key: String): ByteArray? {
            val item = items.firstOrNull { it.type == key } ?: return null
            val body = item.body(bytes)
            val data = parse(body)?.firstOrNull { it.type == "data" }?.body(body) ?: return null
            return data.takeIf { it.size >= 8 }?.copyOfRange(8, data.size)
        }
        fun add(key: String, type: Int, value: ByteArray) { output.write(atom(key, atom("data", be(type) + be(0) + value))) }
        fun text(key: String, value: String?) { if (!value.isNullOrBlank()) add(key, 1, value.toByteArray(Charsets.UTF_8)) }
        text("©nam", fields.title)
        if ("©ART" in replace) text("©ART", fields.artist)
        text("©alb", fields.album)
        if ("aART" in replace) text("aART", fields.albumArtist)
        if ("©wrt" in replace) text("©wrt", fields.composer)
        if ("©gen" in replace) text("©gen", fields.genre)
        if (fields.lyrics != null) text("©lyr", fields.lyrics)
        val originalDate = value("©day")?.toString(Charsets.UTF_8)
        text("©day", tagDateWithYear(fields.year, originalDate))
        for ((key, number) in listOf("trkn" to fields.trackNumber, "disk" to fields.discNumber)) {
            if (number != null) {
                if (number !in 1..65535) return null
                val previous = value(key)
                val data = ByteArray(if (key == "trkn") 8 else 6)
                data[2] = (number ushr 8).toByte(); data[3] = number.toByte()
                if (previous != null && previous.size >= 6) { data[4] = previous[4]; data[5] = previous[5] }
                add(key, 0, data)
            }
        }
        fields.artworkBytes?.takeIf { it.isNotEmpty() }?.let {
            if (it.size > MaxEmbeddedArtworkBytes) return null
            val type = when (fields.artworkMime) { "image/png" -> 14; "image/jpeg", null, "" -> 13; else -> return null }
            add("covr", type, it)
        }
        fields.replayGainTrackGainDb?.let {
            val name = atom("mean", be(0) + "com.apple.iTunes".toByteArray()) +
                atom("name", be(0) + "replaygain_track_gain".toByteArray())
            val data = atom("data", be(1) + be(0) + String.format(java.util.Locale.ROOT, "%.2f dB", it).toByteArray())
            output.write(atom("----", name + data))
        }
        return output.toByteArray()
    }

    private fun freeKey(body: ByteArray): String? {
        val children = parse(body) ?: return null
        fun text(key: String) = children.firstOrNull { it.type == key }?.body(body)?.takeIf { it.size >= 4 }?.let { it.copyOfRange(4, it.size).toString(Charsets.UTF_8) }
        return text("name")?.uppercase(java.util.Locale.ROOT)?.takeIf { text("mean") == "com.apple.iTunes" }
    }

    private fun parse(bytes: ByteArray): List<Atom>? {
        val result = ArrayList<Atom>()
        var position = 0
        while (position < bytes.size) {
            if (result.size >= 4096 || bytes.size - position < 8) return null
            val short = bytes.tagU32(position)
            val header = if (short == 1L) 16 else 8
            if (bytes.size - position < header) return null
            val size = when (short) {
                0L -> (bytes.size - position).toLong()
                1L -> ByteBuffer.wrap(bytes, position + 8, 8).long
                else -> short
            }
            if (size < header || size > bytes.size - position) return null
            result += Atom(bytes.copyOfRange(position + 4, position + 8).toString(Charsets.ISO_8859_1), position, size.toInt(), header)
            position += size.toInt()
        }
        return result
    }

    private fun atom(type: String, body: ByteArray) = be(body.size + 8) + type.toByteArray(Charsets.ISO_8859_1) + body
    private fun be(n: Int) = ByteBuffer.allocate(4).putInt(n).array()
    private fun copy(input: RandomAccessFile, output: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var left = size
        while (left > 0) {
            val count = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (count <= 0) throw java.io.EOFException()
            output.write(buffer, 0, count); left -= count
        }
    }
    private const val MaxMoovBytes = 16 * 1024 * 1024
}

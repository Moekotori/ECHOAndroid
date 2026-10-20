package app.echo.android.data

import app.echo.android.data.LocalAudioContainerMetadata.le16
import app.echo.android.data.LocalAudioContainerMetadata.le32
import app.echo.android.data.LocalAudioContainerMetadata.le64
import java.io.InputStream
import java.util.Locale

/** Reads the bounded ASF header only; packet data is never visited. */
internal object AsfMetadataReader {
    fun read(input: InputStream): LocalAudioContainerInfo? {
        val header = input.tagBytes(30) ?: return null
        if (!header.copyOfRange(0, 16).contentEquals(LocalAudioContainerMetadata.AsfHeader)) return null
        val size = le64(header, 16)
        val count = le32(header, 24)
        if (size !in 30L..2L * 1024 * 1024 || count !in 1L..256L) return null
        var remaining = size - 30
        var duration = 0L
        var rate: Int? = null
        val fields = linkedMapOf<String, String>()
        repeat(count.toInt()) {
            if (remaining < 24) return null
            val objectHeader = input.tagBytes(24) ?: return null
            val objectSize = le64(objectHeader, 16)
            if (objectSize < 24 || objectSize > remaining) return null
            val bytes = input.tagBytes((objectSize - 24).toInt()) ?: return null
            val guid = objectHeader.copyOfRange(0, 16).joinToString("") { "%02x".format(it.toInt() and 255) }
            when (guid) {
                "a1dcab8c47a9cf118ee400c00c205365" -> {
                    if (bytes.size < 80) return null
                    val play = le64(bytes, 40)
                    val preroll = le64(bytes, 56)
                    val broadcast = le32(bytes, 64) and 1L != 0L
                    if (!broadcast && play >= 0 && preroll >= 0) duration = (play / 10000 - preroll).coerceAtLeast(0)
                }
                "9107dcb7b7a9cf118ee600c00c205365" -> {
                    if (bytes.size < 54) return null
                    val audio = bytes.copyOfRange(0, 16).contentEquals(AudioStream)
                    val typeLength = le32(bytes, 40)
                    if (audio && typeLength >= 16 && typeLength <= bytes.size - 54) {
                        rate = le32(bytes, 58).toInt().takeIf { it in 1..768000 }
                    }
                }
                "3326b2758e66cf11a6d900aa0062ce6c" -> {
                    if (bytes.size < 10) return null
                    var offset = 10
                    listOf("title", "author", "copyright", "description", "rating").forEachIndexed { index, key ->
                        val length = le16(bytes, index * 2)
                        if (length > bytes.size - offset || length and 1 != 0) return null
                        fields.putIfAbsent(key, utf16(bytes, offset, length))
                        offset += length
                    }
                }
                "40a4d0d207e3d21197f000a0c95ea850" -> {
                    if (bytes.size < 2) return null
                    val descriptors = le16(bytes, 0)
                    if (descriptors > 1024) return null
                    var offset = 2
                    repeat(descriptors) {
                        if (offset > bytes.size - 2) return null
                        val nameSize = le16(bytes, offset)
                        offset += 2
                        if (nameSize > bytes.size - offset - 4 || nameSize and 1 != 0) return null
                        val key = utf16(bytes, offset, nameSize).lowercase(Locale.ROOT)
                        offset += nameSize
                        val type = le16(bytes, offset)
                        val length = le16(bytes, offset + 2)
                        offset += 4
                        if (length > bytes.size - offset) return null
                        val value = when {
                            type == 0 && length and 1 == 0 -> utf16(bytes, offset, length)
                            type == 3 && length == 4 -> le32(bytes, offset).toString()
                            type == 5 && length == 2 -> le16(bytes, offset).toString()
                            else -> null
                        }
                        if (value != null) fields.putIfAbsent(key, value)
                        offset += length
                    }
                }
            }
            remaining -= objectSize
        }
        fun text(vararg keys: String) = keys.firstNotNullOfOrNull { fields[it]?.takeIf(String::isNotBlank) }
        fun number(key: String) = text(key)?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val tags = AudioTagFields(
            title = text("title").orEmpty(), artist = text("author", "wm/artist").orEmpty(),
            album = text("wm/albumtitle"), albumArtist = text("wm/albumartist"),
            trackNumber = number("wm/tracknumber"), discNumber = number("wm/partofset"),
            year = text("wm/year")?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
            genre = text("wm/genre"), composer = text("wm/composer"), lyrics = text("wm/lyrics"),
        ).takeUnless { it.isBlank() }
        return if (duration > 0 || rate != null || tags != null) LocalAudioContainerInfo(duration, rate, tags) else null
    }

    private fun utf16(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.UTF_16LE).trimEnd('\u0000').trim()
    private val AudioStream = byteArrayOf(0x40, -0x62, 0x69, -0x08, 0x4d, 0x5b, -0x31, 0x11, -0x58, -0x03, 0, -0x80, 0x5f, 0x5c, 0x44, 0x2b)
}

package app.echo.android.data

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** CUE is a complete document: a NUL or malformed Unicode must never truncate it. */
internal object CueTextDecoder {
    fun decode(bytes: ByteArray, referenceExists: ((String) -> Boolean)? = null): String? {
        fun starts(vararg values: Int) = bytes.size >= values.size &&
            values.indices.all { bytes[it].toInt() and 255 == values[it] }
        val utf32 = when {
            starts(255, 254, 0, 0) -> true to 4
            starts(0, 0, 254, 255) -> false to 4
            bytes.size >= 8 && bytes[0] != 0.toByte() && starts(bytes[0].toInt() and 255, 0, 0, 0) -> true to 0
            bytes.size >= 8 && starts(0, 0, 0) && bytes[3] != 0.toByte() -> false to 0
            else -> null
        }
        val decoded = if (utf32 != null) {
            val (little, offset) = utf32
            if ((bytes.size - offset) % 4 != 0) return null
            val text = StringBuilder(bytes.size / 4)
            for (at in offset until bytes.size step 4) {
                val code = (0..3).fold(0L) { value, i ->
                    value or ((bytes[at + i].toLong() and 255) shl (if (little) i * 8 else (3 - i) * 8))
                }
                if (code > 0x10ffff || code in 0xd800..0xdfff) return null
                text.appendCodePoint(code.toInt())
            }
            text.toString()
        } else {
            val encoding = when {
                starts(239, 187, 191) -> Charsets.UTF_8
                starts(255, 254) -> Charsets.UTF_16LE
                starts(254, 255) -> Charsets.UTF_16BE
                bytes.size >= 4 && bytes[0] != 0.toByte() && bytes[1] == 0.toByte() && bytes[3] == 0.toByte() -> Charsets.UTF_16LE
                bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] != 0.toByte() && bytes[2] == 0.toByte() -> Charsets.UTF_16BE
                else -> null
            }
            if (encoding == null) {
                // Legacy guessing is only appropriate for unmarked, non-Unicode text.
                if (bytes.any { it == 0.toByte() }) return null
                legacy(bytes, referenceExists)
            } else strict(bytes, encoding)
        }
        return decoded?.trimStart('\uFEFF')?.takeIf { text ->
            text.none { it == '\u0000' || it == '\uFFFD' || (it.isISOControl() && it !in "\r\n\t") }
        }
    }

    private fun legacy(bytes: ByteArray, referenceExists: ((String) -> Boolean)?): String? {
        val primary = TagTextDecoder.decode(bytes)
        if (referenceExists == null || bytes.all { it >= 0 }) return primary
        fun score(text: String?): Pair<Int, Int> {
            val sheet = text?.let(CueSheetParser::parseText) ?: return -1 to 0
            val references = (listOfNotNull(sheet.fileName) + sheet.tracks.mapNotNull { it.fileName })
                .distinct().filter { name -> name.any { it.code > 127 } }.take(32)
            return references.count(referenceExists) to references.size
        }
        var best = primary
        val (initial, count) = score(primary)
        if (initial >= 0 && initial == count) return primary
        var matches = initial
        // Exact source names are stronger evidence than language guesses. Ties keep the primary.
        for (name in listOf("UTF-8", "GB18030", "Big5", "Shift_JIS", "EUC-KR", "windows-1251", "windows-1250", "windows-1252")) {
            val charset = try { Charset.forName(name) } catch (_: IllegalArgumentException) { continue }
            val candidate = strict(bytes, charset) ?: continue
            if (candidate == primary) continue
            val (next, _) = score(candidate)
            if (next > matches) { best = candidate; matches = next }
        }
        return best
    }

    private fun strict(bytes: ByteArray, encoding: Charset): String? = try {
        encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) { null }
}

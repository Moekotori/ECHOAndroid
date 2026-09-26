package app.echo.android.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * WAV LIST/INFO and ID3 encoding 0 have no reliable charset flag.
 * Prefer UTF-8, then CJK encodings when they produce real CJK text, else Latin-1.
 */
internal object TagTextDecoder {
    fun decode(bytes: ByteArray): String? {
        // A UTF-16 code unit can end in 00; never trim bytes before detecting Unicode.
        when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> return decodeDeclared(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
            bytes.startsWith(0xFF, 0xFE) -> return decodeDeclared(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16LE)
            bytes.startsWith(0xFE, 0xFF) -> return decodeDeclared(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16BE)
        }
        var unicodeEnd = bytes.size
        while (unicodeEnd >= 2 && bytes[unicodeEnd - 1] == 0.toByte() && bytes[unicodeEnd - 2] == 0.toByte()) unicodeEnd -= 2
        val unicodePayload = if (unicodeEnd == bytes.size) bytes else bytes.copyOf(unicodeEnd)
        if (unicodePayload.looksLikeUtf16LittleEndian()) {
            decodeDeclared(bytes, StandardCharsets.UTF_16LE)?.let { return it }
        }
        if (unicodePayload.looksLikeUtf16BigEndian()) {
            decodeDeclared(bytes, StandardCharsets.UTF_16BE)?.let { return it }
        }
        val payload = bytes.trimTagPadding()
        if (payload.isEmpty()) return ""

        decodeStrict(payload, StandardCharsets.UTF_8)?.takeIf { it.isReadableText() }?.let { return it.trim() }

        val gbk = decodeStrict(payload, Gb18030)?.takeIf { it.isReadableText() }
            ?: decodeStrict(payload, Gbk)?.takeIf { it.isReadableText() }
        val shiftJis = ShiftJis?.let { charset -> decodeStrict(payload, charset)?.takeIf { it.isReadableText() } }
        val latin = decodeStrict(payload, StandardCharsets.ISO_8859_1)?.takeIf { it.isReadableText() }
            ?: decodeStrict(payload, Windows1252)?.takeIf { it.isReadableText() }

        return pickBest(payload, gbk, shiftJis, latin)?.trim()
    }

    /** Honor a declared encoding; malformed Unicode must not be guessed as another language. */
    fun decodeDeclared(bytes: ByteArray, charset: Charset): String? =
        decodeStrict(bytes, charset)?.substringBefore('\u0000')?.trimStart('\uFEFF')
            ?.takeIf { it.isReadableText() }?.trim()

    private fun pickBest(bytes: ByteArray, gbk: String?, shiftJis: String?, latin: String?): String? {
        val asciiTrails = doubleByteAsciiTrailCount(bytes)
        val cjkCandidate = listOfNotNull(gbk, shiftJis).maxByOrNull { it.scriptScore() }
        if (shiftJis != null && shiftJis.kanaCount() > (gbk?.kanaCount() ?: 0)) return shiftJis
        val cjkCount = cjkCandidate?.cjkScriptCount() ?: 0
        val latinIsWestern = latin != null && latin.all {
            it.isLetterOrDigit() || it.isWhitespace() || it in "'’‘\"“”.,:;!?-–—()/&"
        }
        if (asciiTrails > 0 && latinIsWestern) return latin
        val latinLetters = latin?.count { it.isLetter() && it.code < 0x80 } ?: 0
        return when {
            cjkCandidate == null -> latin
            cjkCount >= 2 -> cjkCandidate
            cjkCount == 1 && (cjkCandidate.length <= 3 || latinLetters < 3) -> cjkCandidate
            latin != null -> latin
            else -> cjkCandidate
        }
    }

    private fun doubleByteAsciiTrailCount(bytes: ByteArray): Int {
        var index = 0
        var count = 0
        while (index < bytes.size) {
            val lead = bytes[index].toInt() and 0xFF
            if (lead < 0x80) {
                index += 1
                continue
            }
            if (index + 1 >= bytes.size) break
            val trail = bytes[index + 1].toInt() and 0xFF
            if (trail in 0x41..0x5A || trail in 0x61..0x7A) count += 1
            index += 2
        }
        return count
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .trimEnd('\u0000')
        } catch (_: CharacterCodingException) {
            null
        }

    private fun ByteArray.trimTagPadding(): ByteArray {
        var end = size
        while (end > 0 && this[end - 1] == 0.toByte()) end -= 1
        if (end == size) return this
        return if (end <= 0) ByteArray(0) else copyOfRange(0, end)
    }

    private fun ByteArray.startsWith(vararg values: Int): Boolean =
        size >= values.size && values.indices.all { index -> this[index].toInt() and 0xFF == values[index] }

    private fun ByteArray.looksLikeUtf16LittleEndian(): Boolean =
        size >= 4 && size % 2 == 0 && zeroRatio(startIndex = 1) >= Utf16ZeroRatioThreshold

    private fun ByteArray.looksLikeUtf16BigEndian(): Boolean =
        size >= 4 && size % 2 == 0 && zeroRatio(startIndex = 0) >= Utf16ZeroRatioThreshold

    private fun ByteArray.zeroRatio(startIndex: Int): Float {
        var total = 0
        var zeros = 0
        var index = startIndex
        val sampleSize = minOf(size, 512)
        while (index < sampleSize) {
            total += 1
            if (this[index].toInt() == 0) zeros += 1
            index += 2
        }
        return if (total == 0) 0f else zeros.toFloat() / total.toFloat()
    }

    private fun String.isReadableText(): Boolean {
        if (isEmpty()) return true
        return none { char ->
            char == '\u0000' ||
                char == '\uFFFD' ||
                (char.isISOControl() && char != '\n' && char != '\r' && char != '\t')
        }
    }

    private fun String.kanaCount(): Int = count { it in '\u3041'..'\u3096' || it in '\u30A1'..'\u30FA' }

    private fun String.cjkScriptCount(): Int = count { it.isCjkScript() }

    private fun String.scriptScore(): Int {
        var cjk = 0
        var latinExt = 0
        var asciiLetters = 0
        for (char in this) {
            when {
                char == '\uFFFD' -> return Int.MIN_VALUE
                char.isCjkScript() -> cjk += 1
                char.code in 0xC0..0x024F -> latinExt += 1
                char.isLetter() && char.code < 0x80 -> asciiLetters += 1
            }
        }
        return cjk * 8 + asciiLetters - latinExt * 3
    }

    private fun Char.isCjkScript(): Boolean =
        this in '\u3400'..'\u4DBF' ||
            this in '\u4E00'..'\u9FFF' ||
            this in '\uF900'..'\uFAFF' ||
            this in '\u3040'..'\u30FF' ||
            this in '\uAC00'..'\uD7AF' ||
            this in '\u31F0'..'\u31FF'

    private val Gb18030: Charset = Charset.forName("GB18030")
    private val Gbk: Charset = Charset.forName("GBK")
    private val Windows1252: Charset = Charset.forName("windows-1252")
    private val ShiftJis: Charset? = runCatching { Charset.forName("Shift_JIS") }.getOrNull()
    private const val Utf16ZeroRatioThreshold = 0.3f
}

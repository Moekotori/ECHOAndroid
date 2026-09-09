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
        val payload = bytes.trimTagPadding()
        if (payload.isEmpty()) return ""

        decodeBom(payload)?.let { return it }

        decodeStrict(payload, StandardCharsets.UTF_8)?.takeIf { it.isReadableText() }?.let { return it.trim() }

        if (payload.looksLikeUtf16LittleEndian()) {
            decodeStrict(payload, StandardCharsets.UTF_16LE)?.takeIf { it.isReadableText() }?.let { return it.trim() }
        }
        if (payload.looksLikeUtf16BigEndian()) {
            decodeStrict(payload, StandardCharsets.UTF_16BE)?.takeIf { it.isReadableText() }?.let { return it.trim() }
        }

        val gbk = decodeStrict(payload, Gb18030)?.takeIf { it.isReadableText() }
            ?: decodeStrict(payload, Gbk)?.takeIf { it.isReadableText() }
        val shiftJis = ShiftJis?.let { charset -> decodeStrict(payload, charset)?.takeIf { it.isReadableText() } }
        val latin = String(payload, StandardCharsets.ISO_8859_1).trimEnd('\u0000').takeIf { it.isReadableText() }

        return pickBest(payload, gbk, shiftJis, latin)?.trim()
    }

    private fun pickBest(bytes: ByteArray, gbk: String?, shiftJis: String?, latin: String?): String? {
        val asciiTrails = doubleByteAsciiTrailCount(bytes)
        val cjkCandidate = listOfNotNull(gbk, shiftJis).maxByOrNull { it.scriptScore() }
        val cjkCount = cjkCandidate?.cjkScriptCount() ?: 0
        val latinIsWestern = latin != null && latin.cjkScriptCount() == 0
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

    private fun decodeBom(bytes: ByteArray): String? =
        when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) ->
                decodeStrict(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
            bytes.startsWith(0xFF, 0xFE) ->
                decodeStrict(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16LE)
            bytes.startsWith(0xFE, 0xFF) ->
                decodeStrict(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16BE)
            else -> null
        }?.takeIf { it.isReadableText() }?.trim()

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
        size >= 6 && zeroRatio(startIndex = 1) >= Utf16ZeroRatioThreshold

    private fun ByteArray.looksLikeUtf16BigEndian(): Boolean =
        size >= 6 && zeroRatio(startIndex = 0) >= Utf16ZeroRatioThreshold

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
        val suspicious = count { char ->
            char == '\u0000' ||
                char == '\uFFFD' ||
                (char.isISOControl() && char != '\n' && char != '\r' && char != '\t')
        }
        return suspicious <= maxOf(1, length / 100)
    }

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
    private val ShiftJis: Charset? = runCatching { Charset.forName("Shift_JIS") }.getOrNull()
    private const val Utf16ZeroRatioThreshold = 0.3f
}

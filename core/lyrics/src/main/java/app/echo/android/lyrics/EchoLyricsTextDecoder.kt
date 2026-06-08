package app.echo.android.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object EchoLyricsTextDecoder {
    fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""

        decodeBom(bytes)?.let { return it }

        decodeStrict(bytes, StandardCharsets.UTF_8)?.takeIf { it.isReadableText() }?.let { return it }

        if (bytes.looksLikeUtf16LittleEndian()) {
            decodeStrict(bytes, StandardCharsets.UTF_16LE)?.takeIf { it.isReadableText() }?.let { return it }
        }
        if (bytes.looksLikeUtf16BigEndian()) {
            decodeStrict(bytes, StandardCharsets.UTF_16BE)?.takeIf { it.isReadableText() }?.let { return it }
        }

        ChineseCompatibleCharsets.forEach { charset ->
            decodeStrict(bytes, charset)?.takeIf { it.isReadableText() }?.let { return it }
        }

        return decodeLenient(bytes, Gb18030)?.takeIf { it.isReadableText() }
            ?: decodeLenient(bytes, StandardCharsets.UTF_8)?.takeIf { it.isReadableText() }
    }

    private fun decodeBom(bytes: ByteArray): String? =
        when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> {
                decodeStrict(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
            }
            bytes.startsWith(0xFF, 0xFE) -> {
                decodeStrict(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16LE)
            }
            bytes.startsWith(0xFE, 0xFF) -> {
                decodeStrict(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16BE)
            }
            else -> null
        }?.takeIf { it.isReadableText() }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun decodeLenient(bytes: ByteArray, charset: Charset): String? =
        runCatching { String(bytes, charset) }.getOrNull()

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
        val sample = take(4096)
        if (sample.isBlank()) return true
        val suspicious = sample.count { char ->
            char == '\u0000' ||
                char == '\uFFFD' ||
                (char.isISOControl() && char != '\n' && char != '\r' && char != '\t')
        }
        return suspicious <= maxOf(1, sample.length / 100)
    }

    private val Gb18030: Charset = Charset.forName("GB18030")
    private val Gbk: Charset = Charset.forName("GBK")
    private val ChineseCompatibleCharsets = listOf(Gb18030, Gbk)
    private const val Utf16ZeroRatioThreshold = 0.3f
}

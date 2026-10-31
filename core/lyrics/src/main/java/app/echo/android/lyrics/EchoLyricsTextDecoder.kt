package app.echo.android.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.mozilla.universalchardet.UniversalDetector

object EchoLyricsTextDecoder {
    data class Result(val text: String, val charset: String, val lossy: Boolean = false) {
        fun attach(lyrics: app.echo.android.model.lyrics.EchoLyrics) = lyrics.copy(metadata = lyrics.metadata +
            mapOf("text_encoding" to charset, "text_decoding_lossy" to lossy.toString()))
    }
    fun decode(bytes: ByteArray): String? = decodeResult(bytes)?.text
    /** Explicit repair for unmarked text. BOM and compressed KRC retain their declared format. */
    fun decodeResult(bytes: ByteArray, charsetName: String?): Result? {
        if (charsetName == null) return decodeResult(bytes)
        require(charsetName in app.echo.android.model.library.EchoTextEncodings.names)
        if (bytes.startsWith(0xEF, 0xBB, 0xBF) || bytes.startsWith(0xFF, 0xFE) || bytes.startsWith(0xFE, 0xFF) ||
            bytes.take(4) == "krc1".toByteArray().toList()) return decodeResult(bytes)
        val charset = Charset.forName(charsetName)
        return decodeStrict(bytes, charset)?.takeIf { it.isReadableText() }?.let { Result(it, charset.name()) }
    }
    fun decodeResult(bytes: ByteArray): Result? {
        if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("krc1".toByteArray())) {
            return decodeKrc(bytes)?.takeIf { it.isReadableText() }?.let { Result(it, "UTF-8") }
        }
        if (bytes.isEmpty()) return Result("", "UTF-8")
        val bom = when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> StandardCharsets.UTF_8 to 3
            bytes.startsWith(0xFF, 0xFE) -> StandardCharsets.UTF_16LE to 2
            bytes.startsWith(0xFE, 0xFF) -> StandardCharsets.UTF_16BE to 2
            else -> null
        }
        // A declared Unicode encoding must never fall through to an unrelated legacy charset.
        if (bom != null) return decodeStrict(bytes.copyOfRange(bom.second, bytes.size), bom.first)
            ?.takeIf { it.isReadableText() }?.let { Result(it, bom.first.name()) }
        if (bytes.looksLikeUtf16LittleEndian()) decodeStrict(bytes, StandardCharsets.UTF_16LE)
            ?.takeIf { it.isReadableText() }?.let { return Result(it, "UTF-16LE") }
        if (bytes.looksLikeUtf16BigEndian()) decodeStrict(bytes, StandardCharsets.UTF_16BE)
            ?.takeIf { it.isReadableText() }?.let { return Result(it, "UTF-16BE") }
        decodeStrict(bytes, StandardCharsets.UTF_8)?.takeIf { it.isReadableText() }?.let { return Result(it, "UTF-8") }
        val detected = detectLegacyCharset(bytes)
        // A few GB2312 pairs often get a spurious Latin/Cyrillic guess. Preserve the
        // established Chinese fallback for that small, structurally valid sample.
        if (detected?.name() !in EastAsianCharsets && bytes.isShortChineseSample()) {
            decodeStrict(bytes, Gb18030)?.takeIf { it.isReadableText() }?.let { return Result(it, Gb18030.name()) }
        }
        detected?.let { charset ->
            decodeStrict(bytes, charset)?.takeIf { it.isReadableText() }?.let { return Result(it, charset.name()) }
        }
        ChineseCompatibleCharsets.forEach { charset ->
            decodeStrict(bytes, charset)?.takeIf { it.isReadableText() }?.let { return Result(it, charset.name()) }
        }
        return decodeLenient(bytes, Gb18030)?.takeIf { it.isReadableText() }?.let { Result(it, Gb18030.name(), true) }
            ?: decodeLenient(bytes, StandardCharsets.UTF_8)?.takeIf { it.isReadableText() }?.let { Result(it, "UTF-8", true) }
    }

    private fun detectLegacyCharset(bytes: ByteArray): Charset? {
        val detector = UniversalDetector()
        // Long ASCII metadata must not consume the entire detection sample. Include
        // the first non-ASCII byte (or ISO-2022 escape), retaining a little context.
        val firstEncodedByte = bytes.indexOfFirst { it < 0 || it == 0x1B.toByte() }
        val start = (firstEncodedByte - 256).coerceAtLeast(0)
        detector.handleData(bytes, start, minOf(bytes.size - start, 64 * 1024))
        detector.dataEnd()
        return detector.detectedCharset?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
    }

    private fun ByteArray.isShortChineseSample(): Boolean {
        var index = 0
        var pairs = 0
        while (index < size) {
            val lead = this[index].toInt() and 255
            if (lead < 128) { index++; continue }
            if (index + 1 >= size || lead !in 0xA1..0xF7 || (this[index + 1].toInt() and 255) !in 0xA1..0xFE) return false
            if (++pairs > 8) return false
            index += 2
        }
        return pairs > 0
    }

    private fun decodeKrc(bytes: ByteArray): String? = runCatching {
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val payload = ByteArray(bytes.size - 4) { i -> (bytes[i + 4].toInt() xor key[i % key.size]).toByte() }
        java.util.zip.InflaterInputStream(payload.inputStream()).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 2 * 1024 * 1024) { "Decompressed lyrics exceed 2 MB" }
                output.write(buffer, 0, count)
            }
            decodeStrict(output.toByteArray(), StandardCharsets.UTF_8)
        }
    }.getOrNull()

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
        size >= 2 && size % 2 == 0 && zeroRatio(startIndex = 1) >= Utf16ZeroRatioThreshold

    private fun ByteArray.looksLikeUtf16BigEndian(): Boolean =
        size >= 2 && size % 2 == 0 && zeroRatio(startIndex = 0) >= Utf16ZeroRatioThreshold

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
        if ('\u0000' in this) return false
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
    private val EastAsianCharsets = setOf("GB18030", "GB2312", "GBK", "Big5", "Shift_JIS", "EUC-JP", "EUC-KR", "x-windows-949")
    private const val Utf16ZeroRatioThreshold = 0.3f
}

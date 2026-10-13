package app.echo.android.data

import java.io.InputStream

internal data class EchoApeScanInfo(val durationMs: Long, val sampleRateHz: Int)

/** Reads only the small audio header; Android's metadata retriever need not support APE. */
internal object EchoApeMetadata {
    fun isApe(mimeType: String?, name: String?): Boolean =
        mimeType?.substringBefore(';')?.trim()?.lowercase() in setOf("audio/ape", "audio/x-ape", "audio/x-monkeys-audio") ||
            name.orEmpty().substringBefore('?').endsWith(".ape", ignoreCase = true)

    fun read(input: InputStream): EchoApeScanInfo? {
        var prefix = input.tagBytes(10) ?: return null
        if (prefix.decodeToString(0, 3) == "ID3") {
            val size = prefix.tagSyncSafe(6) ?: return null
            if (size > MaxEmbeddedTextBytes || !input.skipTagBytes(size.toLong() +
                if (prefix[3].toInt() == 4 && prefix[5].toInt() and 16 != 0) 10 else 0)) return null
            prefix = input.tagBytes(10) ?: return null
        }
        if (prefix.decodeToString(0, 4) != "MAC ") return null
        val version = u16(prefix, 4)
        if (version !in 3800..3990) return null
        val blocks: Long
        val finalBlocks: Long
        val frames: Long
        val rate: Long
        if (version >= 3980) {
            val descriptor = prefix + (input.tagBytes(42) ?: return null)
            val length = u32(descriptor, 8)
            val headerLength = u32(descriptor, 12)
            if (length !in 52..MaxEmbeddedTextBytes.toLong() || headerLength !in 24..MaxEmbeddedTextBytes.toLong() ||
                !input.skipTagBytes(length - 52)) return null
            val header = input.tagBytes(24) ?: return null
            blocks = u32(header, 4)
            finalBlocks = u32(header, 8)
            frames = u32(header, 12)
            rate = u32(header, 20)
        } else {
            val header = prefix + (input.tagBytes(22) ?: return null)
            val compression = u16(header, 6)
            blocks = if (version >= 3950) 73728L * 4 else if (version >= 3900 || compression >= 4000) 73728L else 9216L
            finalBlocks = u32(header, 28)
            frames = u32(header, 24)
            rate = u32(header, 12)
        }
        if (blocks !in 1..294_912 || finalBlocks !in 1..blocks || frames !in 1..100_000 || rate !in 1..768_000) return null
        return EchoApeScanInfo(((frames - 1) * blocks + finalBlocks) * 1000 / rate, rate.toInt())
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 255) shl (8 * i)) }
}

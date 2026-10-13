package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput

/** A bounded frame index; compressed audio stays in the extractor's input. */
internal data class EchoApeLayout(
    val version: Int,
    val compression: Int,
    val flags: Int,
    val bitDepth: Int,
    val channels: Int,
    val sampleRate: Int,
    val blocksPerFrame: Int,
    val finalFrameBlocks: Int,
    val positions: LongArray,
    val sizes: IntArray,
    val skips: IntArray,
    val audioEnd: Long,
) {
    val durationUs: Long = ((positions.size - 1L) * blocksPerFrame + finalFrameBlocks) * 1_000_000 / sampleRate
    fun timeUs(frame: Int): Long = frame.toLong() * blocksPerFrame * 1_000_000 / sampleRate
    fun frameAt(timeUs: Long): Int = (timeUs.coerceIn(0, durationUs) * sampleRate / 1_000_000 / blocksPerFrame)
        .toInt().coerceIn(positions.indices)
    fun extraData(): ByteArray = byteArrayOf(
        version.toByte(), (version shr 8).toByte(), compression.toByte(), (compression shr 8).toByte(),
        flags.toByte(), (flags shr 8).toByte(),
    )
}

@UnstableApi
internal fun readApeLayout(input: ExtractorInput): EchoApeLayout {
    // ID3v2 may precede MAC; never scan arbitrarily through a non-APE file.
    val prefix = ByteArray(10)
    input.readFully(prefix, 0, 10)
    val start: Long
    val header: ByteArray
    if (fourcc(prefix, 0).startsWith("ID3")) {
        if ((6..9).any { prefix[it].toInt() and 128 != 0 }) apeMalformed("Invalid ID3 size")
        val tagSize = (6..9).fold(0) { size, i -> (size shl 7) or (prefix[i].toInt() and 127) }
        if (tagSize > MaxApeHeaderBytes) apeMalformed("APE leading tag is too large")
        input.skipFully(tagSize + if (prefix[3].toInt() == 4 && prefix[5].toInt() and 16 != 0) 10 else 0)
        start = input.position
        input.readFully(prefix, 0, 10)
    } else {
        start = input.position - 10
    }
    if (fourcc(prefix, 0) != "MAC ") apeMalformed("Invalid APE signature")
    val version = u16le(prefix, 4)
    if (version !in 3800..3990) {
        throw ParserException.createForUnsupportedContainerFeature("Unsupported APE version: $version")
    }
    header = ByteArray(if (version >= 3980) 52 else 32)
    prefix.copyInto(header)
    input.readFully(header, 10, header.size - 10)
    val compression: Int
    val flags: Int
    val channels: Int
    val sampleRate: Long
    val bits: Int
    val blocks: Long
    val finalBlocks: Long
    val frames: Long
    val seekBytes: Long
    val wavHeaderBytes: Long
    val audioBytes: Long
    val tailBytes: Long
    if (version >= 3980) {
        val descriptorBytes = u32le(header, 8)
        val headerBytes = u32le(header, 12)
        seekBytes = u32le(header, 16)
        wavHeaderBytes = u32le(header, 20)
        audioBytes = u32le(header, 24) or (u32le(header, 28) shl 32)
        tailBytes = u32le(header, 32)
        if (descriptorBytes !in 52L..MaxApeHeaderBytes.toLong() || headerBytes !in 24L..MaxApeHeaderBytes.toLong()) {
            apeMalformed("Invalid APE descriptor length")
        }
        input.skipFully((descriptorBytes - 52).toInt())
        val audioHeader = ByteArray(24)
        input.readFully(audioHeader, 0, 24)
        compression = u16le(audioHeader, 0)
        flags = u16le(audioHeader, 2)
        blocks = u32le(audioHeader, 4)
        finalBlocks = u32le(audioHeader, 8)
        frames = u32le(audioHeader, 12)
        bits = u16le(audioHeader, 16)
        channels = u16le(audioHeader, 18)
        sampleRate = u32le(audioHeader, 20)
        input.skipFully((headerBytes - 24).toInt())
    } else {
        compression = u16le(header, 6)
        flags = u16le(header, 8)
        channels = u16le(header, 10)
        sampleRate = u32le(header, 12)
        wavHeaderBytes = u32le(header, 16)
        tailBytes = u32le(header, 20)
        frames = u32le(header, 24)
        finalBlocks = u32le(header, 28)
        if (flags and 4 != 0) input.skipFully(4)
        seekBytes = if (flags and 16 != 0) {
            val count = ByteArray(4)
            input.readFully(count, 0, 4)
            u32le(count, 0) * 4
        } else frames * 4
        bits = if (flags and 1 != 0) 8 else if (flags and 8 != 0) 24 else 16
        blocks = if (version >= 3950) 73728L * 4 else if (version >= 3900 || compression >= 4000) 73728L else 9216L
        audioBytes = -1
    }
    if (channels !in 1..2 || bits !in listOf(8, 16, 24) || sampleRate !in 1..768_000 ||
        compression !in 1000..5000 || compression % 1000 != 0 || (version < 3930 && compression == 5000) ||
        blocks !in 1..294_912 || finalBlocks !in 1..blocks || frames !in 1..100_000 ||
        seekBytes < frames * 4 || seekBytes > MaxApeHeaderBytes || seekBytes % 4 != 0L ||
        wavHeaderBytes > MaxApeHeaderBytes || tailBytes > MaxApeHeaderBytes
    ) apeMalformed("Invalid or oversized APE audio header")
    if (version < 3980 && flags and 32 == 0) input.skipFully(wavHeaderBytes.toInt())
    val positions = LongArray(frames.toInt())
    val sizes = IntArray(frames.toInt())
    val skips = IntArray(frames.toInt())
    val entry = ByteArray(4)
    for (i in positions.indices) {
        input.readFully(entry, 0, 4)
        positions[i] = start + u32le(entry, 0)
    }
    input.skipFully((seekBytes - frames * 4).toInt())
    if (version >= 3980) input.skipFully(wavHeaderBytes.toInt())
    val first = input.position + if (version < 3810) frames else 0
    positions[0] = first
    for (i in 1 until positions.size) {
        if (positions[i] <= positions[i - 1]) apeMalformed("APE seek offsets are not increasing")
        skips[i] = ((positions[i] - first) and 3).toInt()
    }
    val audioEnd = if (audioBytes >= 0) {
        if (audioBytes <= 0 || audioBytes > Long.MAX_VALUE - first) apeMalformed("Invalid APE audio length")
        first + audioBytes
    } else {
        if (input.length == C.LENGTH_UNSET.toLong()) {
            throw ParserException.createForUnsupportedContainerFeature("Legacy APE requires a known file length")
        }
        input.length - tailBytes
    }
    if (audioEnd <= positions.last() || (input.length != C.LENGTH_UNSET.toLong() && audioEnd > input.length)) {
        apeMalformed("Truncated APE audio data")
    }
    for (i in positions.indices) {
        val end = if (i + 1 < positions.size) positions[i + 1] else audioEnd
        val size = (end - positions[i] + skips[i] + 3) and -4L
        if (size !in 1..MaxApePacketBytes.toLong()) apeMalformed("Invalid or oversized APE frame")
        sizes[i] = size.toInt()
        positions[i] -= skips[i]
    }
    if (version < 3810) {
        val bit = ByteArray(1)
        for (i in positions.indices) {
            input.readFully(bit, 0, 1)
            val value = bit[0].toInt() and 255
            if (value > 7) apeMalformed("Invalid APE bit offset")
            if (i > 0 && value != 0) sizes[i - 1] += 4
            skips[i] = skips[i] * 8 + value
        }
    }
    return EchoApeLayout(version, compression, flags, bits, channels, sampleRate.toInt(),
        blocks.toInt(), finalBlocks.toInt(), positions, sizes, skips, audioEnd)
}

internal const val MaxApeHeaderBytes = 16 * 1024 * 1024
internal const val MaxApePacketBytes = 8 * 1024 * 1024

@UnstableApi
private fun apeMalformed(message: String): Nothing = throw ParserException.createForMalformedContainer(message, null)

private fun u16le(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

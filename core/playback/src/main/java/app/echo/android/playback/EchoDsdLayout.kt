package app.echo.android.playback

internal data class EchoDsfLayout(
    val channelCount: Int,
    val dsdRateHz: Int,
    val decoderPcmRateHz: Int,
    val bitsPerSample: Int,
    val sampleCount: Long,
    val blockSizePerChannel: Int,
    val blockAlign: Int,
    val dataOffset: Long,
    val dataSize: Long,
    val audioSize: Long,
    val id3Offset: Long,
    val durationUs: Long,
    val mimeType: String,
) {
    val padded: Boolean get() = dataSize > audioSize

    fun timeUs(audioBytesRead: Long): Long {
        if (decoderPcmRateHz <= 0 || channelCount <= 0) return 0L
        val pcmSamples = (audioBytesRead / channelCount).coerceAtLeast(0L)
        return pcmSamples * 1_000_000L / decoderPcmRateHz
    }

    fun seekAudioByteOffset(timeUs: Long): Long {
        if (decoderPcmRateHz <= 0 || channelCount <= 0 || blockAlign <= 0) return 0L
        val pcm = (timeUs.coerceAtLeast(0L) * decoderPcmRateHz) / 1_000_000L
        var offset = (pcm * channelCount).coerceIn(0L, audioSize)
        offset -= offset % blockAlign
        return offset
    }

    companion object {
        const val HeaderBytes = 92

        fun sniff(header: ByteArray): Boolean {
            if (header.size < 12) return false
            return fourcc(header, 0) == "DSD " && u64le(header, 4) == 28L
        }

        fun parse(header: ByteArray): EchoDsfLayout? {
            if (!sniff(header) || header.size < HeaderBytes) return null
            if (fourcc(header, 28) != "fmt " || u64le(header, 32) != 52L) return null
            if (u32le(header, 40) != 1L || u32le(header, 44) != 0L) return null
            val channelCount = u32le(header, 52).toInt()
            if (channelCount !in 1..6) return null
            val dsdRateHz = u32le(header, 56).toInt()
            if (dsdRateHz < 8 || dsdRateHz % 8 != 0) return null
            val bitsPerSample = u32le(header, 60).toInt()
            val mimeType = when (bitsPerSample) {
                1 -> EchoDsdMime.LsbfPlanar
                8 -> EchoDsdMime.MsbfPlanar
                else -> return null
            }
            val sampleCount = u64le(header, 64)
            if (sampleCount <= 0L) return null
            val blockSize = u32le(header, 72).toInt()
            if (blockSize <= 0 || blockSize > 1_048_576) return null
            if (blockSize > Int.MAX_VALUE / channelCount) return null
            if (fourcc(header, 80) != "data") return null
            val dataChunkSize = u64le(header, 84)
            if (dataChunkSize < 12L) return null
            val dataSize = dataChunkSize - 12L
            val audioSize = (sampleCount / 8L) * channelCount
            if (audioSize <= 0L) return null
            val decoderPcmRateHz = EchoDsdPcm.decoderPcmRateHz(dsdRateHz)
            return EchoDsfLayout(
                channelCount = channelCount,
                dsdRateHz = dsdRateHz,
                decoderPcmRateHz = decoderPcmRateHz,
                bitsPerSample = bitsPerSample,
                sampleCount = sampleCount,
                blockSizePerChannel = blockSize,
                blockAlign = blockSize * channelCount,
                dataOffset = HeaderBytes.toLong(),
                dataSize = dataSize,
                audioSize = audioSize,
                id3Offset = u64le(header, 20),
                durationUs = EchoDsdPcm.durationUs(sampleCount, dsdRateHz),
                mimeType = mimeType,
            )
        }
    }
}

internal data class EchoDffLayout(
    val channelCount: Int,
    val dsdRateHz: Int,
    val decoderPcmRateHz: Int,
    val dataOffset: Long,
    val dataSize: Long,
    val durationUs: Long,
    val compressed: Boolean,
    val mimeType: String,
) {
    fun timeUs(audioBytesRead: Long): Long {
        if (decoderPcmRateHz <= 0 || channelCount <= 0) return 0L
        val pcmSamples = (audioBytesRead / channelCount).coerceAtLeast(0L)
        return pcmSamples * 1_000_000L / decoderPcmRateHz
    }

    fun seekAudioByteOffset(timeUs: Long): Long {
        if (decoderPcmRateHz <= 0 || channelCount <= 0) return 0L
        val pcm = (timeUs.coerceAtLeast(0L) * decoderPcmRateHz) / 1_000_000L
        var offset = (pcm * channelCount).coerceIn(0L, dataSize)
        offset -= offset % channelCount
        return offset
    }

    companion object {
        const val PacketFrames = 4096

        fun sniff(header: ByteArray): Boolean {
            if (header.size < 16) return false
            return fourcc(header, 0) == "FRM8" && fourcc(header, 12) == "DSD "
        }
    }
}

internal fun fourcc(bytes: ByteArray, start: Int): String {
    if (start + 4 > bytes.size) return ""
    return String(bytes, start, 4, Charsets.ISO_8859_1)
}

internal fun u32le(bytes: ByteArray, start: Int): Long {
    if (start + 4 > bytes.size) return 0L
    return (bytes[start].toLong() and 0xFFL) or
        ((bytes[start + 1].toLong() and 0xFFL) shl 8) or
        ((bytes[start + 2].toLong() and 0xFFL) shl 16) or
        ((bytes[start + 3].toLong() and 0xFFL) shl 24)
}

internal fun u64le(bytes: ByteArray, start: Int): Long =
    u32le(bytes, start) or (u32le(bytes, start + 4) shl 32)

internal fun u16be(bytes: ByteArray, start: Int): Int {
    if (start + 2 > bytes.size) return 0
    return ((bytes[start].toInt() and 0xFF) shl 8) or (bytes[start + 1].toInt() and 0xFF)
}

internal fun u32be(bytes: ByteArray, start: Int): Long {
    if (start + 4 > bytes.size) return 0L
    return ((bytes[start].toLong() and 0xFFL) shl 24) or
        ((bytes[start + 1].toLong() and 0xFFL) shl 16) or
        ((bytes[start + 2].toLong() and 0xFFL) shl 8) or
        (bytes[start + 3].toLong() and 0xFFL)
}

internal fun u64be(bytes: ByteArray, start: Int): Long =
    (u32be(bytes, start) shl 32) or u32be(bytes, start + 4)

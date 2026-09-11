package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteAudioFormat

object EchoLinkCastFormat {
    fun fromTrack(
        uri: String,
        mimeType: String? = null,
        sampleRateHz: Int? = null,
        bitDepth: Int? = null,
        channelCount: Int? = null,
        codec: String? = null,
    ): EchoRemoteAudioFormat {
        val mime = EchoLinkCastPolicy.mimeTypeForUri(uri, mimeType)
        val guessed = fromMimeAndName(uri, mime)
        val resolvedCodec = (codec?.takeIf { it.isNotBlank() } ?: guessed.codec)?.lowercase()
        return EchoRemoteAudioFormat(
            codec = resolvedCodec,
            mimeType = mime.takeIf { it != "application/octet-stream" } ?: guessed.mimeType,
            sampleRateHz = sampleRateHz?.takeIf { it > 0 } ?: guessed.sampleRateHz,
            bitDepth = bitDepth?.takeIf { it > 0 } ?: guessed.bitDepth,
            channelCount = channelCount?.takeIf { it > 0 } ?: guessed.channelCount,
            lossless = resolvedCodec?.let(::isLosslessCodec) ?: guessed.lossless,
        )
    }

    fun sniff(header: ByteArray): EchoRemoteAudioFormat? {
        if (header.size < 12) return fromMagic(header)
        return parseFlac(header) ?: parseWav(header) ?: parseAiff(header) ?: fromMagic(header)
    }

    fun merge(base: EchoRemoteAudioFormat?, sniffed: EchoRemoteAudioFormat?): EchoRemoteAudioFormat? {
        if (base == null) return sniffed
        return base.merge(sniffed)
    }

    fun formatLabel(format: EchoRemoteAudioFormat?): String? {
        if (format == null) return null
        val parts = buildList {
            format.sampleRateHz?.takeIf { it > 0 }?.let { hz ->
                add(if (hz % 1000 == 0) "${hz / 1000} kHz" else "$hz Hz")
            }
            format.bitDepth?.takeIf { it > 0 }?.let { add("${it}-bit") }
            format.channelCount?.takeIf { it > 0 }?.let { channels ->
                add(if (channels == 1) "Mono" else if (channels == 2) "Stereo" else "${channels}ch")
            }
            format.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun httpHeaders(format: EchoRemoteAudioFormat?): Map<String, String> {
        if (format == null) return emptyMap()
        return buildMap {
            format.codec?.takeIf { it.isNotBlank() }?.let { put("X-ECHO-Link-Codec", it) }
            format.sampleRateHz?.takeIf { it > 0 }?.let { put("X-ECHO-Link-Sample-Rate", it.toString()) }
            format.bitDepth?.takeIf { it > 0 }?.let { put("X-ECHO-Link-Bit-Depth", it.toString()) }
            format.channelCount?.takeIf { it > 0 }?.let { put("X-ECHO-Link-Channels", it.toString()) }
            format.lossless?.let { put("X-ECHO-Link-Lossless", if (it) "1" else "0") }
            format.mimeType?.takeIf { it.isNotBlank() }?.let { put("X-ECHO-Link-Mime-Type", it) }
        }
    }

    private fun fromMimeAndName(uri: String, mime: String): EchoRemoteAudioFormat {
        val name = uri.substringBefore('?').substringAfterLast('/').lowercase()
        val codec = when {
            "flac" in mime || name.endsWith(".flac") -> "flac"
            "wav" in mime && "wavpack" !in mime || name.endsWith(".wav") -> "wav"
            "aiff" in mime || name.endsWith(".aiff") || name.endsWith(".aif") -> "aiff"
            "dsf" in mime || name.endsWith(".dsf") -> "dsd"
            "dff" in mime || name.endsWith(".dff") || name.endsWith(".dsd") -> "dsd"
            "wavpack" in mime || name.endsWith(".wv") -> "wavpack"
            "alac" in name || name.endsWith(".alac") -> "alac"
            mime == "audio/mp4" || name.endsWith(".m4a") || name.endsWith(".aac") ->
                if (name.endsWith(".alac")) "alac" else "aac"
            "mpeg" in mime || name.endsWith(".mp3") -> "mp3"
            "ogg" in mime || name.endsWith(".ogg") || name.endsWith(".oga") -> "ogg"
            "opus" in mime || name.endsWith(".opus") -> "opus"
            "ape" in mime || name.endsWith(".ape") -> "ape"
            "pcm" in mime || name.endsWith(".pcm") -> "pcm"
            else -> null
        }
        return EchoRemoteAudioFormat(
            codec = codec,
            mimeType = mime.takeIf { it != "application/octet-stream" },
            lossless = codec?.let(::isLosslessCodec),
        )
    }

    private fun fromMagic(header: ByteArray): EchoRemoteAudioFormat? {
        if (header.size < 4) return null
        val codec = when {
            header.startsWith("fLaC") -> "flac"
            header.startsWith("RIFF") && header.size >= 12 && header.ascii(8, 4) == "WAVE" -> "wav"
            header.startsWith("FORM") && header.size >= 12 &&
                (header.ascii(8, 4) == "AIFF" || header.ascii(8, 4) == "AIFC") -> "aiff"
            header.startsWith("OggS") -> "ogg"
            header.startsWith("DSD ") -> "dsd"
            header.startsWith("FRM8") -> "dsd"
            header.startsWith("wvpk") -> "wavpack"
            header.startsWith("ID3") || isMpegFrame(header) -> "mp3"
            header.size >= 8 && header.ascii(4, 4) == "ftyp" -> m4aCodec(header)
            else -> null
        } ?: return null
        val mime = when (codec) {
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aiff" -> "audio/aiff"
            "ogg", "opus" -> "audio/ogg"
            "dsd" -> if (header.startsWith("DSD ")) "audio/x-dsf" else "audio/x-dff"
            "wavpack" -> "audio/x-wavpack"
            "mp3" -> "audio/mpeg"
            "alac", "aac" -> "audio/mp4"
            else -> null
        }
        return EchoRemoteAudioFormat(codec = codec, mimeType = mime, lossless = isLosslessCodec(codec))
    }

    private fun parseFlac(header: ByteArray): EchoRemoteAudioFormat? {
        if (!header.startsWith("fLaC")) return null
        if (header.size < 42) return fromMagic(header)
        val type = header[4].toInt() and 0x7F
        val length = ((header[5].toInt() and 0xFF) shl 16) or
            ((header[6].toInt() and 0xFF) shl 8) or
            (header[7].toInt() and 0xFF)
        if (type != 0 || length < 18 || header.size < 8 + 18) return fromMagic(header)
        val b0 = header[8 + 10].toInt() and 0xFF
        val b1 = header[8 + 11].toInt() and 0xFF
        val b2 = header[8 + 12].toInt() and 0xFF
        val b3 = header[8 + 13].toInt() and 0xFF
        val sampleRate = (b0 shl 12) or (b1 shl 4) or (b2 shr 4)
        val channels = ((b2 shr 1) and 0x07) + 1
        val bitDepth = (((b2 and 0x01) shl 4) or (b3 shr 4)) + 1
        return EchoRemoteAudioFormat(
            codec = "flac",
            mimeType = "audio/flac",
            sampleRateHz = sampleRate.takeIf { it in 1..1_536_000 },
            bitDepth = bitDepth.takeIf { it in 4..32 },
            channelCount = channels.takeIf { it in 1..8 },
            lossless = true,
        )
    }

    private fun parseWav(header: ByteArray): EchoRemoteAudioFormat? {
        if (!header.startsWith("RIFF") || header.size < 36 || header.ascii(8, 4) != "WAVE") {
            return null
        }
        var offset = 12
        while (offset + 8 <= header.size) {
            val chunk = header.ascii(offset, 4)
            val size = header.le32(offset + 4)
            if (size < 0) return fromMagic(header)
            if (chunk == "fmt " && offset + 8 + 16 <= header.size) {
                val formatTag = header.le16(offset + 8)
                val channels = header.le16(offset + 10)
                val sampleRate = header.le32(offset + 12)
                val bitDepth = header.le16(offset + 22)
                val lossless = formatTag == 1 || formatTag == 3 || formatTag == 0xFFFE
                return EchoRemoteAudioFormat(
                    codec = "wav",
                    mimeType = "audio/wav",
                    sampleRateHz = sampleRate.takeIf { it in 1..1_536_000 },
                    bitDepth = bitDepth.takeIf { it in 8..64 },
                    channelCount = channels.takeIf { it in 1..16 },
                    lossless = lossless,
                )
            }
            offset += 8 + size + (size and 1)
        }
        return fromMagic(header)
    }

    private fun parseAiff(header: ByteArray): EchoRemoteAudioFormat? {
        if (!header.startsWith("FORM") || header.size < 38) return null
        val form = header.ascii(8, 4)
        if (form != "AIFF" && form != "AIFC") return fromMagic(header)
        var offset = 12
        while (offset + 8 <= header.size) {
            val chunk = header.ascii(offset, 4)
            val size = header.be32(offset + 4)
            if (size < 0) return null
            if (chunk == "COMM" && offset + 8 + 18 <= header.size) {
                val channels = header.be16(offset + 8)
                val bitDepth = header.be16(offset + 14)
                val sampleRate = header.extended80Rate(offset + 16)
                return EchoRemoteAudioFormat(
                    codec = "aiff",
                    mimeType = "audio/aiff",
                    sampleRateHz = sampleRate,
                    bitDepth = bitDepth.takeIf { it in 8..64 },
                    channelCount = channels.takeIf { it in 1..16 },
                    lossless = true,
                )
            }
            offset += 8 + size + (size and 1)
        }
        return fromMagic(header)
    }

    private fun m4aCodec(header: ByteArray): String? {
        val brand = if (header.size >= 12) header.ascii(8, 4).lowercase() else ""
        return when {
            "alac" in brand -> "alac"
            brand == "M4A ".lowercase() || brand.startsWith("mp4") || brand == "isom" -> "aac"
            else -> "aac"
        }
    }

    private fun isLosslessCodec(codec: String): Boolean =
        codec.lowercase() in setOf("flac", "wav", "aiff", "alac", "dsd", "wavpack", "ape", "tak", "pcm")

    private fun isMpegFrame(header: ByteArray): Boolean {
        if (header.size < 2) return false
        val a = header[0].toInt() and 0xFF
        val b = header[1].toInt() and 0xFF
        return a == 0xFF && (b and 0xE0) == 0xE0
    }

    private fun ByteArray.startsWith(ascii: String): Boolean {
        if (size < ascii.length) return false
        return ascii.indices.all { index -> this[index].toInt().toChar() == ascii[index] }
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String {
        if (offset < 0 || offset + length > size) return ""
        return String(copyOfRange(offset, offset + length), Charsets.US_ASCII)
    }

    private fun ByteArray.le16(offset: Int): Int {
        if (offset + 2 > size) return -1
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.be16(offset: Int): Int {
        if (offset + 2 > size) return -1
        return ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
    }

    private fun ByteArray.le32(offset: Int): Int {
        if (offset + 4 > size) return -1
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun ByteArray.be32(offset: Int): Int {
        if (offset + 4 > size) return -1
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private fun ByteArray.extended80Rate(offset: Int): Int? {
        if (offset + 10 > size) return null
        val exp = ((this[offset].toInt() and 0x7F) shl 8) or (this[offset + 1].toInt() and 0xFF)
        var mantissa = 0L
        for (index in 2 until 6) {
            mantissa = (mantissa shl 8) or (this[offset + index].toInt() and 0xFF).toLong()
        }
        val shift = exp - 16383 - 31
        val rate = if (shift >= 0) mantissa shl shift else mantissa shr -shift
        return rate.toInt().takeIf { it in 1..1_536_000 }
    }
}

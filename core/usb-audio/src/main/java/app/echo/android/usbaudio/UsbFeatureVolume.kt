package app.echo.android.usbaudio

/** Only master controls on an unambiguous playback path are eligible; capture units are never selected. */
internal data class UsbVolumeUnit(val id: Int, val acInterface: Int, val uac2: Boolean, val mute: Boolean)

internal object UsbVolumeTopology {
    fun find(raw: ByteArray, format: UsbAudioStreamingFormat): UsbVolumeUnit? {
        val ac = format.acInterfaceNumber ?: return null
        val terminal = format.terminalLink ?: return null
        val two = format.audioClassVersion == UsbAudioClassVersion.Uac2
        if (!two && format.audioClassVersion != UsbAudioClassVersion.Uac1) return null
        val sources = HashMap<Int, List<Int>>()
        val features = HashMap<Int, UsbVolumeUnit>()
        val outputs = ArrayList<Int>()
        val controlInterfaces = HashSet<Int>()
        var inControl = false
        var offset = 0
        fun byte(index: Int) = raw[index].toInt() and 255
        while (offset + 2 <= raw.size) {
            val size = byte(offset)
            if (size < 2 || offset + size > raw.size) return null
            val type = byte(offset + 1)
            if (type == 4) {
                if (size >= 9 && byte(offset + 5) == 1 && byte(offset + 6) == 1) controlInterfaces += byte(offset + 2)
                inControl = size >= 9 && byte(offset + 2) == ac && byte(offset + 5) == 1 && byte(offset + 6) == 1
            }
            if (inControl && type == 0x24 && size >= 4) {
                val id = byte(offset + 3)
                when (byte(offset + 2)) {
                    2 -> sources[id] = emptyList()
                    3 -> if (size >= 9) {
                        sources[id] = listOf(byte(offset + 7))
                        val terminalType = byte(offset + 4) or (byte(offset + 5) shl 8)
                        if (terminalType ushr 8 != 1) outputs += id // Physical output, not USB capture.
                    }
                    4, 5 -> if (size >= 6 && byte(offset + 4) == 1) {
                        // Multiple-input mixers/selectors are ambiguous until their active path is known.
                        sources[id] = listOf(byte(offset + 5))
                    }
                    6 -> if (size >= (if (two) 10 else 8)) {
                        sources[id] = listOf(byte(offset + 4))
                        val controlSize = if (two) 4 else byte(offset + 5)
                        val start = offset + if (two) 5 else 6
                        if (controlSize in 1..4 && start + controlSize < offset + size) {
                            val controls = byte(start)
                            val volume = if (two) (controls shr 2) and 3 == 3 else controls and 2 != 0
                            val mute = if (two) controls and 3 == 3 else controls and 1 != 0
                            if (volume) features[id] = UsbVolumeUnit(id, ac, two, mute)
                        }
                    }
                }
            }
            offset += size
        }
        if (controlInterfaces.size != 1) return null
        fun path(id: Int, seen: Set<Int>): List<Int>? {
            if (id == terminal) return listOf(id)
            if (id in seen || seen.size > 64) return null
            val inputs = sources[id] ?: return null
            if (inputs.size != 1) return null
            return path(inputs[0], seen + id)?.let { listOf(id) + it }
        }
        val candidates = outputs.flatMap { path(it, emptySet()).orEmpty().mapNotNull(features::get) }.distinct()
        return candidates.singleOrNull()
    }
}

internal data class UsbVolumeRange(val minimum: Int, val maximum: Int, val resolution: Int) {
    fun valid() = minimum in -32767..0 && maximum in minimum..32767 && resolution in 1..32767
    fun quantize(value: Int): Int = (minimum + ((value.coerceIn(minimum, maximum) - minimum + resolution - 1) / resolution) * resolution).coerceAtMost(maximum)
}

/** UAC values are signed 1/256 dB. Every change requires a matching GET_CUR readback. */
internal class UsbFeatureVolumeControl(private val transfer: UsbAudioControlTransfer, val unit: UsbVolumeUnit) {
    private fun read(selector: Int, request: Int, count: Int): ByteArray? {
        val payload = ByteArray(count)
        return payload.takeIf { transfer.controlTransfer(0xa1, request, selector shl 8,
            (unit.id shl 8) or unit.acInterface, payload, count, 300) == count }
    }
    private fun signed(bytes: ByteArray, offset: Int = 0) = ((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8)).toShort().toInt()
    fun readVolume(): Int? = read(2, if (unit.uac2) 1 else 0x81, 2)?.let { signed(it) }
    fun readMute(): Boolean? = if (!unit.mute) null else read(1, if (unit.uac2) 1 else 0x81, 1)?.let { when (it[0].toInt()) { 0 -> false; 1 -> true; else -> null } }
    fun range(): UsbVolumeRange? {
        val result = if (unit.uac2) {
            // Read the bounded subrange count first, then the full RANGE payload.
            val head = read(2, 2, 2) ?: return null
            val count = (head[0].toInt() and 255) or ((head[1].toInt() and 255) shl 8)
            if (count != 1) return null // Do not guess across discontinuous ranges.
            val bytes = read(2, 2, 8) ?: return null
            if (bytes[0].toInt() != 1 || bytes[1].toInt() != 0) return null
            UsbVolumeRange(signed(bytes, 2), signed(bytes, 4), signed(bytes, 6))
        } else {
            UsbVolumeRange(read(2, 0x82, 2)?.let { signed(it) } ?: return null,
                read(2, 0x83, 2)?.let { signed(it) } ?: return null,
                read(2, 0x84, 2)?.let { signed(it) } ?: return null)
        }
        return result.takeIf { it.valid() }
    }
    fun writeVolume(value: Int): Boolean {
        val bytes = byteArrayOf(value.toByte(), (value shr 8).toByte())
        return transfer.controlTransfer(0x21, 1, 2 shl 8, (unit.id shl 8) or unit.acInterface,
            bytes, 2, 300) == 2 && readVolume() == value
    }
    fun writeMute(value: Boolean): Boolean {
        if (!unit.mute) return false
        val bytes = byteArrayOf(if (value) 1 else 0)
        return transfer.controlTransfer(0x21, 1, 1 shl 8, (unit.id shl 8) or unit.acInterface,
            bytes, 1, 300) == 1 && readMute() == value
    }
}

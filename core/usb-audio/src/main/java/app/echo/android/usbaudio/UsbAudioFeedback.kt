package app.echo.android.usbaudio

object UsbAudioFeedback {
    fun nominalQ16(sampleRateHz: Int, packetsPerSecond: Int): Long {
        val rate = sampleRateHz.coerceAtLeast(1).toLong()
        val pps = packetsPerSecond.coerceAtLeast(1).toLong()
        return (rate shl 16) / pps
    }

    fun decodeQ16(payload: ByteArray, highSpeed: Boolean): Long? {
        if (highSpeed) {
            if (payload.size < 4) return null
            val value = payload[0].toLong() and 0xffL or
                ((payload[1].toLong() and 0xffL) shl 8) or
                ((payload[2].toLong() and 0xffL) shl 16) or
                ((payload[3].toLong() and 0xffL) shl 24)
            return value.takeIf { it > 0L }
        }
        if (payload.size < 3) return null
        val value = payload[0].toLong() and 0xffL or
            ((payload[1].toLong() and 0xffL) shl 8) or
            ((payload[2].toLong() and 0xffL) shl 16)
        return (value shl 2).takeIf { it > 0L }
    }

    fun clampQ16(measured: Long, nominal: Long): Long {
        val slack = 1L shl 16
        val min = (nominal - slack).coerceAtLeast(1L)
        val max = nominal + slack
        return measured.coerceIn(min, max)
    }

    fun smoothQ16(current: Long, measured: Long): Long =
        ((current * 3L) + measured) / 4L
}

package app.echo.android.usbaudio

import android.hardware.usb.UsbDeviceConnection

data class UsbAudioClockRange(
    val minHz: Int,
    val maxHz: Int,
    val resolutionHz: Int,
) {
    fun contains(rateHz: Int): Boolean {
        if (rateHz <= 0 || rateHz < minHz || rateHz > maxHz) return false
        if (minHz == maxHz || resolutionHz <= 0) return rateHz == minHz || rateHz == maxHz
        return (rateHz - minHz) % resolutionHz == 0
    }
}

object UsbAudioClock {
    fun setSampleRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
        sampleRateHz: Int,
    ): Boolean {
        val rate = sampleRateHz.takeIf { it > 0 } ?: return false
        val ranges = getSupportedSampleRates(connection, format)
        if (ranges.isNotEmpty() && !supportsRate(ranges, rate)) return false
        return when (format.audioClassVersion) {
            UsbAudioClassVersion.Uac2,
            UsbAudioClassVersion.Uac3,
            -> setUac2ClockRate(connection, format, rate) || setUac1EndpointRate(connection, format, rate)
            else -> setUac1EndpointRate(connection, format, rate) || setUac2ClockRate(connection, format, rate)
        }
    }

    fun getSampleRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
    ): Int? =
        getUac2ClockRate(connection, format) ?: getUac1EndpointRate(connection, format)

    fun getSupportedSampleRates(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
    ): List<UsbAudioClockRange> {
        if (
            format.audioClassVersion != UsbAudioClassVersion.Uac2 &&
            format.audioClassVersion != UsbAudioClassVersion.Uac3
        ) {
            val minHz = format.sampleRateMinHz
            val maxHz = format.sampleRateMaxHz
            if (minHz != null && maxHz != null && minHz > 0 && maxHz >= minHz) {
                return listOf(UsbAudioClockRange(minHz, maxHz, 1))
            }
            return format.sampleRates.map { UsbAudioClockRange(it, it, 0) }
        }
        val payload = ByteArray(RANGE_BUFFER_BYTES)
        return clockIdsFor(format).firstNotNullOfOrNull { clockId ->
            val transferred = connection.controlTransfer(
                UAC2_INTERFACE_GET,
                GET_RANGE,
                UAC2_CS_SAM_FREQ shl 8,
                clockIndex(clockId, format.acInterfaceNumber),
                payload,
                payload.size,
                TIMEOUT_MS,
            )
            parseRangePayload(payload, transferred).takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    fun supportsRate(ranges: List<UsbAudioClockRange>, rateHz: Int): Boolean =
        ranges.any { it.contains(rateHz) }

    fun accepted(requestedHz: Int, setSucceeded: Boolean, readBackHz: Int?): Boolean =
        when {
            readBackHz == requestedHz -> true
            setSucceeded && readBackHz == null -> true
            else -> false
        }

    fun parseRangePayload(payload: ByteArray, length: Int): List<UsbAudioClockRange> {
        if (length < 2 || payload.size < 2) return emptyList()
        val count = payload.le16(0).coerceAtLeast(0)
        if (count == 0) return emptyList()
        val available = minOf(length, payload.size)
        return (0 until count).mapNotNull { index ->
            val offset = 2 + index * 12
            if (offset + 12 > available) return@mapNotNull null
            val minHz = payload.le32(offset)
            val maxHz = payload.le32(offset + 4)
            val resolutionHz = payload.le32(offset + 8)
            if (minHz <= 0 || maxHz < minHz) null
            else UsbAudioClockRange(minHz = minHz, maxHz = maxHz, resolutionHz = resolutionHz.coerceAtLeast(0))
        }
    }

    private fun setUac1EndpointRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
        sampleRateHz: Int,
    ): Boolean {
        val endpoint = format.endpointAddress ?: return false
        val payload = sampleRateHz.toUac1RateBytes()
        val transferred = connection.controlTransfer(
            UAC1_ENDPOINT_SET,
            SET_CUR,
            CS_SAM_FREQ shl 8,
            endpoint,
            payload,
            payload.size,
            TIMEOUT_MS,
        )
        if (transferred != payload.size) return false
        val read = getUac1EndpointRate(connection, format)
        return accepted(sampleRateHz, setSucceeded = true, readBackHz = read)
    }

    private fun getUac1EndpointRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
    ): Int? {
        val endpoint = format.endpointAddress ?: return null
        val payload = ByteArray(3)
        val transferred = connection.controlTransfer(
            UAC1_ENDPOINT_GET,
            GET_CUR,
            CS_SAM_FREQ shl 8,
            endpoint,
            payload,
            payload.size,
            TIMEOUT_MS,
        )
        if (transferred != payload.size) return null
        return payload.toUac1Rate()
    }

    private fun setUac2ClockRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
        sampleRateHz: Int,
    ): Boolean {
        val payload = sampleRateHz.toUac2RateBytes()
        return clockIdsFor(format).any { clockId ->
            val transferred = connection.controlTransfer(
                UAC2_INTERFACE_SET,
                SET_CUR,
                UAC2_CS_SAM_FREQ shl 8,
                clockIndex(clockId, format.acInterfaceNumber),
                payload,
                payload.size,
                TIMEOUT_MS,
            )
            if (transferred != payload.size) return@any false
            val read = getUac2ClockRate(connection, format, clockId)
            accepted(sampleRateHz, setSucceeded = true, readBackHz = read)
        }
    }

    private fun getUac2ClockRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
        clockId: Int? = null,
    ): Int? {
        val payload = ByteArray(4)
        val clocks = if (clockId != null) listOf(clockId) else clockIdsFor(format)
        return clocks.firstNotNullOfOrNull { id ->
            val transferred = connection.controlTransfer(
                UAC2_INTERFACE_GET,
                GET_CUR,
                UAC2_CS_SAM_FREQ shl 8,
                clockIndex(id, format.acInterfaceNumber),
                payload,
                payload.size,
                TIMEOUT_MS,
            )
            if (transferred == payload.size) payload.toUac2Rate() else null
        }
    }

    private fun clockIdsFor(format: UsbAudioStreamingFormat): List<Int> =
        format.clockSourceIds.filter { it > 0 }.ifEmpty { listOf(1, 2, 3, 4, 5) }

    private fun clockIndex(clockId: Int, acInterfaceNumber: Int?): Int =
        (clockId and 0xff) or ((acInterfaceNumber ?: 0) shl 8)

    private fun Int.toUac1RateBytes(): ByteArray =
        byteArrayOf(
            (this and 0xff).toByte(),
            ((this shr 8) and 0xff).toByte(),
            ((this shr 16) and 0xff).toByte(),
        )

    private fun Int.toUac2RateBytes(): ByteArray =
        byteArrayOf(
            (this and 0xff).toByte(),
            ((this shr 8) and 0xff).toByte(),
            ((this shr 16) and 0xff).toByte(),
            ((this shr 24) and 0xff).toByte(),
        )

    private fun ByteArray.toUac1Rate(): Int? {
        if (size < 3) return null
        val rate = this[0].toInt() and 0xff or
            ((this[1].toInt() and 0xff) shl 8) or
            ((this[2].toInt() and 0xff) shl 16)
        return rate.takeIf { it > 0 }
    }

    private fun ByteArray.toUac2Rate(): Int? {
        if (size < 4) return null
        val rate = this[0].toInt() and 0xff or
            ((this[1].toInt() and 0xff) shl 8) or
            ((this[2].toInt() and 0xff) shl 16) or
            ((this[3].toInt() and 0xff) shl 24)
        return rate.takeIf { it > 0 }
    }

    private fun ByteArray.le16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.le32(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private const val SET_CUR = 0x01
    private const val GET_CUR = 0x81
    private const val GET_RANGE = 0x82
    private const val CS_SAM_FREQ = 0x01
    private const val UAC2_CS_SAM_FREQ = 0x01
    private const val UAC1_ENDPOINT_SET = 0x22
    private const val UAC1_ENDPOINT_GET = 0xA2
    private const val UAC2_INTERFACE_SET = 0x21
    private const val UAC2_INTERFACE_GET = 0xA1
    private const val TIMEOUT_MS = 1_000
    private const val RANGE_BUFFER_BYTES = 256
}

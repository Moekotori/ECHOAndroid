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

internal fun interface UsbAudioControlTransfer {
    fun controlTransfer(requestType: Int, request: Int, value: Int, index: Int,
        payload: ByteArray, length: Int, timeoutMs: Int): Int
}

internal data class UsbAudioClockConfiguration(
    val requestedHz: Int,
    val setSucceeded: Boolean = false,
    val readBackHz: Int? = null,
    val clockId: Int? = null,
) {
    val accepted: Boolean get() = UsbAudioClock.accepted(requestedHz, setSucceeded, readBackHz)
    val verified: Boolean get() = requestedHz > 0 && readBackHz == requestedHz
}

object UsbAudioClock {
    fun setSampleRate(connection: UsbDeviceConnection, format: UsbAudioStreamingFormat, sampleRateHz: Int): Boolean =
        setSampleRate(UsbAudioControlTransfer(connection::controlTransfer), format, sampleRateHz)

    fun getSampleRate(connection: UsbDeviceConnection, format: UsbAudioStreamingFormat): Int? =
        getSampleRate(UsbAudioControlTransfer(connection::controlTransfer), format)

    fun getSupportedSampleRates(connection: UsbDeviceConnection, format: UsbAudioStreamingFormat): List<UsbAudioClockRange> =
        getSupportedSampleRates(UsbAudioControlTransfer(connection::controlTransfer), format)

    internal fun setSampleRate(
        connection: UsbAudioControlTransfer,
        format: UsbAudioStreamingFormat,
        sampleRateHz: Int,
    ): Boolean = configureSampleRate(connection, format, sampleRateHz).accepted

    internal fun configureSampleRate(
        connection: UsbAudioControlTransfer,
        format: UsbAudioStreamingFormat,
        sampleRateHz: Int,
        afterRateSet: () -> Unit = {},
    ): UsbAudioClockConfiguration {
        if (sampleRateHz <= 0) return UsbAudioClockConfiguration(sampleRateHz)
        val uac2 = format.audioClassVersion == UsbAudioClassVersion.Uac2 ||
            format.audioClassVersion == UsbAudioClassVersion.Uac3
        // Resolve once: RANGE, SET and readback must address the same active source.
        val clockId = if (uac2) resolveClockSource(connection, format)
            ?: return UsbAudioClockConfiguration(sampleRateHz) else null
        val resolved = if (clockId != null) format.copy(
            clockSourceIds = listOf(clockId), clockSelectorSources = emptyMap(),
        ) else format
        val ranges = getSupportedSampleRates(connection, resolved)
        if (ranges.isNotEmpty() && !supportsRate(ranges, sampleRateHz))
            return UsbAudioClockConfiguration(sampleRateHz, clockId = clockId)
        val payload = if (uac2) sampleRateHz.toUac2RateBytes() else sampleRateHz.toUac1RateBytes()
        val index = if (clockId != null) clockIndex(clockId, format.acInterfaceNumber)
            else format.endpointAddress ?: return UsbAudioClockConfiguration(sampleRateHz)
        val transferred = connection.controlTransfer(
            if (uac2) UAC2_INTERFACE_SET else UAC1_ENDPOINT_SET,
            SET_CUR, CS_SAM_FREQ shl 8, index, payload, payload.size, TIMEOUT_MS,
        )
        val setSucceeded = transferred == payload.size
        if (setSucceeded) afterRateSet()
        val readBack = if (clockId != null) getUac2ClockRate(connection, resolved, clockId)
            else getUac1EndpointRate(connection, resolved)
        return UsbAudioClockConfiguration(sampleRateHz, setSucceeded, readBack, clockId)
    }

    internal fun getSampleRate(
        connection: UsbAudioControlTransfer,
        format: UsbAudioStreamingFormat,
    ): Int? = when (format.audioClassVersion) {
        UsbAudioClassVersion.Uac2, UsbAudioClassVersion.Uac3 ->
            resolveClockSource(connection, format)?.let { getUac2ClockRate(connection, format, it) }
        else -> getUac1EndpointRate(connection, format)
    }

    internal fun getSupportedSampleRates(
        connection: UsbAudioControlTransfer,
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
        val clockId = resolveClockSource(connection, format) ?: return emptyList()
        val transferred = connection.controlTransfer(
            UAC2_INTERFACE_GET,
            UAC2_RANGE,
            UAC2_CS_SAM_FREQ shl 8,
            clockIndex(clockId, format.acInterfaceNumber),
            payload,
            payload.size,
            TIMEOUT_MS,
        )
        return parseRangePayload(payload, transferred)
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

    private fun getUac1EndpointRate(
        connection: UsbAudioControlTransfer,
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

    private fun getUac2ClockRate(
        connection: UsbAudioControlTransfer,
        format: UsbAudioStreamingFormat,
        clockId: Int,
    ): Int? {
        val payload = ByteArray(4)
        val transferred = connection.controlTransfer(
            UAC2_INTERFACE_GET,
            UAC2_CUR,
            UAC2_CS_SAM_FREQ shl 8,
            clockIndex(clockId, format.acInterfaceNumber),
            payload,
            payload.size,
            TIMEOUT_MS,
        )
        return if (transferred == payload.size) payload.toUac2Rate() else null
    }

    private fun resolveClockSource(connection: UsbAudioControlTransfer, format: UsbAudioStreamingFormat): Int? {
        var id = format.clockSourceIds.singleOrNull()?.takeIf { it in 1..255 } ?: return null
        val visited = mutableSetOf<Int>()
        while (visited.add(id)) {
            val sources = format.clockSelectorSources[id] ?: return id
            if (sources.isEmpty()) return null
            val pin = if (sources.size == 1) 1 else {
                val payload = ByteArray(1)
                val transferred = connection.controlTransfer(UAC2_INTERFACE_GET, UAC2_CUR,
                    CS_SAM_FREQ shl 8, clockIndex(id, format.acInterfaceNumber), payload, 1, TIMEOUT_MS)
                if (transferred != 1) return null
                payload[0].toInt() and 0xff
            }
            id = sources.getOrNull(pin - 1)?.takeIf { it in 1..255 } ?: return null
        }
        return null
    }

    private fun clockIndex(clockId: Int, acInterfaceNumber: Int?): Int =
        ((clockId and 0xff) shl 8) or ((acInterfaceNumber ?: 0) and 0xff)

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
    private const val UAC2_CUR = 0x01
    private const val UAC2_RANGE = 0x02
    private const val CS_SAM_FREQ = 0x01
    private const val UAC2_CS_SAM_FREQ = 0x01
    private const val UAC1_ENDPOINT_SET = 0x22
    private const val UAC1_ENDPOINT_GET = 0xA2
    private const val UAC2_INTERFACE_SET = 0x21
    private const val UAC2_INTERFACE_GET = 0xA1
    private const val TIMEOUT_MS = 1_000
    private const val RANGE_BUFFER_BYTES = 256
}

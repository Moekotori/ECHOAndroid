package app.echo.android.usbaudio

import android.hardware.usb.UsbDeviceConnection

object UsbAudioClock {
    fun setSampleRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
        sampleRateHz: Int,
    ): Boolean {
        val rate = sampleRateHz.takeIf { it > 0 } ?: return false
        return when (format.audioClassVersion) {
            UsbAudioClassVersion.Uac2,
            UsbAudioClassVersion.Uac3,
            -> setUac2ClockRate(connection, format, rate) || setUac1EndpointRate(connection, format, rate)
            else -> setUac1EndpointRate(connection, format, rate) || setUac2ClockRate(connection, format, rate)
        }
    }

    private fun setUac1EndpointRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
        sampleRateHz: Int,
    ): Boolean {
        val endpoint = format.endpointAddress ?: return false
        val payload = byteArrayOf(
            (sampleRateHz and 0xff).toByte(),
            ((sampleRateHz shr 8) and 0xff).toByte(),
            ((sampleRateHz shr 16) and 0xff).toByte(),
        )
        val transferred = connection.controlTransfer(
            UAC1_ENDPOINT_SET,
            SET_CUR,
            CS_SAM_FREQ shl 8,
            endpoint,
            payload,
            payload.size,
            TIMEOUT_MS,
        )
        return transferred == payload.size
    }

    private fun setUac2ClockRate(
        connection: UsbDeviceConnection,
        format: UsbAudioStreamingFormat,
        sampleRateHz: Int,
    ): Boolean {
        val acInterface = format.acInterfaceNumber ?: 0
        val payload = byteArrayOf(
            (sampleRateHz and 0xff).toByte(),
            ((sampleRateHz shr 8) and 0xff).toByte(),
            ((sampleRateHz shr 16) and 0xff).toByte(),
            ((sampleRateHz shr 24) and 0xff).toByte(),
        )
        val clockIds = format.clockSourceIds.ifEmpty { listOf(1, 2, 3, 4, 5) }
        return clockIds.any { clockId ->
            val transferred = connection.controlTransfer(
                UAC2_INTERFACE_SET,
                SET_CUR,
                UAC2_CS_SAM_FREQ shl 8,
                (clockId and 0xff) or (acInterface shl 8),
                payload,
                payload.size,
                TIMEOUT_MS,
            )
            transferred == payload.size
        }
    }

    private const val SET_CUR = 0x01
    private const val CS_SAM_FREQ = 0x01
    private const val UAC2_CS_SAM_FREQ = 0x01
    private const val UAC1_ENDPOINT_SET = 0x22
    private const val UAC2_INTERFACE_SET = 0x21
    private const val TIMEOUT_MS = 1_000
}

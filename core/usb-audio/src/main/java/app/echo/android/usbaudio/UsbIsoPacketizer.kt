package app.echo.android.usbaudio

object UsbIsoPacketizer {
    const val FullSpeedPacketsPerSecond = 1_000
    const val HighSpeedPacketsPerSecond = 8_000

    fun packetsPerSecond(
        sampleRateHz: Int,
        channelCount: Int,
        bytesPerSample: Int,
        maxPacketSize: Int,
        usbVersion: Int? = null,
    ): Int {
        val packetBytes = maxPacketPayloadBytes(maxPacketSize)
        val extraTransactions = (maxPacketSize shr 11) and 0x03
        if (extraTransactions > 0 || packetBytes > 1_023) {
            return HighSpeedPacketsPerSecond
        }
        val fullSpeedBytes = samplesForPacket(
            sampleRateHz,
            FullSpeedPacketsPerSecond,
            remainder = FullSpeedPacketsPerSecond - 1,
        ) * channelCount.coerceAtLeast(1) * bytesPerSample.coerceAtLeast(1)
        if (fullSpeedBytes > packetBytes) {
            return HighSpeedPacketsPerSecond
        }
        // USB 2.0 isochronous audio is almost always 125µs microframes. A 192-byte
        // wMaxPacketSize can still be HS (48 kHz / 16-bit / 2ch FS size) so version
        // is the discriminator versus true full-speed endpoints.
        if ((usbVersion ?: 0) >= 0x0200 && packetBytes < 1_023) {
            return HighSpeedPacketsPerSecond
        }
        return FullSpeedPacketsPerSecond
    }

    fun samplesForPacket(sampleRateHz: Int, packetsPerSecond: Int, remainder: Int): Int {
        val safeRate = sampleRateHz.coerceAtLeast(1)
        val safePps = packetsPerSecond.coerceAtLeast(1)
        return (remainder + safeRate) / safePps
    }

    fun nextRemainder(sampleRateHz: Int, packetsPerSecond: Int, remainder: Int): Int {
        val safeRate = sampleRateHz.coerceAtLeast(1)
        val safePps = packetsPerSecond.coerceAtLeast(1)
        return (remainder + safeRate) % safePps
    }

    fun packetByteSize(sampleCount: Int, channelCount: Int, bytesPerSample: Int): Int =
        sampleCount.coerceAtLeast(0) * channelCount.coerceAtLeast(1) * bytesPerSample.coerceAtLeast(1)

    fun maxPacketPayloadBytes(maxPacketSize: Int): Int {
        val size = maxIsoPacketBytes(maxPacketSize)
        val extraTransactions = (maxPacketSize shr 11) and 0x03
        return size * (extraTransactions + 1)
    }

    fun maxIsoPacketBytes(maxPacketSize: Int): Int =
        (maxPacketSize and 0x07ff).coerceAtLeast(1)

    fun packetsPerUrb(packetsPerSecond: Int): Int =
        if (packetsPerSecond >= HighSpeedPacketsPerSecond) 8 else 1
}

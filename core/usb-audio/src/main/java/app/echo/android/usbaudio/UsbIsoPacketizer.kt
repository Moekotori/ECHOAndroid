package app.echo.android.usbaudio

object UsbIsoPacketizer {
    const val FullSpeedPacketsPerSecond = 1_000
    const val HighSpeedPacketsPerSecond = 8_000

    fun packetsPerSecond(
        sampleRateHz: Int,
        channelCount: Int,
        bytesPerSample: Int,
        maxPacketSize: Int,
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
        return if (fullSpeedBytes <= packetBytes) {
            FullSpeedPacketsPerSecond
        } else {
            HighSpeedPacketsPerSecond
        }
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
        val size = maxPacketSize and 0x07ff
        val extraTransactions = (maxPacketSize shr 11) and 0x03
        return size * (extraTransactions + 1)
    }

    fun packetsPerUrb(packetsPerSecond: Int): Int =
        if (packetsPerSecond >= HighSpeedPacketsPerSecond) 8 else 1
}

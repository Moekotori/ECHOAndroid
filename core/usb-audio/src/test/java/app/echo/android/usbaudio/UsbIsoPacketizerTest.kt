package app.echo.android.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbIsoPacketizerTest {
    @Test
    fun fortyEightKhzFullSpeedIsExactlyFortyEightSamples() {
        var remainder = 0
        repeat(1_000) {
            val samples = UsbIsoPacketizer.samplesForPacket(48_000, 1_000, remainder)
            remainder = UsbIsoPacketizer.nextRemainder(48_000, 1_000, remainder)
            assertEquals(48, samples)
        }
        assertEquals(0, remainder)
    }

    @Test
    fun fortyFourOneKhzFullSpeedAveragesCorrectly() {
        var remainder = 0
        var total = 0
        repeat(1_000) {
            val samples = UsbIsoPacketizer.samplesForPacket(44_100, 1_000, remainder)
            remainder = UsbIsoPacketizer.nextRemainder(44_100, 1_000, remainder)
            total += samples
        }
        assertEquals(44_100, total)
    }

    @Test
    fun highSpeedIsSelectedWhenFullSpeedPacketWouldOverflow() {
        val pps = UsbIsoPacketizer.packetsPerSecond(
            sampleRateHz = 192_000,
            channelCount = 2,
            bytesPerSample = 3,
            maxPacketSize = 1_024,
        )
        assertEquals(UsbIsoPacketizer.HighSpeedPacketsPerSecond, pps)
    }

    @Test
    fun usb2IsochronousWithFullSpeedSizedMaxPacketIsStillHighSpeed() {
        val pps = UsbIsoPacketizer.packetsPerSecond(
            sampleRateHz = 48_000,
            channelCount = 2,
            bytesPerSample = 2,
            maxPacketSize = 192,
            usbVersion = 0x0200,
        )
        assertEquals(UsbIsoPacketizer.HighSpeedPacketsPerSecond, pps)
    }

    @Test
    fun isoPacketBytesIgnoreHighBandwidthMultiplier() {
        val highBandwidth = 192 or (1 shl 11)
        assertEquals(192, UsbIsoPacketizer.maxIsoPacketBytes(highBandwidth))
        assertEquals(384, UsbIsoPacketizer.maxPacketPayloadBytes(highBandwidth))
    }

    @Test
    fun usb1FullSpeedKeepsOneMillisecondPackets() {
        val pps = UsbIsoPacketizer.packetsPerSecond(
            sampleRateHz = 48_000,
            channelCount = 2,
            bytesPerSample = 2,
            maxPacketSize = 192,
            usbVersion = 0x0110,
        )
        assertEquals(UsbIsoPacketizer.FullSpeedPacketsPerSecond, pps)
    }
}

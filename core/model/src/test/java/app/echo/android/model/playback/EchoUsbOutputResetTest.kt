package app.echo.android.model.playback

import org.junit.Assert.*
import org.junit.Test

class EchoUsbOutputResetTest {
    @Test fun formatResetKeepsVolumeAndDevicePermissions() {
        val options = EchoUsbOutputOptions(sampleRateHz = 96000, bitDepth = 24, channelCount = 1,
            volumePercent = 31, volumeMode = EchoUsbVolumeMode.Hardware,
            bufferMode = EchoUsbBufferMode.Stable, keepAliveOnPause = false,
            dopDeviceLimits = mapOf("1:2" to 2822400))
        val reset = options.resetFormatAndBuffer()
        assertEquals(0, reset.sampleRateHz)
        assertEquals(0, reset.bitDepth)
        assertEquals(0, reset.channelCount)
        assertEquals(EchoUsbBufferMode.Balanced, reset.bufferMode)
        assertEquals(options.volumePercent, reset.volumePercent)
        assertEquals(options.volumeMode, reset.volumeMode)
        assertEquals(options.keepAliveOnPause, reset.keepAliveOnPause)
        assertEquals(options.dopDeviceLimits, reset.dopDeviceLimits)
    }
}

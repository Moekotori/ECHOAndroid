package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoUsbVolumeKeysTest {
    @Test fun pressHoldAndBoundsUseExistingPercentScale() {
        assertEquals(49, usbKeyVolumePercent(50, -1, 0))
        assertEquals(48, usbKeyVolumePercent(50, -1, 6))
        assertEquals(45, usbKeyVolumePercent(50, -1, 16))
        assertEquals(0, usbKeyVolumePercent(2, -1, 20))
        assertEquals(100, usbKeyVolumePercent(99, 1, 20))
    }
}

package app.echo.android.usbaudio

import org.junit.Assert.*
import org.junit.Test

class UsbTransferSnapshotTest {
    @Test fun mapsNativeAbiAndKeepsUnavailableMinimumDistinctFromZero() {
        val data = longArrayOf(48, -1, 768, 48000, 6, 50, 25, 2, 3, 4, 5, 6, 480, 7, 6, -71, 1)
        val snapshot = UsbTransferSnapshot.fromNative(data)
        assertNull(snapshot.minQueuedFrames)
        assertEquals(6L, snapshot.supplyGapCount)
        assertEquals(480L, snapshot.supplyGapFrames)
        assertEquals("feedback-packet", snapshot.lastErrorSource)
        assertEquals(-71, snapshot.lastErrorCode)
        assertTrue(snapshot.fatal)
        data[1] = 0
        assertEquals(0L, UsbTransferSnapshot.fromNative(data).minQueuedFrames)
    }

    @Test fun scopesExistingWorkaroundAndDoesNotEnableUnverifiedDevices() {
        assertTrue(UsbDeviceQuirks.resolve(0x0644, 1).reselectStreamingAfterRateChange)
        assertEquals("teac-reselect-streaming-after-rate-change", UsbDeviceQuirks.resolve(0x0644, 2).matchedRules.single().id)
        assertFalse(UsbDeviceQuirks.resolve(0x1234, 0x0644).reselectStreamingAfterRateChange)
        assertTrue(UsbDeviceQuirks.resolve(0x1234, 1).matchedRules.isEmpty())
    }
}

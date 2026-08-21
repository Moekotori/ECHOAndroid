package app.echo.android.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioClockTest {
    @Test
    fun rejectsReadbackMismatchEvenWhenSetSucceeded() {
        assertTrue(UsbAudioClock.accepted(48_000, setSucceeded = true, readBackHz = 48_000))
        assertTrue(UsbAudioClock.accepted(48_000, setSucceeded = true, readBackHz = null))
        assertFalse(UsbAudioClock.accepted(48_000, setSucceeded = true, readBackHz = 44_100))
        assertFalse(UsbAudioClock.accepted(48_000, setSucceeded = false, readBackHz = null))
    }

    @Test
    fun parsesDiscreteAndContinuousUac2Ranges() {
        val payload = byteArrayOf(
            2, 0,
            0x44, 0xAC.toByte(), 0x00, 0x00,
            0x44, 0xAC.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x77, 0x01, 0x00,
            0x00, 0xEE.toByte(), 0x02, 0x00,
            0x00, 0x77, 0x01, 0x00,
        )
        val ranges = UsbAudioClock.parseRangePayload(payload, payload.size)
        assertEquals(2, ranges.size)
        assertTrue(UsbAudioClock.supportsRate(ranges, 44_100))
        assertFalse(UsbAudioClock.supportsRate(ranges, 48_000))
        assertTrue(UsbAudioClock.supportsRate(ranges, 192_000))
        assertTrue(UsbAudioClock.supportsRate(ranges, 96_000))
        assertFalse(UsbAudioClock.supportsRate(ranges, 88_200))
    }

    @Test
    fun discreteRangeWithZeroResolutionStillMatchesMinAndMax() {
        val range = UsbAudioClockRange(minHz = 48_000, maxHz = 48_000, resolutionHz = 0)
        assertTrue(range.contains(48_000))
        assertFalse(range.contains(96_000))
    }
}

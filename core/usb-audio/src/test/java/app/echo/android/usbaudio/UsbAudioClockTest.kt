package app.echo.android.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioClockTest {
    private val uac2 = UsbAudioStreamingFormat(1, 1, UsbAudioClassVersion.Uac2,
        acInterfaceNumber = 3, clockSourceIds = listOf(0x12), endpointAddress = 1)

    @Test fun uac2ReadsClockEntityOnItsControlInterface() {
        var calls = 0
        val connection = UsbAudioControlTransfer { type, request, value, index, payload, length, _ ->
            calls++
            assertEquals(0xA1, type)
            assertEquals(0x01, request)
            assertEquals(0x0100, value)
            assertEquals(0x1203, index)
            assertEquals(4, length)
            byteArrayOf(0x80.toByte(), 0xBB.toByte(), 0, 0).copyInto(payload)
            4
        }
        assertEquals(48_000, UsbAudioClock.getSampleRate(connection, uac2))
        assertEquals(1, calls)
    }

    @Test fun uac2SetsThenVerifiesRateUsingUac2Requests() {
        val requests = mutableListOf<Pair<Int, Int>>()
        val connection = UsbAudioControlTransfer { type, request, value, index, payload, length, _ ->
            requests += type to request
            assertEquals(0x0100, value)
            assertEquals(0x1203, index)
            if (request == 0x02) {
                // The device doesn't provide ranges; SET/GET CUR still work.
                -1
            } else {
                assertEquals(0x01, request)
                assertEquals(4, length)
                val rate = byteArrayOf(0x80.toByte(), 0xBB.toByte(), 0, 0)
                if (type == 0x21) org.junit.Assert.assertArrayEquals(rate, payload)
                else rate.copyInto(payload)
                4
            }
        }
        assertTrue(UsbAudioClock.setSampleRate(connection, uac2, 48_000))
        assertEquals(listOf(0xA1 to 0x02, 0x21 to 0x01, 0xA1 to 0x01), requests)
    }

    @Test fun uac2RangeUsesEntityIndexAndRangeRequest() {
        val connection = UsbAudioControlTransfer { type, request, value, index, payload, _, _ ->
            assertEquals(0xA1, type)
            assertEquals(0x02, request)
            assertEquals(0x0100, value)
            assertEquals(0x1203, index)
            byteArrayOf(1, 0, 0x80.toByte(), 0xBB.toByte(), 0, 0,
                0x80.toByte(), 0xBB.toByte(), 0, 0, 0, 0, 0, 0).copyInto(payload)
            14
        }
        assertEquals(listOf(UsbAudioClockRange(48_000, 48_000, 0)),
            UsbAudioClock.getSupportedSampleRates(connection, uac2))
    }

    @Test fun uac1EndpointReadKeepsItsOriginalRequestCode() {
        val requests = mutableListOf<Pair<Int, Int>>()
        val connection = UsbAudioControlTransfer { type, request, _, index, payload, _, _ ->
            requests += type to request
            if (type == 0xA2) {
                assertEquals(0x81, request)
                assertEquals(1, index)
                byteArrayOf(0x80.toByte(), 0xBB.toByte(), 0).copyInto(payload)
                3
            } else -1
        }
        assertEquals(48_000, UsbAudioClock.getSampleRate(connection, uac2.copy(audioClassVersion = UsbAudioClassVersion.Uac1)))
        assertEquals(0xA2 to 0x81, requests.last())
    }

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
